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

    fun getMessagesByThread(ctx: Context, threadId: Long, primaryAddress: String): List<SmsMessage> {
        val fromDb = (getSmsMessagesByThread(ctx, threadId) + getMmsMessagesByThread(ctx, threadId, primaryAddress))
            .sortedBy { it.timestamp }
        // Remove locally-stored sent entries that now appear in the real DB (Samsung wrote them back)
        SentMmsStore.dedup(ctx, primaryAddress, fromDb)
        val localSent = SentMmsStore.loadForAddress(ctx, primaryAddress)
        return (fromDb + localSent).sortedBy { it.timestamp }
    }

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
            // Step 1: fetch all MMS rows in this thread (1 query)
            data class MmsRow(val id: Long, val date: Long, val msgBox: Int)
            val rows = mutableListOf<MmsRow>()
            ctx.contentResolver.query(Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX),
                "${Telephony.Mms.THREAD_ID} = ?", arrayOf(threadId.toString()),
                "${Telephony.Mms.DATE} ASC")?.use { c ->
                while (c.moveToNext())
                    rows += MmsRow(c.getLong(0), c.getLong(1) * 1000L, c.getInt(2))
            }
            if (rows.isEmpty()) return result

            val ids  = rows.map { it.id }
            val idPh = ids.joinToString(",") { "?" }
            val idArgs = ids.map { it.toString() }.toTypedArray()

            // Step 2: batch-fetch all parts (1 query instead of N)
            val mediaPartMap = mutableMapOf<Long, MmsPart>()
            val textPartMap  = mutableMapOf<Long, MmsPart>()
            ctx.contentResolver.query(
                Uri.parse("content://mms/part"),
                arrayOf("mid", Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE,
                        Telephony.Mms.Part.NAME, "text"),
                "mid IN ($idPh)", idArgs, null)?.use { c ->
                while (c.moveToNext()) {
                    val mid = c.getLong(0); val ct = c.getString(2) ?: continue
                    if (ct == "application/smil") continue
                    val partUri = "content://mms/part/${c.getLong(1)}"
                    if (ct.startsWith("text/")) {
                        if (!textPartMap.containsKey(mid))
                            textPartMap[mid] = MmsPart(partUri, ct, c.getString(3), c.getString(4) ?: "")
                    } else if (!mediaPartMap.containsKey(mid)) {
                        mediaPartMap[mid] = MmsPart(partUri, ct, c.getString(3))
                    }
                }
            }

            // Step 3: batch-fetch sender addresses for incoming messages (1 query instead of N)
            val senderMap = mutableMapOf<Long, String>()
            val inIds = ids.filter { id -> rows.first { it.id == id }.msgBox == Telephony.Mms.MESSAGE_BOX_INBOX }
            if (inIds.isNotEmpty()) {
                val inPh   = inIds.joinToString(",") { "?" }
                val inArgs = inIds.map { it.toString() }.toTypedArray()
                try {
                    ctx.contentResolver.query(
                        Uri.parse("content://mms/addr"),
                        arrayOf("msg_id", "address"),
                        "msg_id IN ($inPh) AND type = 137", inArgs, null)?.use { c ->
                        while (c.moveToNext()) {
                            val addr = c.getString(1)
                            if (!addr.isNullOrBlank() && addr != "insert-address-token")
                                senderMap.putIfAbsent(c.getLong(0), addr)
                        }
                    }
                } catch (_: Exception) {
                    // Fallback: per-row query if batch addr query unsupported
                    for (id in inIds) {
                        getMmsSenderAddress(ctx, id)?.let { senderMap[id] = it }
                    }
                }
            }

            // Step 4: assemble results
            for (row in rows) {
                val part = mediaPartMap[row.id] ?: textPartMap[row.id] ?: continue
                val isIn    = row.msgBox == Telephony.Mms.MESSAGE_BOX_INBOX
                val isVoice = part.mimeType.startsWith("audio/")
                val body = when {
                    part.mimeType == "text/plain"          -> part.textBody ?: ""
                    isVoice                                -> "🎤 Voice message"
                    part.mimeType.startsWith("image/")     -> ""
                    part.mimeType.startsWith("video/")     -> "🎬 Video"
                    else                                   -> "📎 ${part.name ?: "File"}"
                }
                val senderAddr = if (isIn) senderMap[row.id] else null
                result += SmsMessage(
                    id         = row.id,
                    threadId   = threadId,
                    address    = senderAddr ?: primaryAddress,
                    body       = body,
                    timestamp  = row.date,
                    isIncoming = isIn,
                    senderName = senderAddr?.let { getContactName(ctx, it) },
                    isMms      = true,
                    isVoice    = isVoice,
                    mediaUri   = if (part.mimeType == "text/plain") null else part.uri,
                    mimeType   = if (part.mimeType == "text/plain") null else part.mimeType
                )
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
                  else try { Telephony.Threads.getOrCreateThreadId(ctx, participants.toSet()) }
                       catch (e: Exception) { throw Exception("Cannot resolve thread: ${e.message}") }
        val parts = listOf(MmsPduBuilder.Part("text/plain", text.toByteArray(Charsets.UTF_8)))
        sendViaMms(ctx, participants, parts, subId)
        return tid
    }

    fun sendVoiceMms(ctx: Context, to: String, audioFile: java.io.File, subId: Int = -1) {
        val parts = listOf(MmsPduBuilder.Part("audio/amr", audioFile.readBytes()))
        sendViaMms(ctx, listOf(to), parts, subId)
    }

    fun sendMediaMms(ctx: Context, to: String, mediaUri: android.net.Uri, mimeType: String,
                     subId: Int = -1, extraRecipients: List<String> = emptyList()) {
        val recipients = (listOf(to) + extraRecipients).distinct()
        val (data, effectiveMime) = compressForMms(ctx, mediaUri, mimeType)
        android.util.Log.d("NexLink_MMS", "sendMediaMms: to=$to mime=$effectiveMime bytes=${data.size}")
        val parts = listOf(MmsPduBuilder.Part(effectiveMime, data))
        sendViaMms(ctx, recipients, parts, subId)
    }

    /**
     * Compresses images to ≤ 500 KB for MMS. Non-image types are returned as-is
     * (audio/AMR from the voice recorder is already small; video is passed through).
     * Returns the compressed bytes and the effective MIME type (always image/jpeg for images).
     */
    private fun compressForMms(ctx: Context, mediaUri: android.net.Uri, mimeType: String): Pair<ByteArray, String> {
        val raw = ctx.contentResolver.openInputStream(mediaUri)?.use { it.readBytes() }
            ?: throw Exception("Cannot read media file")

        if (!mimeType.startsWith("image/")) return Pair(raw, mimeType)

        val MAX_BYTES = 500 * 1024  // 500 KB target
        val MAX_DIM   = 1024        // max dimension in pixels

        // Decode bounds only to find the sampling factor needed
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)

        var sample = 1
        var w = bounds.outWidth; var h = bounds.outHeight
        while (w > MAX_DIM || h > MAX_DIM) { sample *= 2; w /= 2; h /= 2 }

        val bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return Pair(raw, mimeType)

        var quality = 85
        var result: ByteArray
        do {
            val baos = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, baos)
            result = baos.toByteArray()
            quality -= 10
        } while (result.size > MAX_BYTES && quality >= 20)

        bmp.recycle()
        android.util.Log.d("NexLink_MMS", "compressForMms: ${raw.size / 1024}KB → ${result.size / 1024}KB (q=${ quality + 10})")
        return Pair(result, "image/jpeg")
    }

    @Suppress("DEPRECATION")
    private fun sendViaMms(ctx: Context, recipients: List<String>, parts: List<MmsPduBuilder.Part>,
                           subId: Int) {
        // Generate one Transaction-ID used in both the PDU and the content://mms outbox row.
        // Samsung's MmsService reads tr_id from content://mms/outbox to embed in the actual
        // M-Send.req it posts to the MMSC — without a matching row it falls back to "Unknown",
        // which causes Optus MMSC to return error 3514.
        val txId     = MmsPduBuilder.generateTxId()
        val pduBytes = MmsPduBuilder.build(recipients, parts, txId)
        android.util.Log.d("NexLink_MMS", "sendViaMms: recipients=$recipients txId=$txId pdu=${pduBytes.size}B subId=$subId")

        // Attempt 1: direct HTTP POST to carrier MMSC (full PDU control, bypasses Samsung layer)
        if (MmsSender.send(ctx, pduBytes, subId) == 0) {
            android.util.Log.d("NexLink_MMS", "sendViaMms: direct HTTP succeeded"); return
        }

        // Attempt 2: MmsFileProvider + outbox row — matches QKSMS / Fossify Messages exactly.
        //
        // Step A: Insert message into content://mms/outbox with tr_id so Samsung's MmsService
        //         can find the row and embed a proper Transaction-ID in its PDU recomposition.
        val outboxUri = runCatching { insertToOutbox(ctx, recipients, parts, txId) }.getOrNull()
        android.util.Log.d("NexLink_MMS", "sendViaMms: outbox row=$outboxUri")

        // Step B: Write PDU to cache file (MmsFileProvider, exported=false grantUriPermissions=true)
        val contentUri = MmsFileProvider.writePdu(ctx, pduBytes)

        // Step C: Send — locationUrl=null lets Samsung use its own APN config
        @Suppress("DEPRECATION")
        val configOverrides = android.os.Bundle().apply {
            putBoolean(android.telephony.SmsManager.MMS_CONFIG_GROUP_MMS_ENABLED, true)
            putInt(android.telephony.SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 1_200_000)
        }
        android.util.Log.d("NexLink_MMS", "sendViaMms: MmsFileProvider uri=$contentUri")
        getSmsManager(subId).sendMultimediaMessage(
            ctx.applicationContext, contentUri, null, configOverrides, makeSentIntent(ctx))
        android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed({ java.io.File(ctx.cacheDir, contentUri.lastPathSegment ?: "").delete() }, 180_000L)
    }

    private fun insertToOutbox(ctx: Context, recipients: List<String>,
                               parts: List<MmsPduBuilder.Part>, txId: String): Uri {
        val mmsUri = ctx.contentResolver.insert(Telephony.Mms.CONTENT_URI, ContentValues().apply {
            put(Telephony.Mms.MESSAGE_TYPE,  128)   // M-Send.req = 0x80
            put(Telephony.Mms.MESSAGE_BOX,   Telephony.Mms.MESSAGE_BOX_OUTBOX)
            put(Telephony.Mms.CONTENT_TYPE,  "application/vnd.wap.multipart.related")
            put(Telephony.Mms.DATE,          System.currentTimeMillis() / 1000L)
            put(Telephony.Mms.READ,          1)
            put("tr_id",                     txId)  // Telephony.Mms.TRANSACTION_ID
        }) ?: throw Exception("insertToOutbox: insert failed")

        val id      = android.content.ContentUris.parseId(mmsUri)
        val addrUri = Uri.parse("content://mms/$id/addr")
        val partUri = Uri.parse("content://mms/$id/part")

        // FROM address
        ctx.contentResolver.insert(addrUri, ContentValues().apply {
            put("address", "insert-address-token"); put("type", 137); put("charset", 106)
        })
        // TO addresses
        for (to in recipients) {
            ctx.contentResolver.insert(addrUri, ContentValues().apply {
                put("address", MmsPduBuilder.normalizeToE164(to)); put("type", 151); put("charset", 106)
            })
        }
        // Media parts (Samsung may use these to recompose the PDU)
        for (part in parts) {
            val pUri = ctx.contentResolver.insert(partUri, ContentValues().apply {
                put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
                put(Telephony.Mms.Part.NAME, outboxPartName(part.contentType))
                if (part.contentType.startsWith("text/")) put(Telephony.Mms.Part.CHARSET, 106)
            }) ?: continue
            ctx.contentResolver.openOutputStream(pUri)?.use { it.write(part.data) }
        }

        android.util.Log.d("NexLink_MMS", "insertToOutbox: $mmsUri txId=$txId parts=${parts.size}")
        return mmsUri
    }

    private fun outboxPartName(mime: String) = when {
        mime.contains("jpeg") || mime.contains("jpg") -> "photo.jpg"
        mime.contains("png")      -> "photo.png"
        mime.contains("amr")      -> "voice.amr"
        mime.contains("mp4")      -> "video.mp4"
        mime.contains("3gp")      -> "video.3gp"
        mime.startsWith("audio/") -> "audio.mp3"
        mime.startsWith("text/")  -> "message.txt"
        else                      -> "file.dat"
    }

    @Suppress("DEPRECATION")
    private fun getSmsManager(subId: Int) =
        if (subId >= 0) android.telephony.SmsManager.getSmsManagerForSubscriptionId(subId)
        else android.telephony.SmsManager.getDefault()

    private fun makeSentIntent(ctx: Context) = android.app.PendingIntent.getBroadcast(
        ctx.applicationContext,
        (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
        android.content.Intent("${ctx.packageName}.MMS_SENT").setPackage(ctx.packageName),
        android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
    )

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
