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
    val mediaUri: String? = null
)

data class SimInfo(val subscriptionId: Int, val displayName: String, val slotIndex: Int, val number: String? = null)

object SmsHelper {

    private val contactCache = HashMap<String, String>()

    fun getConversations(ctx: Context, limit: Int = 250): List<Conversation> {
        val list = mutableListOf<Conversation>()
        val uri = Telephony.Sms.CONTENT_URI
        val proj = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)

        // Single upfront pass: gather unread counts for all addresses at once
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

    /** One query to get ALL unread rows, grouped by address. Replaces N per-address queries. */
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

    fun getMessages(ctx: Context, address: String, limit: Int = 300): List<SmsMessage> {
        val sms = getSmsMessages(ctx, address, limit)
        val mms = getMmsVoiceMessages(ctx, address)
        return (sms + mms).sortedBy { it.timestamp }
    }

    private fun getSmsMessages(ctx: Context, address: String, limit: Int): List<SmsMessage> {
        val list = mutableListOf<SmsMessage>()
        val proj = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                           Telephony.Sms.DATE, Telephony.Sms.TYPE)
        // DESC LIMIT gets the NEWEST $limit messages; reverse so display is oldest→newest
        val orderBy = if (limit > 0) "${Telephony.Sms.DATE} DESC LIMIT $limit" else "${Telephony.Sms.DATE} ASC"
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI, proj,
                "${Telephony.Sms.ADDRESS} = ?", arrayOf(address),
                orderBy
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
        // DESC query gives newest-first; reverse to chronological for display
        return if (limit > 0) list.reversed() else list
    }

    fun isDefaultSmsApp(ctx: Context): Boolean = try {
        ctx.packageName == android.provider.Telephony.Sms.getDefaultSmsPackage(ctx)
    } catch (_: Exception) { false }

    private fun getMmsVoiceMessages(ctx: Context, address: String): List<SmsMessage> {
        val result = mutableListOf<SmsMessage>()
        try {
            val proj = arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX)
            ctx.contentResolver.query(Telephony.Mms.CONTENT_URI, proj, null, null, "${Telephony.Mms.DATE} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val mmsId  = c.getLong(0)
                    val date   = c.getLong(1) * 1000L
                    val box    = c.getInt(2)
                    val isIn   = box == Telephony.Mms.MESSAGE_BOX_INBOX
                    val addrType = if (isIn) "137" else "151" // FROM=137, TO=151
                    val mmsAddr = getMmsAddress(ctx, mmsId, addrType)
                    if (!numberMatches(mmsAddr, address)) continue
                    val audioUri = getMmsAudioPart(ctx, mmsId) ?: continue
                    result += SmsMessage(mmsId, address, "🎤 Voice message", date, isIn, isVoice = true, mediaUri = audioUri)
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun getMmsAddress(ctx: Context, mmsId: Long, type: String): String {
        ctx.contentResolver.query(
            android.net.Uri.parse("content://mms/$mmsId/addr"),
            arrayOf("address"), "type = ?", arrayOf(type), null
        )?.use { c -> if (c.moveToFirst()) return c.getString(0) ?: "" }
        return ""
    }

    private fun getMmsAudioPart(ctx: Context, mmsId: Long): String? {
        ctx.contentResolver.query(
            android.net.Uri.parse("content://mms/part"),
            arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE),
            "mid = ?", arrayOf(mmsId.toString()), null
        )?.use { c ->
            while (c.moveToNext()) {
                val ct = c.getString(1) ?: continue
                if (ct.startsWith("audio/")) return "content://mms/part/${c.getLong(0)}"
            }
        }
        return null
    }

    private fun numberMatches(a: String, b: String): Boolean {
        fun clean(n: String) = n.replace("[^\\d]".toRegex(), "").takeLast(9)
        return clean(a) == clean(b) && clean(a).isNotEmpty()
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
        ctx.contentResolver.insert(android.net.Uri.parse("content://mms/$mmsId/part"), android.content.ContentValues().apply {
            put("mid", mmsId); put("ct", "application/smil"); put("cid", "<smil>")
            put("_data", smil.toByteArray())
        })

        val partUri = ctx.contentResolver.insert(android.net.Uri.parse("content://mms/$mmsId/part"), android.content.ContentValues().apply {
            put("mid", mmsId); put("ct", "audio/amr"); put("name", "audio.amr"); put("cid", "<audio>")
        }) ?: return
        ctx.contentResolver.openOutputStream(partUri)?.use { out -> audioFile.inputStream().copyTo(out) }

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
