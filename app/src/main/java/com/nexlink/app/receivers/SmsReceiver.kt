package com.nexlink.app.receivers

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.google.android.mms.pdu_alt.NotificationInd
import com.google.android.mms.pdu_alt.PduParser
import com.nexlink.app.App
import com.nexlink.app.R
import com.nexlink.app.db.CryptoStore
import com.nexlink.app.db.IconPrefs
import com.nexlink.app.db.MmsDownloader
import com.nexlink.app.db.MmsPduResult
import com.nexlink.app.db.NotificationPrefs
import com.nexlink.app.db.SmsHelper
import com.nexlink.app.ui.sms.ConversationActivity
import com.nexlink.app.wear.WearSync
import java.io.File

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val grouped = mutableMapOf<String, StringBuilder>()
        for (msg in messages) {
            grouped.getOrPut(msg.originatingAddress ?: "") { StringBuilder() }
                .append(msg.messageBody)
        }
        val pending = goAsync()
        Thread {
            try {
                for ((sender, body) in grouped) {
                    val bodyStr = body.toString()
                    when {
                        CryptoStore.isKeyExchange(bodyStr) -> {
                            // Peer has NexLink — store their public key and establish a session.
                            val peerPub = CryptoStore.parseKeyExchange(bodyStr)
                            if (peerPub != null) {
                                CryptoStore.storePeerKey(context, sender, peerPub)

                                if (!CryptoStore.didInitiateTo(context, sender)) {
                                    // They initiated (or we reinstalled and lost our init flag).
                                    // Always auto-reply so reinstalls self-heal without manual action.
                                    CryptoStore.markKeySent(context, sender)
                                    val ourPub = CryptoStore.getPublicKeyBytes(context)
                                    SmsHelper.sendSms(context, sender,
                                        CryptoStore.buildKeyExchange(ourPub), -1)
                                } else {
                                    // We initiated and just received their reply.
                                    // Both sides now have each other's key → session ready.
                                    CryptoStore.markSessionReady(context, sender)
                                    notifySessionEstablished(context, sender)
                                }
                            }
                            // Key exchange messages are internal — not shown to the user
                        }
                        else -> {
                            SmsHelper.saveIncomingSms(context, sender, bodyStr)
                            // If the peer sends an encrypted message it proves they have our public key
                            // (they derived the same session key) → mark session bidirectionally ready.
                            if (CryptoStore.isEncrypted(bodyStr) &&
                                CryptoStore.getSessionKey(context, sender) != null) {
                                CryptoStore.markSessionReady(context, sender)
                            }
                            val notif = if (CryptoStore.isEncrypted(bodyStr))
                                "🔒 Encrypted message" else bodyStr
                            SmsNotifier.notify(context, sender, notif)
                            WearSync.pushConversations(context)
                            notifySessionEstablished(context, sender)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

private fun notifySessionEstablished(ctx: Context, address: String) {
    val intent = android.content.Intent("com.nexlink.app.SESSION_ESTABLISHED").apply {
        setPackage(ctx.packageName)
        putExtra("address", address)
    }
    ctx.sendBroadcast(intent)
}

class SmsReceiverFallback : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return
        // When NexLink is the default SMS app, SmsReceiver already handles SMS_DELIVER
        // and posts one notification. When we are NOT the default, the system's default
        // SMS app will post its own notification — we must not add a second one here.
        // This receiver is a required stub for the default-SMS-app contract only.
    }
}

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.WAP_PUSH_DELIVER") return
        if (intent.type != "application/vnd.wap.mms-message") return

        val pending = goAsync()
        val data = intent.getByteArrayExtra("data")
        Log.d("NexLink_MMS", "WAP push arrived: dataLen=${data?.size}")

        if (data == null) {
            Log.e("NexLink_MMS", "WAP push has no data extra")
            pending.finish()
            return
        }

        // Parse M-Notification.ind to extract content-location URL
        val notif = try { PduParser(data, true).parse() as? NotificationInd } catch (e: Exception) {
            Log.e("NexLink_MMS", "PDU parse failed: ${e.message}")
            null
        }
        val contentUrl = notif?.contentLocation?.let { String(it, Charsets.UTF_8) }?.trim()
        Log.d("NexLink_MMS", "WAP push content-location=$contentUrl")

        if (contentUrl.isNullOrBlank()) {
            Log.e("NexLink_MMS", "No content-location — cannot download MMS")
            pending.finish()
            return
        }

        // Temp file the telephony service will write the downloaded PDU into
        val fileName = "mms_inbound_${System.currentTimeMillis()}.pdu"
        val destUri = Uri.Builder()
            .scheme("content")
            .authority("${ctx.packageName}.MmsFileProvider")
            .path(fileName)
            .build()

        // Explicitly grant write access to the telephony packages that run MmsService
        for (pkg in listOf(
            "com.android.providers.telephony",
            "com.android.phone",
            "com.android.mms.service"
        )) {
            try {
                ctx.grantUriPermission(pkg, destUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
        }

        val onDone = Intent("com.nexlink.app.MMS_DOWNLOADED").apply {
            setPackage(ctx.packageName)
            putExtra("file_name", fileName)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, System.currentTimeMillis().toInt(), onDone,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        try {
            @Suppress("DEPRECATION")
            SmsManager.getDefault().downloadMultimediaMessage(ctx, contentUrl, destUri, null, pi)
            Log.d("NexLink_MMS", "downloadMultimediaMessage() called: $contentUrl → $destUri")
        } catch (e: Exception) {
            Log.e("NexLink_MMS", "downloadMultimediaMessage() threw: ${e.message}")
        }

        pending.finish()
    }
}

/** Receives the completion broadcast from SmsManager.downloadMultimediaMessage(). */
class MmsDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val rc = resultCode
        val fileName = intent.getStringExtra("file_name") ?: return
        Log.d("NexLink_MMS", "MmsDownloadReceiver: rc=$rc fileName=$fileName")

        if (rc != Activity.RESULT_OK) {
            Log.e("NexLink_MMS", "downloadMultimediaMessage failed: resultCode=$rc")
            SmsNotifier.notify(ctx, "MMS", "📷 MMS (download failed, code=$rc)")
            return
        }

        val file = File(ctx.cacheDir, fileName)
        if (!file.exists()) {
            Log.e("NexLink_MMS", "Downloaded PDU file missing: $fileName")
            return
        }

        val pduBytes = try { file.readBytes() } catch (e: Exception) {
            Log.e("NexLink_MMS", "Failed to read PDU file: ${e.message}")
            return
        }
        file.delete()
        Log.d("NexLink_MMS", "MmsDownloadReceiver: read ${pduBytes.size}B, persisting…")

        val pending = goAsync()
        Thread {
            try {
                val result = MmsDownloader.storeRawPdu(ctx, pduBytes)
                if (result != null) {
                    SmsNotifier.notify(ctx, result.sender ?: "Unknown", result.notifText)
                } else {
                    Log.e("NexLink_MMS", "storePdu returned null — notifying generic")
                    SmsNotifier.notify(ctx, "Unknown", "📷 MMS received")
                }
                WearSync.pushConversations(ctx)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Receives the result of SmsManager.sendMultimediaMessage() and logs it. */
class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val result = resultCode
        val isOk   = result == android.app.Activity.RESULT_OK

        // MMS_DATA contains the raw M-Send.conf PDU from the MMSC.
        // resultCode=OK only means HTTP 200 was returned; the ACTUAL accept/reject
        // decision is the Response-Status field inside this PDU.
        val pdu    = intent.getByteArrayExtra("android.telephony.extra.MMS_DATA")
        val status = pdu?.let { parseResponseStatus(it) }
        val hex    = pdu?.take(48)?.joinToString(" ") { "%02X".format(it) } ?: "null"

        val statusDesc = when (status) {
            null  -> "no-data"
            0x80  -> "OK"
            0x81  -> "Error-unspecified"
            0x82  -> "Error-service-denied"
            0x83  -> "Error-message-format-corrupt"
            0x84  -> "Error-sending-address-unresolved"
            0x85  -> "Error-message-not-found"
            0x86  -> "Error-network-problem"
            0x87  -> "Error-content-not-accepted"
            0x88  -> "Error-unsupported-message"
            else  -> "unknown-0x%02X".format(status)
        }

        android.util.Log.d("NexLink_MMS",
            "MmsSentReceiver: httpOk=$isOk mmscStatus=$statusDesc(${status?.let{"0x%02X".format(it)}}) pdu=$hex")

        val outboxId = intent.getLongExtra("outbox_id", -1L)
        if (status == 0x80 && outboxId > 0) {
            // Flip outbox row → sent so the UI dedupes the optimistic bubble
            try {
                val uri = android.content.ContentUris.withAppendedId(
                    android.provider.Telephony.Mms.CONTENT_URI, outboxId)
                ctx.contentResolver.update(uri,
                    android.content.ContentValues().apply {
                        put(android.provider.Telephony.Mms.MESSAGE_BOX,
                            android.provider.Telephony.Mms.MESSAGE_BOX_SENT)
                    }, null, null)
            } catch (_: Exception) {}
        } else if (status != null && status != 0x80) {
            android.util.Log.e("NexLink_MMS", "MMSC REJECTED: $statusDesc — see pdu bytes above for full response")
        }
    }

    /** Scan M-Send.conf bytes for X-Mms-Response-Status field (0x91) and return the status byte. */
    private fun parseResponseStatus(pdu: ByteArray): Int? {
        var i = 0
        while (i < pdu.size - 1) {
            val field = pdu[i++].toInt() and 0xFF
            if (i >= pdu.size) break
            if (field == 0x92) return pdu[i].toInt() and 0xFF  // X-Mms-Response-Status value (0x12+0x80)
            // Skip value: short-int=1 byte, long-int=length+bytes, text=scan to null
            val v = pdu[i].toInt() and 0xFF
            i += when {
                v and 0x80 != 0 -> 1
                v in 1..30      -> 1 + v
                else            -> { var e = i; while (e < pdu.size && pdu[e] != 0.toByte()) e++; e - i + 1 }
            }
        }
        return null
    }
}

/** Handles "Mark as Read" taps from the notification shade. */
class NotificationMarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val sender   = intent.getStringExtra("address") ?: return
        val threadId = intent.getLongExtra("thread_id", 0L)
        if (threadId > 0) {
            try {
                ctx.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    android.content.ContentValues().apply { put(Telephony.Sms.READ, 1) },
                    "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                    arrayOf(threadId.toString())
                )
                ctx.contentResolver.update(
                    Telephony.Mms.CONTENT_URI,
                    android.content.ContentValues().apply { put(Telephony.Mms.READ, 1) },
                    "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0",
                    arrayOf(threadId.toString())
                )
            } catch (_: Exception) {}
        }
        SmsNotifier.clearPending(ctx, sender)
    }
}

/** Handles inline replies from the notification shade. */
class NotificationReplyReceiver : BroadcastReceiver() {
    companion object { const val KEY_REPLY = "nexlink_reply_text" }

    override fun onReceive(ctx: Context, intent: Intent) {
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val reply   = results.getCharSequence(KEY_REPLY)?.toString()?.trim() ?: return
        val address = intent.getStringExtra("address") ?: return
        val name    = intent.getStringExtra("contact_name") ?: address

        val pending = goAsync()
        Thread {
            try {
                SmsHelper.sendSms(ctx, address, reply, -1)
                // Add reply to pending so the notification updates with the sent text
                SmsNotifier.notifyReplied(ctx, address, name, reply)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

object SmsNotifier {

    // In-memory store of unread message bodies per sender — cleared when the chat is opened.
    // Resets on process kill, which is fine since the OS dismisses notifications too.
    private val pending = mutableMapOf<String, MutableList<String>>()

    fun notify(ctx: Context, sender: String, body: String) {
        val name     = SmsHelper.getContactName(ctx, sender)
        val threadId = runCatching {
            Telephony.Threads.getOrCreateThreadId(ctx, setOf(sender))
        }.getOrDefault(0L)

        // Accumulate messages for bundling
        pending.getOrPut(sender) { mutableListOf() }.add(body)
        val messages = pending[sender]!!

        // Tapping the notification opens the exact conversation
        val openIntent = Intent(ctx, ConversationActivity::class.java).apply {
            putExtra("address", sender)
            putExtra("contact_name", name)
            if (threadId > 0) putExtra("thread_id", threadId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, sender.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // MessagingStyle enables Samsung Buds and other wearables to read the message aloud,
        // announcing "SMS from [contact name]" as the service + sender.
        val person = Person.Builder().setName(name).build()
        val msgStyle = NotificationCompat.MessagingStyle(person)
            .setConversationTitle("SMS")
        messages.takeLast(6).forEach { msgStyle.addMessage(it, System.currentTimeMillis(), person) }

        val contentText = if (messages.size == 1) body else "${messages.size} new messages from $name"

        // Inline reply action
        val replyInput = RemoteInput.Builder(NotificationReplyReceiver.KEY_REPLY)
            .setLabel("Reply")
            .build()
        val replyIntent = Intent(ctx, NotificationReplyReceiver::class.java).apply {
            putExtra("address", sender)
            putExtra("thread_id", threadId)
            putExtra("contact_name", name)
        }
        val replyPi = PendingIntent.getBroadcast(
            ctx, sender.hashCode() + 9000, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(R.drawable.ic_send, "Reply", replyPi)
            .addRemoteInput(replyInput)
            .setAllowGeneratedReplies(true)
            .build()

        // Mark as Read action
        val markReadIntent = Intent(ctx, NotificationMarkReadReceiver::class.java).apply {
            putExtra("address", sender)
            putExtra("thread_id", threadId)
        }
        val markReadPi = PendingIntent.getBroadcast(
            ctx, sender.hashCode() + 9001, markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val markReadAction = NotificationCompat.Action.Builder(R.drawable.ic_clear, "Mark as Read", markReadPi)
            .build()

        val channelId = if (NotificationPrefs.isPriorityContact(ctx, sender))
            App.CH_SMS_PRIORITY else App.CH_SMS

        val notif = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(notifIconRes(ctx))
            .setLargeIcon(buildAvatarIcon(name))
            .setContentTitle(name)
            .setContentText(contentText)
            .setStyle(msgStyle)
            .setContentIntent(pi)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setNumber(messages.size)
            .setGroup("sms_${sender.hashCode()}")
            .build()

        ctx.getSystemService(NotificationManager::class.java)
            .notify(sender.hashCode(), notif)
    }

    /** Called by ConversationActivity when the user opens the chat. */
    fun clearPending(ctx: Context, sender: String) {
        pending.remove(sender)
        ctx.getSystemService(NotificationManager::class.java)
            .cancel(sender.hashCode())
    }

    /** Called after an inline reply is sent — updates the notification to show "Replied". */
    fun notifyReplied(ctx: Context, sender: String, name: String, replyText: String) {
        pending.remove(sender)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        // Show a brief "Replied" notification then auto-cancel
        val notif = NotificationCompat.Builder(ctx, App.CH_SMS)
            .setSmallIcon(notifIconRes(ctx))
            .setContentTitle(name)
            .setContentText("You: $replyText")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        nm.notify(sender.hashCode(), notif)
    }

    fun notifIconRes(ctx: Context): Int = when (IconPrefs.getNotifIconIndex(ctx)) {
        1  -> R.drawable.ic_notif_custom_1
        2  -> R.drawable.ic_notif_custom_2
        3  -> R.drawable.ic_notif_custom_3
        4  -> R.drawable.ic_notif_custom_4
        5  -> R.drawable.ic_notif_custom_5
        6  -> R.drawable.ic_notif_custom_6
        7  -> R.drawable.ic_notif_custom_7
        8  -> R.drawable.ic_notif_custom_8
        9  -> R.drawable.ic_notif_custom_9
        10 -> R.drawable.ic_notif_custom_10
        else -> R.drawable.ic_notif_nexlink
    }

    private fun buildAvatarIcon(name: String): Bitmap {
        val size     = 128
        val bmp      = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas   = Canvas(bmp)
        val paint    = Paint(Paint.ANTI_ALIAS_FLAG)
        // Colour derived from the name so each contact gets a consistent colour
        val hue      = ((name.hashCode() and 0xFFFFFF) % 360).toFloat()
        val hsv      = floatArrayOf(hue, 0.55f, 0.75f)
        paint.color  = android.graphics.Color.HSVToColor(hsv)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color     = 0xFFFFFFFF.toInt()
        paint.textSize  = size * 0.46f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        val initials = name.split(" ").take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
        val textY    = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(initials.take(2), size / 2f, textY, paint)
        return bmp
    }
}
