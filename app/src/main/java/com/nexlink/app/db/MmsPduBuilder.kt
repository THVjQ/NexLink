package com.nexlink.app.db

import java.io.ByteArrayOutputStream

/**
 * Builds a WAP binary MMS M-Send.req PDU.
 *
 * Key changes from naive implementations that cause silent carrier drops:
 *  - Includes X-Mms-Date (required by most MMSCs)
 *  - Includes From: insert-address-token (MMSC fills sender; required by many carriers)
 *  - Wraps media in a SMIL presentation (required by Australian and many other carriers)
 *  - Uses application/vnd.wap.multipart.related with type/start params
 *  - Part headers use WSP field encoding (0x97 = Content-Type in WSP, NOT OMA-MMS-ENC)
 *  - Content-ID and Content-Location included per-part so SMIL src refs resolve
 */
object MmsPduBuilder {

    // OMA-MMS-ENC message header field codes (field-index | 0x80)
    private const val MF_MESSAGE_TYPE    = 0x8C  // 0x0C | 0x80
    private const val MF_TRANSACTION_ID  = 0x98  // 0x18 | 0x80
    private const val MF_MMS_VERSION     = 0x8D  // 0x0D | 0x80
    private const val MF_DATE            = 0x85  // 0x05 | 0x80
    private const val MF_FROM            = 0x89  // 0x09 | 0x80
    private const val MF_TO              = 0x97  // 0x17 | 0x80
    private const val MF_CONTENT_TYPE    = 0x84  // 0x04 | 0x80
    private const val MF_MESSAGE_CLASS   = 0x8A  // 0x0A | 0x80
    private const val MF_EXPIRY          = 0x88  // 0x08 | 0x80
    private const val MF_PRIORITY        = 0x8F  // 0x0F | 0x80
    private const val MF_DELIVERY_REPORT = 0x86  // 0x06 | 0x80
    private const val MF_READ_REPLY      = 0x90  // 0x10 | 0x80

    private const val V_M_SEND_REQ      = 0x80
    private const val V_MMS_1_2         = 0x92
    private const val V_INSERT_ADDRESS  = 0x81   // OMA-MMS-ENC §7.3.11
    private const val V_PERSONAL        = 0x80   // Message-Class: Personal
    private const val V_NORMAL          = 0x81   // Priority: Normal
    private const val V_NO              = 0x80   // Delivery-Report / Read-Reply: No
    private const val V_RELATIVE        = 0x81   // Expiry: relative-token

    // WSP well-known param tokens (WAP-230-WSP Table 38)
    // Type  = 0x03 in WSP → short-int 0x83  (NOT 0x09 which is Content-MO-Type)
    // Start = 0x0A in WSP → short-int 0x8A
    private const val WP_TYPE  = 0x83  // 0x03 | 0x80
    private const val WP_START = 0x8A  // 0x0A | 0x80

    // WSP well-known content-type tokens (used in multipart PART headers only)
    private val WSP_CONTENT_TYPE = mapOf(
        "text/plain"          to 0x03,
        "image/gif"           to 0x0D,
        "image/jpeg"          to 0x0E,
        "image/jpg"           to 0x0E,
        "image/tiff"          to 0x0F,
        "image/png"           to 0x10,
        "image/vnd.wap.wbmp"  to 0x11,
        "video/mpeg"          to 0x1C,
        "video/3gpp"          to 0x22,
        "application/vnd.wap.multipart.mixed"   to 0x23,
        "application/vnd.wap.multipart.related" to 0x33,
    )

    data class Part(val contentType: String, val data: ByteArray)

    /** Generate a Transaction-ID in the same format as QKSMS / android-smsmms. */
    fun generateTxId(): String = "T${System.currentTimeMillis().toString(16)}"

