package com.nexlink.app.db

import android.content.Context
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.EncodedStringValue
import com.google.android.mms.pdu_alt.PduBody
import com.google.android.mms.pdu_alt.PduComposer
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduPart
import com.google.android.mms.pdu_alt.SendReq

/**
 * Builds a WAP binary MMS M-Send.req PDU using android-smsmms PduComposer —
 * the same library used by QKSMS and Fossify Messages (Apache 2.0).
 */
object MmsPduBuilder {

    data class Part(val contentType: String, val bytes: ByteArray)

    fun generateTxId(): String = "T${System.currentTimeMillis().toString(16)}"

    fun build(ctx: Context, recipients: List<String>, parts: List<Part>,
              txId: String = generateTxId()): ByteArray {

        val sendReq = SendReq()
        sendReq.transactionId = txId.toByteArray(Charsets.UTF_8)
        sendReq.date = System.currentTimeMillis() / 1000L
        // FROM_INSERT_ADDRESS_TOKEN: PduComposer checks charset==129, not the data bytes.
        // Pass ByteArray(0) because EncodedStringValue rejects null.
        sendReq.from = EncodedStringValue(PduHeaders.FROM_INSERT_ADDRESS_TOKEN, ByteArray(0))

        for (r in recipients) {
            val addr = "${normalizeToE164(r)}/TYPE=PLMN"
            android.util.Log.d("NexLink_MMS", "MmsPduBuilder: To='$addr'")
            sendReq.addTo(EncodedStringValue(CharacterSets.UTF_8, addr.toByteArray(Charsets.UTF_8)))
        }

        sendReq.messageClass = PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray(Charsets.UTF_8)
        sendReq.deliveryReport = PduHeaders.VALUE_NO
        sendReq.readReport     = PduHeaders.VALUE_NO
        sendReq.expiry         = 7L * 24 * 60 * 60

        val body = PduBody()

        // SMIL at position 0 (required for multipart/related)
        val smilBytes = buildSmil(parts).toByteArray(Charsets.UTF_8)
        val smilPart  = PduPart()
        smilPart.setContentType("application/smil".toByteArray(Charsets.UTF_8))
        smilPart.setContentId("<smil>".toByteArray(Charsets.UTF_8))
        smilPart.setContentLocation("smil.xml".toByteArray(Charsets.UTF_8))
        smilPart.setData(smilBytes)
        body.addPart(0, smilPart)

        parts.forEachIndexed { i, part ->
            val pduPart = PduPart()
            pduPart.setContentType(part.contentType.toByteArray(Charsets.UTF_8))
            pduPart.setContentId("<part$i>".toByteArray(Charsets.UTF_8))
            pduPart.setContentLocation(fileNameFor(part.contentType, i).toByteArray(Charsets.UTF_8))
            if (part.contentType.startsWith("text/")) pduPart.setCharset(CharacterSets.UTF_8)
            pduPart.setData(part.bytes)
            body.addPart(pduPart)
        }

        sendReq.body = body

        val pduBytes = PduComposer(ctx, sendReq).make()
            ?: throw IllegalStateException("PduComposer.make() returned null")

        val end = minOf(48, pduBytes.size)
        val hex = pduBytes.copyOfRange(0, end).joinToString(" ") { "%02X".format(it) }
        android.util.Log.d("NexLink_MMS", "MmsPduBuilder: ${pduBytes.size}B txId=$txId hex=$hex")
        return pduBytes
    }

    // ── SMIL builder ─────────────────────────────────────────────────────────

    private fun buildSmil(parts: List<Part>): String {
        val regions = StringBuilder()
        val body    = StringBuilder()
        var hasImage = false; var hasVideo = false; var hasText = false

        parts.forEachIndexed { i, part ->
            val loc = fileNameFor(part.contentType, i)
            when {
                part.contentType.startsWith("image/") && !hasImage -> {
                    regions.append("<region id=\"Image\" top=\"0\" left=\"0\" height=\"100%\" width=\"100%\" fit=\"meet\"/>")
                    body.append("<img src=\"$loc\" region=\"Image\"/>"); hasImage = true
                }
                part.contentType.startsWith("video/") && !hasVideo -> {
                    regions.append("<region id=\"Video\" top=\"0\" left=\"0\" height=\"100%\" width=\"100%\" fit=\"meet\"/>")
                    body.append("<video src=\"$loc\" region=\"Video\"/>"); hasVideo = true
                }
                part.contentType.startsWith("audio/") ->
                    body.append("<audio src=\"$loc\"/>")
                part.contentType.startsWith("text/plain") && !hasText -> {
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
        mimeType.startsWith("audio/")                         -> "audio$index.mp3"
        mimeType.startsWith("text/")                          -> "text$index.txt"
        else                                                  -> "part$index.dat"
    }

    // ── Address helpers ───────────────────────────────────────────────────────

    fun normalizeToE164(raw: String): String {
        val s = raw.trim()
        if (s.startsWith("+")) return s
        val digits = s.replace("[^\\d]".toRegex(), "")
        return when {
            digits.startsWith("00")                            -> "+${digits.drop(2)}"
            digits.length == 10 && digits.startsWith("0")     -> "+61${digits.drop(1)}"
            digits.length == 11 && digits.startsWith("61")    -> "+$digits"
            digits.length == 9  && !digits.startsWith("0")    -> "+61$digits"
            digits.isNotEmpty()                                -> "+$digits"
            else                                               -> s
        }
    }
}
