package com.nexlink.app.receivers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.nexlink.app.App
import com.nexlink.app.R
import com.nexlink.app.db.CryptoStore
import com.nexlink.app.db.SmsHelper
import com.nexlink.app.ui.sms.ConversationActivity

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
                            // Auto-reply with our public key if we haven't sent it yet.
                            val peerPub = CryptoStore.parseKeyExchange(bodyStr)
                            if (peerPub != null) {
                                CryptoStore.storePeerKey(context, sender, peerPub)
                                if (!CryptoStore.hasSentKey(context, sender)) {
                                    CryptoStore.markKeySent(context, sender)
                                    val ourPub = CryptoStore.getPublicKeyBytes(context)
                                    SmsHelper.sendSms(context, sender,
                                        CryptoStore.buildKeyExchange(ourPub), -1)
                                }
                            }
                            // Key exchange messages are internal — not shown to the user
                        }
                        else -> {
                            SmsHelper.saveIncomingSms(context, sender, bodyStr)
                            val notif = if (CryptoStore.isEncrypted(bodyStr))
                                "🔒 Encrypted message" else bodyStr
                            SmsNotifier.notify(context, sender, notif)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
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

// Delegates all WAP push handling, MMS network download, PDU storage, and MMSC ACKs
// to the android-smsmms PushReceiver — the same base class used by QKSMS / Fossify Messages.
// After download completes, TransactionService broadcasts MMS_RECEIVED which is handled
// by NexLinkMmsReceivedReceiver.
class MmsReceiver : com.android.mms.transaction.PushReceiver()

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

        // InboxStyle shows each message on its own line when there are multiple
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(name)
        messages.takeLast(6).forEach { style.addLine(it) }
        if (messages.size > 1) style.setSummaryText("${messages.size} new messages")

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

        val notif = NotificationCompat.Builder(ctx, App.CH_SMS)
            .setSmallIcon(R.drawable.ic_notif_nexlink)
            .setLargeIcon(buildAvatarIcon(name))
            .setContentTitle(name)
            .setContentText(contentText)
            .setStyle(style)
            .setContentIntent(pi)
            .addAction(replyAction)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
            .setSmallIcon(R.drawable.ic_notif_nexlink)
            .setContentTitle(name)
            .setContentText("You: $replyText")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        nm.notify(sender.hashCode(), notif)
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