    /**
     * Build an M-Send.req PDU.
     * Pass the same [txId] you will store in content://mms tr_id so Samsung's
     * MmsService can match the outbox row and embed a real Transaction-ID.
     */
    fun build(recipients: List<String>, parts: List<Part>,
              txId: String = generateTxId()): ByteArray {
        data class Tagged(val part: Part, val location: String, val cid: String)
        val tagged = parts.mapIndexed { i, p ->
            Tagged(p, fileNameFor(p.contentType, i), "<part$i@nexlink>")
        }

        // Build SMIL document referencing all media parts (required by most MMSCs)
        val smilXml  = buildSmil(tagged.map { Triple(it.part.contentType, it.location, it.cid) })
        val smilData = smilXml.toByteArray(Charsets.UTF_8)

        val out = ByteArrayOutputStream()

        // ── Message headers (order matches QKSMS / android-smsmms PduComposer output) ──
        writeByte(out, MF_MESSAGE_TYPE);   writeByte(out, V_M_SEND_REQ)
        writeByte(out, MF_TRANSACTION_ID); writeText(out, txId)
        writeByte(out, MF_MMS_VERSION);    writeByte(out, V_MMS_1_2)

        // Date (required by most carrier MMSCs even though optional per spec)
        val epochSecs = System.currentTimeMillis() / 1000L
        writeByte(out, MF_DATE); writeLongInteger(out, epochSecs)

        writeByte(out, MF_FROM); writeByte(out, 1); writeByte(out, V_INSERT_ADDRESS)

        for (r in recipients) {
            val normalized = normalizeToE164(r)
            android.util.Log.d("NexLink_MMS", "MmsPduBuilder: To raw='$r' → normalized='$normalized'")
            writeByte(out, MF_TO); writeText(out, normalized)
        }

        // Optional but widely required headers — Optus MMSC returns 3514 without them
        writeByte(out, MF_MESSAGE_CLASS);   writeByte(out, V_PERSONAL)  // Personal
        writeByte(out, MF_PRIORITY);        writeByte(out, V_NORMAL)    // Normal
        writeByte(out, MF_DELIVERY_REPORT); writeByte(out, V_NO)        // No
        writeByte(out, MF_READ_REPLY);      writeByte(out, V_NO)        // No

        // Expiry: 7 days relative = 604800s = 0x093A80 (3 bytes)
        // Value: value-length=5, Relative-token=0x81, Long-integer(3, 0x093A80)
        writeByte(out, MF_EXPIRY); writeByte(out, 5)
        writeByte(out, V_RELATIVE); writeByte(out, 3)
        writeByte(out, 0x09); writeByte(out, 0x3A); writeByte(out, 0x80)

        // Content-Type: application/vnd.wap.multipart.related; type="application/smil"; start="<smil>"
        //   WP_TYPE  = 0x83 (WSP param 0x03 = "type")
        //   WP_START = 0x8A (WSP param 0x0A = "start")
        //   value bytes: 1(B3) + 1(WP_TYPE) + 17("application/smil\0") + 1(WP_START) + 7("<smil>\0") = 27
        writeByte(out, MF_CONTENT_TYPE)
        writeByte(out, 27)         // Short-length = value-length
        writeByte(out, 0xB3)       // application/vnd.wap.multipart.related
        writeByte(out, WP_TYPE);   writeText(out, "application/smil")   // 1 + 17 bytes
        writeByte(out, WP_START);  writeText(out, "<smil>")             // 1 + 7 bytes

        // ── Multipart body: SMIL first, then media parts ──────────────────────
        writeUintVar(out, 1 + tagged.size)   // +1 for SMIL

        // Part 0: SMIL document
        val smilHdr = buildPartHeader("application/smil", "smil.xml", "<smil>")
        writeUintVar(out, smilHdr.size)
        writeUintVar(out, smilData.size)
        out.write(smilHdr)
        out.write(smilData)

        // Remaining parts (image / audio / text)
        for (t in tagged) {
            val hdr = buildPartHeader(t.part.contentType, t.location, t.cid)
            writeUintVar(out, hdr.size)
            writeUintVar(out, t.part.data.size)
            out.write(hdr)
            out.write(t.part.data)
        }

        val hex = out.toByteArray().take(48).joinToString(" ") { "%02X".format(it) }
        android.util.Log.d("NexLink_MMS", "PDU header bytes: $hex")

        return out.toByteArray()
    }

    // Content-Type value bytes for the MESSAGE header (OMA-MMS-ENC Content-general-form)
    // application/vnd.wap.multipart.related; type="application/smil"; start="<smil>"
    // Size: 1 + 1 + 17 + 1 + 7 = 27 bytes — fits in short-length (≤ 30).
    private fun buildMsgContentType(): ByteArray {
        val b = ByteArrayOutputStream()
        b.write(0xB3)       // multipart/related short-int (0x33 | 0x80)
        b.write(WP_TYPE)    // "type" well-known param (0x08 | 0x80)
        writeText(b, "application/smil")   // 17 bytes incl. null
        b.write(WP_START)   // "start" well-known param (0x09 | 0x80)
        writeText(b, "<smil>")             // 7 bytes incl. null
        return b.toByteArray()
    }

    private fun buildPartHeader(contentType: String, location: String, cid: String): ByteArray {
        val b = ByteArrayOutputStream()

        // WAP-230-WSP §8.5: Part Header starts directly with Content-Type value (no field-code byte).
        // Well-known type  → short-integer (token | 0x80)
        // Unknown type     → Extension-media text string (null-terminated)
        val token = WSP_CONTENT_TYPE[contentType.lowercase()]
        if (token != null) {
            b.write(0x80 or (token and 0x7F))
        } else {
            writeText(b, contentType)
        }

        // Subsequent headers use WSP well-known field codes (WAP-230-WSP Table 12).
        // Token-text form ("Content-ID\0") is rejected by Samsung's PduParser.
        // Content-ID       = 0x0C | 0x80 = 0x8C
        // Content-Location = 0x0E | 0x80 = 0x8E
        b.write(0x8C); writeText(b, cid)       // Content-ID value
        b.write(0x8E); writeText(b, location)  // Content-Location value

        return b.toByteArray()
    }

