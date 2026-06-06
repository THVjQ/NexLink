package com.nexlink.app.db

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat

data class Conversation(
    val threadId: Long = 0,
    val address: String,
    val participants: List<String> = emptyList(),
    val contactName: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int
)

data class SmsMessage(
    val id: Long,
    val threadId: Long = 0,
    val address: String,
    val body: String,
    val timestamp: Long,
    val isIncoming: Boolean,
    val senderName: String? = null,
    val isMms: Boolean = false,
    val isVoice: Boolean = false,
    val mediaUri: String? = null,
    val mimeType: String? = null
)

data class SimInfo(val subscriptionId: Int, val displayName: String, val slotIndex: Int, val number: String? = null)

private data class MmsPart(val uri: String, val mimeType: String, val name: String?, val textBody: String? = null)

object SmsHelper {

    private val contactCache = HashMap<String, String>()

    // ── Conversations ─────────────────────────────────────────────────────────

    fun getConversations(ctx: Context, limit: Int = 250): List<Conversation> {
        val canonMap  = buildCanonicalAddressMap(ctx)
        val unreadMap = buildUnreadByThread(ctx)
        val list      = mutableListOf<Conversation>()
        try {
            ctx.contentResolver.query(
                Uri.parse("content://mms-sms/conversations?simple=true"),
                null, null, null, "date DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    if (limit > 0 && list.size >= limit) break
                    val tidIdx     = c.getColumnIndex("_id")
                    val dateIdx    = c.getColumnIndex("date")
                    val snippetIdx = c.getColumnIndex("snippet")
                    val recipIdx   = c.getColumnIndex("recipient_ids")
                    if (tidIdx < 0 || recipIdx < 0) continue
                    val threadId   = c.getLong(tidIdx)
                    val rawDate    = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                    val date       = if (rawDate < 1_000_000_000_000L) rawDate * 1000L else rawDate
                    val snippet    = if (snippetIdx >= 0) c.getString(snippetIdx) ?: "" else ""
                    val recipIds   = c.getString(recipIdx) ?: continue
                    val parts      = recipIds.trim().split(" ")
                        .mapNotNull { sid -> canonMap[sid.toLongOrNull() ?: -1L] }
                        .filter { it.isNotBlank() }
                    if (parts.isEmpty()) continue
                    val primary    = parts.first()
                    val name       = if (parts.size > 1) parts.joinToString(", ") { getContactName(ctx, it) }
                                     else getContactName(ctx, primary)
                    list += Conversation(
                        threadId    = threadId,
                        address     = primary,
                        participants = parts,
                        contactName = name,
                        lastMessage = snippet,
                        timestamp   = date,
                        unreadCount = unreadMap[threadId] ?: 0
                    )
                }
            }
        } catch (_: Exception) {}
        if (list.isEmpty()) return getConversationsFallback(ctx, limit)
        return list
    }

    private fun getConversationsFallback(ctx: Context, limit: Int): List<Conversation> {
        val list      = mutableListOf<Conversation>()
        val proj      = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.THREAD_ID)
        val unreadMap = buildUnreadMap(ctx)
        val seen      = mutableSetOf<String>()
        try {
            ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, proj, null, null,
                "${Telephony.Sms.DATE} DESC")?.use { c ->
                while (c.moveToNext()) {
                    if (limit > 0 && seen.size >= limit) break
                    val addr = c.getString(0) ?: continue
                    if (addr in seen) continue
                    seen += addr
                    list += Conversation(
                        threadId    = c.getLong(3),
                        address     = addr,
                        contactName = getContactName(ctx, addr),
                        lastMessage = c.getString(1) ?: "",
                        timestamp   = c.getLong(2),
                        unreadCount = unreadMap[addr] ?: 0
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun buildCanonicalAddressMap(ctx: Context): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        try {
            ctx.contentResolver.query(Uri.parse("content://mms-sms/canonical-addresses"),
                null, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val idIdx   = c.getColumnIndex("_id")
                    val addrIdx = c.getColumnIndex("address")
                    if (idIdx < 0 || addrIdx < 0) break
                    map[c.getLong(idIdx)] = c.getString(addrIdx) ?: continue
                }
            }
        } catch (_: Exception) {}
        return map
    }

    private fun buildUnreadByThread(ctx: Context): Map<Long, Int> {
        val map = mutableMapOf<Long, Int>()
        fun addFrom(uri: Uri, threadCol: String) {
            try {
                ctx.contentResolver.query(uri, arrayOf(threadCol), "read = 0", null, null)
                    ?.use { c -> while (c.moveToNext()) { val t = c.getLong(0); map[t] = (map[t] ?: 0) + 1 } }
            } catch (_: Exception) {}
        }
        addFrom(Telephony.Sms.CONTENT_URI, Telephony.Sms.THREAD_ID)
        addFrom(Telephony.Mms.CONTENT_URI, Telephony.Mms.THREAD_ID)
        return map
    }

    private fun buildUnreadMap(ctx: Context): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        try {
            ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.READ} = 0", null, null)?.use { c ->
                while (c.moveToNext()) { val a = c.getString(0) ?: continue; map[a] = (map[a] ?: 0) + 1 }
            }
        } catch (_: Exception) {}
        return map
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    fun getMessages(ctx: Context, address: String): List<SmsMessage> {
        val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(ctx, address) }.getOrDefault(0L)
        return if (threadId > 0) getMessagesByThread(ctx, threadId, address)
               else (getSmsMessages(ctx, address) + getMmsMediaMessages(ctx, address)).sortedBy { it.timestamp }
    }

    fun getMessagesByThread(ctx: Context, threadId: Long, primaryAddress: String): List<SmsMessage> =
        (getSmsMessagesByThread(ctx, threadId) + getMmsMessagesByThread(ctx, threadId, primaryAddress))
            .sortedBy { it.timestamp }

    private fun getSmsMessagesByThread(ctx: Context, threadId: Long): List<SmsMessage> {
        val list = mutableListOf<SmsMessage>()
        val proj = arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
                           Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
        try {
            ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, proj,
                "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC")?.use { c ->
                while (c.moveToNext()) {
                    list += SmsMessage(
                        id         = c.getLong(0),
                        threadId   = c.getLong(1),
                        address    = c.getString(2) ?: "",
                        body       = c.getString(3) ?: "",
                        timestamp  = c.getLong(4),
                        isIncoming = c.getInt(5) == Telephony.Sms.MESSAGE_TYPE_INBOX
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun getMmsMessagesByThread(ctx: Context, threadId: Long, primaryAddress: String): List<SmsMessage> {
        val result = mutableListOf<SmsMessage>()
        try {
            val proj = arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX)
            ctx.contentResolver.query(Telephony.Mms.CONTENT_URI, proj,
                "${Telephony.Mms.THREAD_ID} = ?", arrayOf(threadId.toString()),
                "${Telephony.Mms.DATE} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val mmsId = c.getLong(0)
                    val date  = c.getLong(1) * 1000L
                    val isIn  = c.getInt(2) == Telephony.Mms.MESSAGE_BOX_INBOX
                    val part  = getMmsFirstMediaPart(ctx, mmsId) ?: continue
                    val isVoice = part.mimeType.startsWith("audio/")
                    val body = when {
                        part.mimeType == "text/plain"           -> part.textBody ?: ""
                        isVoice                                  -> "🎤 Voice message"
                        part.mimeType.startsWith("image/")      -> ""
                        part.mimeType.startsWith("video/")      -> "🎬 Video"
                        else                                     -> "📎 ${part.name ?: "File"}"
                    }
                    val senderAddr = if (isIn) getMmsSenderAddress(ctx, mmsId) else null
                    result += SmsMessage(
                        id         = mmsId,
                        threadId   = threadId,
                        address    = senderAddr ?: primaryAddress,
                        body       = body,
                        timestamp  = date,
                        isIncoming = isIn,
                        senderName = senderAddr?.let { getContactName(ctx, it) },
                        isMms      = true,
                        isVoice    = isVoice,
                        mediaUri   = if (part.mimeType == "text/plain") null else part.uri,
                        mimeType   = if (part.mimeType == "text/plain") null else part.mimeType
                    )
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun getMmsSenderAddress(ctx: Context, mmsId: Long): String? {
        ctx.contentResolver.query(Uri.parse("content://mms/$mmsId/addr"),
            arrayOf("address"), "type = 137", null, null)?.use { c ->
            if (c.moveToFirst()) {
                val a = c.getString(0)
                if (!a.isNullOrBlank() && a != "insert-address-token") return a
            }
        }
        return null
    }

    // Legacy address-based MMS fetch (fallback when thread_id unavailable)
    private fun getMmsMediaMessages(ctx: Context, address: String): List<SmsMessage> {
        val result = mutableListOf<SmsMessage>()
        try {
            val variants      = buildAddressVariants(address)
            val placeholders  = variants.joinToString(",") { "?" }
            val mmsIds        = mutableSetOf<Long>()
            ctx.contentResolver.query(Uri.parse("content://mms/addr"), arrayOf("mid"),
                "address IN ($placeholders)", variants.toTypedArray(), null)
                ?.use { c -> while (c.moveToNext()) mmsIds.add(c.getLong(0)) }
            if (mmsIds.isEmpty()) return result
            val idPh = mmsIds.joinToString(",") { "?" }
            ctx.contentResolver.query(Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX),
                "_id IN ($idPh)", mmsIds.map { it.toString() }.toTypedArray(),
                "${Telephony.Mms.DATE} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val mmsId   = c.getLong(0)
                    val date    = c.getLong(1) * 1000L
                    val isIn    = c.getInt(2) == Telephony.Mms.MESSAGE_BOX_INBOX
                    val part    = getMmsFirstMediaPart(ctx, mmsId) ?: continue
                    val isVoice = part.mimeType.startsWith("audio/")
                    val body = when {
                        part.mimeType == "text/plain"      -> part.textBody ?: ""
                        isVoice                             -> "🎤 Voice message"
                        part.mimeType.startsWith("image/") -> ""
                        part.mimeType.startsWith("video/") -> "🎬 Video"
                        else                               -> "📎 ${part.name ?: "File"}"
                    }
                    result += SmsMessage(mmsId, address = address, body = body, timestamp = date,
                        isIncoming = isIn, isMms = true, isVoice = isVoice,
                        mediaUri = if (part.mimeType == "text/plain") null else part.uri,
                        mimeType = if (part.mimeType == "text/plain") null else part.mimeType)
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun getSmsMessages(ctx: Context, address: String): List<SmsMessage> {
        val list = mutableListOf<SmsMessage>()
        val proj = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                           Telephony.Sms.DATE, Telephony.Sms.TYPE)
        try {
            ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, proj,
                "${Telephony.Sms.ADDRESS} = ?", arrayOf(address),
                "${Telephony.Sms.DATE} ASC")?.use { c ->
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

    private fun buildAddressVariants(address: String): List<String> {
        val digits   = address.replace("[^\\d]".toRegex(), "")
        val variants = mutableSetOf(address)
        if (digits.length >= 9) {
            variants += digits
            variants += digits.takeLast(9)
            if (digits.startsWith("61") && digits.length > 9) {
                variants += "0${digits.drop(2)}"; variants += "+$digits"
            } else if (digits.startsWith("0") && digits.length == 10) {
                variants += "61${digits.drop(1)}"; variants += "+61${digits.drop(1)}"
            }
        }
        return variants.toList()
    }

    private fun getMmsFirstMediaPart(ctx: Context, mmsId: Long): MmsPart? {
        var textFallback: MmsPart? = null
        ctx.contentResolver.query(Uri.parse("content://mms/part"),
            arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.NAME, "text"),
            "mid = ?", arrayOf(mmsId.toString()), null)?.use { c ->
            while (c.moveToNext()) {
                val ct = c.getString(1) ?: continue
                if (ct == "application/smil") continue
                if (ct.startsWith("text/")) {
                    if (textFallback == null)
                        textFallback = MmsPart("content://mms/part/${c.getLong(0)}", ct, c.getString(2), c.getString(3) ?: "")
                    continue
                }
                return MmsPart("content://mms/part/${c.getLong(0)}", ct, c.getString(2))
            }
        }
        return textFallback
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun deleteMessage(ctx: Context, id: Long, isMms: Boolean) {
        try {
            val uri = if (isMms) Telephony.Mms.CONTENT_URI else Telephony.Sms.CONTENT_URI
            ctx.contentResolver.delete(uri, "_id = ?", arrayOf(id.toString()))
        } catch (_: Exception) {}
    }

    fun deleteThread(ctx: Context, threadId: Long) {
        try {
            ctx.contentResolver.delete(Uri.parse("content://mms-sms/conversations/$threadId"), null, null)
        } catch (_: Exception) {}
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    fun sendSms(ctx: Context, address: String, body: String, subscriptionId: Int = -1) {
        @Suppress("DEPRECATION")
        val sm = if (subscriptionId >= 0) android.telephony.SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                 else android.telephony.SmsManager.getDefault()
        sm.sendTextMessage(address, null, body, null, null)
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT); put(Telephony.Sms.READ, 1)
        }
        try { ctx.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values) } catch (_: Exception) {}
    }

    fun sendGroupText(ctx: Context, threadId: Long, participants: List<String>, text: String, subId: Int = -1): Long {
        val tid = if (threadId > 0) threadId
                  else runCatching { Telephony.Threads.getOrCreateThreadId(ctx, participants.toSet()) }.getOrDefault(0L)
        val mmsUri = createMmsRecord(ctx, tid, "application/vnd.wap.multipart.mixed", subId)
        val mmsId  = mmsUri.lastPathSegment ?: throw Exception("Invalid MMS URI")
        participants.forEach { addr -> insertMmsAddr(ctx, mmsId, addr) }
        val partUri = ctx.contentResolver.insert(Uri.parse("content://mms/$mmsId/part"),
            ContentValues().apply {
                put("mid", mmsId); put("ct", "text/plain"); put("cid", "<text>"); put("cl", "text.txt")
            }) ?: throw Exception("Failed to create MMS text part")
        ctx.contentResolver.openOutputStream(partUri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        sendMms(ctx, mmsUri, subId)
        return tid
    }

    fun sendVoiceMms(ctx: Context, to: String, audioFile: java.io.File, subId: Int = -1) {
        val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(ctx, setOf(to)) }.getOrDefault(0L)
        val mmsUri   = createMmsRecord(ctx, threadId, "application/vnd.wap.multipart.related", subId)
        val mmsId    = mmsUri.lastPathSegment ?: throw Exception("Invalid MMS URI")
        insertMmsAddr(ctx, mmsId, to)
        val smil = "<smil><head><layout><root-layout/></layout></head><body><par dur=\"10000ms\"><audio src=\"audio.amr\"/></par></body></smil>"
        insertSmilPart(ctx, mmsId, smil)
        val partUri = ctx.contentResolver.insert(Uri.parse("content://mms/$mmsId/part"),
            ContentValues().apply {
                put("mid", mmsId); put("ct", "audio/amr"); put("name", "audio.amr"); put("cid", "<audio>")
            }) ?: throw Exception("Failed to create MMS audio part")
        ctx.contentResolver.openOutputStream(partUri)?.use { audioFile.inputStream().copyTo(it) }
        sendMms(ctx, mmsUri, subId)
    }

    fun sendMediaMms(ctx: Context, to: String, mediaUri: android.net.Uri, mimeType: String,
                     subId: Int = -1, extraRecipients: List<String> = emptyList()) {
        val allRecipients = (listOf(to) + extraRecipients).toSet()
        val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(ctx, allRecipients) }.getOrDefault(0L)
        val smilTag  = when { mimeType.startsWith("image/") -> "img"; mimeType.startsWith("video/") -> "video"; mimeType.startsWith("audio/") -> "audio"; else -> null }
        val msgCt    = if (smilTag != null) "application/vnd.wap.multipart.related" else "application/vnd.wap.multipart.mixed"
        val mmsUri2  = createMmsRecord(ctx, threadId, msgCt, subId)
        val mmsId    = mmsUri2.lastPathSegment ?: throw Exception("Invalid MMS URI")
        allRecipients.forEach { addr -> insertMmsAddr(ctx, mmsId, addr) }
        val fileName = "media.${mimeType.substringAfter("/").take(8)}"
        if (smilTag != null) {
            val smil = "<smil><head><layout><root-layout/></layout></head><body><par dur=\"10000ms\"><$smilTag src=\"$fileName\"/></par></body></smil>"
            insertSmilPart(ctx, mmsId, smil)
        }
        val partUri = ctx.contentResolver.insert(Uri.parse("content://mms/$mmsId/part"),
            ContentValues().apply {
                put("mid", mmsId); put("ct", mimeType); put("name", fileName); put("cid", "<media>")
            }) ?: throw Exception("Failed to create MMS media part")
        val inputStream = ctx.contentResolver.openInputStream(mediaUri)
            ?: throw Exception("Cannot read selected media file")
        inputStream.use { i ->
            ctx.contentResolver.openOutputStream(partUri)?.use { o -> i.copyTo(o) }
        }
        sendMms(ctx, mmsUri2, subId)
    }

    private fun createMmsRecord(ctx: Context, threadId: Long, contentType: String, subId: Int): Uri {
        val values = ContentValues().apply {
            put("thread_id", threadId); put("date", System.currentTimeMillis() / 1000)
            put("msg_box", 4); put("m_type", 128); put("v", 18); put("ct", contentType)
            put("read", 1); put("seen", 1)
            if (subId >= 0) put("sub_id", subId)
        }
        // SecurityException propagates if NexLink is not the default SMS app
        return ctx.contentResolver.insert(Uri.parse("content://mms"), values)
            ?: throw Exception("Failed to create MMS record — check MMS/APN settings and that NexLink is the default SMS app")
    }

    private fun insertMmsAddr(ctx: Context, mmsId: String, address: String) {
        ctx.contentResolver.insert(Uri.parse("content://mms/$mmsId/addr"),
            ContentValues().apply { put("address", address); put("type", 151); put("charset", 106) })
    }

    private fun insertSmilPart(ctx: Context, mmsId: String, smil: String) {
        val uri = ctx.contentResolver.insert(Uri.parse("content://mms/$mmsId/part"),
            ContentValues().apply {
                put("mid", mmsId); put("ct", "application/smil"); put("cid", "<smil>"); put("cl", "smil.xml")
            })
        uri?.let { ctx.contentResolver.openOutputStream(it)?.use { os -> os.write(smil.toByteArray()) } }
    }

    @Suppress("DEPRECATION")
    private fun sendMms(ctx: Context, mmsUri: Uri, subId: Int) {
        val sm = if (subId >= 0) android.telephony.SmsManager.getSmsManagerForSubscriptionId(subId)
                 else android.telephony.SmsManager.getDefault()
        sm.sendMultimediaMessage(ctx, mmsUri, null, null, null)
    }

    // ── Mark read ─────────────────────────────────────────────────────────────

    fun markRead(ctx: Context, address: String) {
        val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
        try { ctx.contentResolver.update(Telephony.Sms.CONTENT_URI, values,
            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(address)) } catch (_: Exception) {}
    }

    fun markReadByThread(ctx: Context, threadId: Long) {
        val values = ContentValues().apply { put("read", 1) }
        try {
            ctx.contentResolver.update(Telephony.Sms.CONTENT_URI, values,
                "${Telephony.Sms.THREAD_ID} = ? AND read = 0", arrayOf(threadId.toString()))
            ctx.contentResolver.update(Telephony.Mms.CONTENT_URI, values,
                "${Telephony.Mms.THREAD_ID} = ? AND read = 0", arrayOf(threadId.toString()))
        } catch (_: Exception) {}
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    fun isDefaultSmsApp(ctx: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val rm = ctx.getSystemService(android.app.role.RoleManager::class.java)
            rm?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName
        }
    }

    fun saveIncomingSms(ctx: Context, address: String, body: String) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis()); put(Telephony.Sms.DATE_SENT, System.currentTimeMillis())
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX); put(Telephony.Sms.READ, 0); put(Telephony.Sms.SEEN, 0)
        }
        try { ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) } catch (_: Exception) {}
    }

    fun getContactName(ctx: Context, address: String): String {
        if (address.isBlank()) return address
        contactCache[address]?.let { return it }
        try {
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon().appendPath(address).build()
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) { val n = it.getString(0); contactCache[address] = n; return n } }
        } catch (_: Exception) {}
        return address
    }

    @SuppressLint("MissingPermission")
    fun getSims(ctx: Context): List<SimInfo> {
        return try {
            val sm = ctx.getSystemService(android.telephony.SubscriptionManager::class.java) ?: return emptyList()
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
            (sm.activeSubscriptionInfoList ?: emptyList()).map { sub ->
                SimInfo(sub.subscriptionId, sub.displayName?.toString() ?: "SIM ${sub.simSlotIndex + 1}",
                    sub.simSlotIndex, sub.number?.takeIf { it.isNotBlank() })
            }
        } catch (_: Exception) { emptyList() }
    }
}
