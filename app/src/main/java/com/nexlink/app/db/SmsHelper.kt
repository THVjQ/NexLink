package com.nexlink.app.db

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat

data class Conversation(
    val address: String,
    val contactName: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int
)

data class SmsMessage(
    val id: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val isIncoming: Boolean,
    val isVoice: Boolean = false,
    val mediaUri: String? = null,
    val mimeType: String? = null
)

data class SimInfo(val subscriptionId: Int, val displayName: String, val slotIndex: Int, val number: String? = null)

private data class MmsPart(val uri: String, val mimeType: String, val name: String?)

object SmsHelper {

    private val contactCache = HashMap<String, String>()

    fun getConversations(ctx: Context, limit: Int = 250): List<Conversation> {
        val list = mutableListOf<Conversation>()
        val uri = Telephony.Sms.CONTENT_URI
        val proj = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)

        val unreadMap = buildUnreadMap(ctx)

        val seen = mutableSetOf<String>()
        try {
            ctx.contentResolver.query(uri, proj, null, null, "${Telephony.Sms.DATE} DESC")?.use { c ->
                while (c.moveToNext()) {
                    if (limit > 0 && seen.size >= limit) break
                    val addr = c.getString(0) ?: continue
                    if (addr in seen) continue
                    seen += addr
                    list += Conversation(
                        address      = addr,
                        contactName  = getContactName(ctx, addr),
                        lastMessage  = c.getString(1) ?: "",
                        timestamp    = c.getLong(2),
                        unreadCount  = unreadMap[addr] ?: 0
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun buildUnreadMap(ctx: Context): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.READ} = 0",
                null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val addr = c.getString(0) ?: continue
                    map[addr] = (map[addr] ?: 0) + 1
                }
            }
        } catch (_: Exception) {}
        return map
    }

    fun getMessages(ctx: Context, address: String): List<SmsMessage> {
        val sms = getSmsMessages(ctx, address)
        val mms = getMmsMediaMessages(ctx, address)
        return (sms + mms).sortedBy { it.timestamp }
    }

    private fun getSmsMessages(ctx: Context, address: String): List<SmsMessage> {
        val list = mutableListOf<SmsMessage>()
        val proj = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                           Telephony.Sms.DATE, Telephony.Sms.TYPE)
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI, proj,
                "${Telephony.Sms.ADDRESS} = ?", arrayOf(address),
                "${Telephony.Sms.DATE} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    list += SmsMessage(
                        id         = c.getLong(0),
                        address    = c.getString(1) ?: address,
                        body       = c.getString(2) ?: "",
                        timestamp  = c.getLong(3),
                        isIncoming = c.getInt(4) == Telephony.Sms.MESSAGE_TYPE_INBOX
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    // Fetches all MMS media (voice, image, video, file) for a conversation
    private fun getMmsMediaMessages(ctx: Context, address: String): List<SmsMessage> {
        val result = mutableListOf<SmsMessage>()
        try {
            val mmsIds = mutableSetOf<Long>()
            val addrVariants = buildAddressVariants(address)
            val placeholders = addrVariants.joinToString(",") { "?" }
            ctx.contentResolver.query(
                android.net.Uri.parse("content://mms/addr"),
                arrayOf("mid"),
                "address IN ($placeholders)",
                addrVariants.toTypedArray(),
                null
            )?.use { c -> while (c.moveToNext()) mmsIds.add(c.getLong(0)) }

            if (mmsIds.isEmpty()) return result

            val idPlaceholders = mmsIds.joinToString(",") { "?" }
            val proj = arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX)
            ctx.contentResolver.query(
                Telephony.Mms.CONTENT_URI, proj,
                "_id IN ($idPlaceholders)",
                mmsIds.map { it.toString() }.toTypedArray(),
                "${Telephony.Mms.DATE} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val mmsId = c.getLong(0)
                    val date  = c.getLong(1) * 1000L
                    val isIn  = c.getInt(2) == Telephony.Mms.MESSAGE_BOX_INBOX
                    val part  = getMmsFirstMediaPart(ctx, mmsId) ?: continue
                    val isVoice = part.mimeType.startsWith("audio/")
                    val body = when {
                        isVoice -> "🎤 Voice message"
                        part.mimeType.startsWith("image/") -> ""
                        part.mimeType.startsWith("video/") -> "🎬 Video"
                        else -> "📎 ${part.name ?: "File"}"
                    }
                    result += SmsMessage(mmsId, address, body, date, isIn,
                        isVoice = isVoice, mediaUri = part.uri, mimeType = part.mimeType)
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun buildAddressVariants(address: String): List<String> {
        val digits = address.replace("[^\\d]".toRegex(), "")
        val variants = mutableSetOf(address)
        if (digits.length >= 9) {
            variants += digits
            variants += digits.takeLast(9)
            if (digits.startsWith("61") && digits.length > 9) {
                variants += "0${digits.drop(2)}"
                variants += "+${digits}"
            } else if (digits.startsWith("0") && digits.length == 10) {
                variants += "61${digits.drop(1)}"
                variants += "+61${digits.drop(1)}"
            }
        }
        return variants.toList()
    }

    // Returns first non-SMIL, non-text part of an MMS — handles audio/image/video/file
    private fun getMmsFirstMediaPart(ctx: Context, mmsId: Long): MmsPart? {
        ctx.contentResolver.query(
            android.net.Uri.parse("content://mms/part"),
            arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.NAME),
            "mid = ?", arrayOf(mmsId.toString()), null
        )?.use { c ->
            while (c.moveToNext()) {
                val ct = c.getString(1) ?: continue
                if (ct == "application/smil" || ct.startsWith("text/")) continue
                return MmsPart(
                    uri = "content://mms/part/${c.getLong(0)}",
                    mimeType = ct,
                    name = c.getString(2)
                )
            }
        }
        return null
    }

    fun saveIncomingSms(ctx: Context, address: String, body: String) {
        val values = android.content.ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, System.currentTimeMillis())
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
        }
        try { ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) } catch (_: Exception) {}
    }

    fun sendVoiceMms(ctx: Context, to: String, audioFile: java.io.File, subId: Int = -1) {
        val threadId = try { Telephony.Threads.getOrCreateThreadId(ctx, to) } catch (_: Exception) { 0L }
        val msgValues = android.content.ContentValues().apply {
            put("thread_id", threadId)
            put("date", System.currentTimeMillis() / 1000)
            put("msg_box", 4)
            put("m_type", 128)
            put("v", 18)
            put("ct", "application/vnd.wap.multipart.related")
            put("read", 1); put("seen", 1)
            if (subId >= 0) put("sub_id", subId)
        }
        val mmsUri = ctx.contentResolver.insert(android.net.Uri.parse("content://mms"), msgValues) ?: return
        val mmsId  = mmsUri.lastPathSegment ?: return

        ctx.contentResolver.insert(android.net.Uri.parse("content://mms/$mmsId/addr"), android.content.ContentValues().apply {
            put("address", to); put("type", 151); put("charset", 106)
        })

        val smil = "<smil><head><layout><root-layout/></layout></head><body><par dur=\"10000ms\"><audio src=\"audio.amr\"/></par></body></smil>"
        val smilUri = ctx.contentResolver.insert(android.net.Uri.parse("content://mms/$mmsId/part"), android.content.ContentValues().apply {
            put("mid", mmsId); put("ct", "application/smil"); put("cid", "<smil>"); put("cl", "smil.xml")
        })
        smilUri?.let { ctx.contentResolver.openOutputStream(it)?.use { os -> os.write(smil.toByteArray()) } }

        val partUri = ctx.contentResolver.insert(android.net.Uri.parse("content://mms/$mmsId/part"), android.content.ContentValues().apply {
            put("mid", mmsId); put("ct", "audio/amr"); put("name", "audio.amr"); put("cid", "<audio>")
        }) ?: return
        ctx.contentResolver.openOutputStream(partUri)?.use { out -> audioFile.inputStream().copyTo(out) }

        @Suppress("DEPRECATION")
        val sm = if (subId >= 0) android.telephony.SmsManager.getSmsManagerForSubscriptionId(subId)
                 else android.telephony.SmsManager.getDefault()
        sm.sendMultimediaMessage(ctx, mmsUri, null, null, null)
    }

    fun sendMediaMms(ctx: Context, to: String, mediaUri: android.net.Uri, mimeType: String, subId: Int = -1) {
        val threadId = try { Telephony.Threads.getOrCreateThreadId(ctx, to) } catch (_: Exception) { 0L }
        val ext = mimeType.substringAfter("/").take(8)
        val fileName = "media.$ext"
        val mediaSmilTag = when {
            mimeType.startsWith("image/") -> "img"
            mimeType.startsWith("video/") -> "video"
            mimeType.startsWith("audio/") -> "audio"
            else -> null
        }
        val msgCt = if (mediaSmilTag != null) "application/vnd.wap.multipart.related"
                    else "application/vnd.wap.multipart.mixed"

        val msgValues = android.content.ContentValues().apply {
            put("thread_id", threadId)
            put("date", System.currentTimeMillis() / 1000)
            put("msg_box", 4)
            put("m_type", 128)
            put("v", 18)
            put("ct", msgCt)
            put("read", 1); put("seen", 1)
            if (subId >= 0) put("sub_id", subId)
        }
        val mmsUri = ctx.contentResolver.insert(android.net.Uri.parse("content://mms"), msgValues) ?: return
        val mmsId  = mmsUri.lastPathSegment ?: return

        ctx.contentResolver.insert(android.net.Uri.parse("content://mms/$mmsId/addr"), android.content.ContentValues().apply {
            put("address", to); put("type", 151); put("charset", 106)
        })

        if (mediaSmilTag != null) {
            val smil = "<smil><head><layout><root-layout/></layout></head><body><par dur=\"10000ms\"><$mediaSmilTag src=\"$fileName\"/></par></body></smil>"
            val smilUri = ctx.contentResolver.insert(android.net.Uri.parse("content://mms/$mmsId/part"), android.content.ContentValues().apply {
                put("mid", mmsId); put("ct", "application/smil"); put("cid", "<smil>"); put("cl", "smil.xml")
            })
            smilUri?.let { ctx.contentResolver.openOutputStream(it)?.use { os -> os.write(smil.toByteArray()) } }
        }

        val partUri = ctx.contentResolver.insert(android.net.Uri.parse("content://mms/$mmsId/part"), android.content.ContentValues().apply {
            put("mid", mmsId); put("ct", mimeType); put("name", fileName); put("cid", "<media>")
        }) ?: return

        ctx.contentResolver.openInputStream(mediaUri)?.use { input ->
            ctx.contentResolver.openOutputStream(partUri)?.use { output -> input.copyTo(output) }
        }

        @Suppress("DEPRECATION")
        val sm = if (subId >= 0) android.telephony.SmsManager.getSmsManagerForSubscriptionId(subId)
                 else android.telephony.SmsManager.getDefault()
        sm.sendMultimediaMessage(ctx, mmsUri, null, null, null)
    }

    fun getContactName(ctx: Context, address: String): String {
        if (address.isBlank()) return address
        contactCache[address]?.let { return it }
        try {
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(address).build()
            ctx.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use {
                if (it.moveToFirst()) {
                    val name = it.getString(0)
                    contactCache[address] = name
                    return name
                }
            }
        } catch (_: Exception) {}
        return address
    }

    @SuppressLint("MissingPermission")
    fun getSims(ctx: Context): List<SimInfo> {
        return try {
            val sm = ctx.getSystemService(android.telephony.SubscriptionManager::class.java) ?: return emptyList()
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
            (sm.activeSubscriptionInfoList ?: emptyList()).map { sub ->
                SimInfo(
                    sub.subscriptionId,
                    sub.displayName?.toString() ?: "SIM ${sub.simSlotIndex + 1}",
                    sub.simSlotIndex,
                    sub.number?.takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun sendSms(ctx: Context, address: String, body: String, subscriptionId: Int = -1) {
        @Suppress("DEPRECATION")
        val smsManager = if (subscriptionId >= 0 && android.os.Build.VERSION.SDK_INT >= 22)
            android.telephony.SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        else android.telephony.SmsManager.getDefault()

        smsManager.sendTextMessage(address, null, body, null, null)
        val values = android.content.ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            put(Telephony.Sms.READ, 1)
        }
        try { ctx.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values) } catch (_: Exception) {}
    }

    fun markRead(ctx: Context, address: String) {
        val values = android.content.ContentValues().apply { put(Telephony.Sms.READ, 1) }
        val sel = "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0"
        try { ctx.contentResolver.update(Telephony.Sms.CONTENT_URI, values, sel, arrayOf(address)) } catch (_: Exception) {}
    }
}