    private fun buildSmil(parts: List<Triple<String, String, String>>): String {
        // Triple: (contentType, location, cid)
        val regions = StringBuilder()
        val body    = StringBuilder()
        var hasImage = false; var hasVideo = false; var hasText = false

        for ((ct, loc, _) in parts) {
            when {
                ct.startsWith("image/") && !hasImage -> {
                    regions.append("<region id=\"Image\" top=\"0\" left=\"0\" height=\"100%\" width=\"100%\" fit=\"meet\"/>")
                    body.append("<img src=\"$loc\" region=\"Image\"/>"); hasImage = true
                }
                ct.startsWith("video/") && !hasVideo -> {
                    regions.append("<region id=\"Video\" top=\"0\" left=\"0\" height=\"100%\" width=\"100%\" fit=\"meet\"/>")
                    body.append("<video src=\"$loc\" region=\"Video\"/>"); hasVideo = true
                }
                ct.startsWith("audio/") -> body.append("<audio src=\"$loc\"/>")
                ct.startsWith("text/plain") && !hasText -> {
                    regions.append("<region id=\"Text\" top=\"70%\" left=\"0\" height=\"30%\" width=\"100%\"/>")
                    body.append("<text src=\"$loc\" region=\"Text\"/>"); hasText = true
                }
            }
        }

        return "<smil><head><layout><root-layout width=\"100%\" height=\"100%\"/>$regions</layout></head>" +
               "<body><par duration=\"5000ms\">$body</par></body></smil>"
    }

    private fun fileNameFor(mimeType: String, index: Int): String = when {
        mimeType.contains("jpeg") || mimeType.contains("jpg") -> "img$index.jpg"
        mimeType.contains("png")                              -> "img$index.png"
        mimeType.contains("gif")                              -> "img$index.gif"
        mimeType.contains("amr")                              -> "audio$index.amr"
        mimeType.contains("3gpp") || mimeType.contains("3gp") -> "video$index.3gp"
        mimeType.contains("mp4")                              -> "video$index.mp4"
        mimeType.contains("mpeg")                             -> "video$index.mpg"
        mimeType.startsWith("audio/")                         -> "audio$index.mp3"
        mimeType.startsWith("text/")                          -> "text$index.txt"
        else                                                  -> "part$index.dat"
    }

    // ── Address helpers ───────────────────────────────────────────────────────

    /**
     * Converts a phone number to E.164 format (+CC...) for MMSC routing.
     *
     * Australian carriers (Telstra/Optus/Vodafone AU) silently drop MMS when the
     * To field uses local format (e.g. 0412345678) instead of E.164 (+61412345678).
     * The HTTP POST succeeds (200 OK from MMSC) but the message is never routed.
     *
     * Handles:
     *   +61412345678   → already E.164, returned as-is
     *   0412345678     → +61412345678  (AU 10-digit local mobile/fixed)
     *   61412345678    → +61412345678  (AU without +)
     *   412345678      → +61412345678  (AU 9-digit bare mobile)
     *   0035312345678  → +35312345678  (IE "00" trunk prefix)
     *   other digits   → +{digits}    (best-effort for unknown country)
     */
    fun normalizeToE164(raw: String): String {
        val s = raw.trim()
        if (s.startsWith("+")) return s                          // already E.164

        val digits = s.replace("[^\\d]".toRegex(), "")
        return when {
            digits.startsWith("00")                              -> "+${digits.drop(2)}"
            digits.length == 10 && digits.startsWith("0")       -> "+61${digits.drop(1)}"  // AU 04xx / 02xx
            digits.length == 11 && digits.startsWith("61")      -> "+$digits"              // AU without +
            digits.length == 9  && !digits.startsWith("0")      -> "+61$digits"            // AU bare 9-digit
            digits.isNotEmpty()                                  -> "+$digits"              // fallback
            else                                                 -> s
        }
    }

    // ── Encoding helpers ──────────────────────────────────────────────────────

    private fun writeByte(out: ByteArrayOutputStream, v: Int) { out.write(v) }

    private fun writeText(out: ByteArrayOutputStream, s: String) {
        out.write(s.toByteArray(Charsets.UTF_8)); out.write(0)
    }

    // OMA-MMS-ENC §8.3.2.2 Long-integer: length-byte (1-8) + big-endian bytes
    private fun writeLongInteger(out: ByteArrayOutputStream, v: Long) {
        val bytes = mutableListOf<Int>()
        var tmp = v
        while (tmp > 0) { bytes.add(0, (tmp and 0xFF).toInt()); tmp = tmp ushr 8 }
        if (bytes.isEmpty()) bytes.add(0)
        out.write(bytes.size)
        bytes.forEach { out.write(it) }
    }

    fun writeUintVar(out: ByteArrayOutputStream, n: Int) {
        if (n == 0) { out.write(0); return }
        var v = n
        val bytes = mutableListOf<Int>()
        bytes.add(v and 0x7F); v = v ushr 7
        while (v > 0) { bytes.add(0, (v and 0x7F) or 0x80); v = v ushr 7 }
        bytes.forEach { out.write(it) }
    }
}
