package com.nexlink.app.db

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import com.nexlink.shared.SmsMessage
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// SyncTech XML format — compatible with SMS Backup & Restore and most Android SMS apps.
object SmsBackupHelper {

    // ── Export ────────────────────────────────────────────────────────────────

    fun exportToXml(ctx: Context, out: OutputStream): Int {
        val msgs = readAllSms(ctx)
        val sdf = SimpleDateFormat("MMM d, yyyy h:mm:ss a", Locale.US)
        val writer = out.bufferedWriter(Charsets.UTF_8)

        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>\n")
        writer.write("<!--Backup by NexLink ${appVersionName(ctx)}-->\n")
        writer.write("<smses count=\"${msgs.size}\" backup_date=\"${System.currentTimeMillis()}\" type=\"full\">\n")

        for (m in msgs) {
            // Decrypt if encrypted and we have the session key
            val body = decryptIfNeeded(ctx, m.address, m.body)
            val contact = SmsHelper.getContactName(ctx, m.address)
            val type = if (m.isIncoming) "1" else "2"   // 1=received 2=sent (Telephony convention)
            val readable = sdf.format(Date(m.timestamp))

            // Encode XML special characters in body
            val safeBody = body
                .replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;").replace("'", "&apos;")
            val safeName = contact
                .replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;")

            writer.write(
                "  <sms protocol=\"0\" address=\"${m.address}\" date=\"${m.timestamp}\" " +
                "type=\"$type\" subject=\"null\" body=\"$safeBody\" " +
                "toa=\"null\" sc_toa=\"null\" service_center=\"null\" " +
                "read=\"1\" status=\"-1\" locked=\"0\" date_sent=\"0\" sub_id=\"-1\" " +
                "readable_date=\"$readable\" contact_name=\"$safeName\" />\n"
            )
        }

        writer.write("</smses>")
        writer.flush()
        return msgs.size
    }

    private fun decryptIfNeeded(ctx: Context, address: String, body: String): String {
        if (!CryptoStore.isEncrypted(body)) return body
        val key = CryptoStore.getSessionKey(ctx, address) ?: return body
        return CryptoStore.decrypt(body, key) ?: body
    }

    private fun readAllSms(ctx: Context): List<SmsMessage> {
        val list = mutableListOf<SmsMessage>()
        val proj = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
            Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.THREAD_ID
        )
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI, proj, null, null, "${Telephony.Sms.DATE} ASC"
            )?.use { c ->
                val iId   = c.getColumnIndex(Telephony.Sms._ID)
                val iAddr = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val iBody = c.getColumnIndex(Telephony.Sms.BODY)
                val iDate = c.getColumnIndex(Telephony.Sms.DATE)
                val iType = c.getColumnIndex(Telephony.Sms.TYPE)
                val iTid  = c.getColumnIndex(Telephony.Sms.THREAD_ID)
                while (c.moveToNext()) {
                    val addr = c.getString(iAddr) ?: continue
                    val type = c.getInt(iType)
                    list += SmsMessage(
                        id         = c.getLong(iId),
                        threadId   = c.getLong(iTid),
                        address    = addr,
                        body       = c.getString(iBody) ?: "",
                        timestamp  = c.getLong(iDate),
                        isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    // ── Import ────────────────────────────────────────────────────────────────

    data class ImportResult(val imported: Int, val skipped: Int)

    fun importFromXml(ctx: Context, inp: InputStream): ImportResult {
        if (!SmsHelper.isDefaultSmsApp(ctx)) return ImportResult(0, 0)

        var imported = 0; var skipped = 0
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser  = factory.newPullParser()
            parser.setInput(inp, "UTF-8")

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "sms") {
                    val address = parser.getAttributeValue(null, "address") ?: ""
                    val body    = parser.getAttributeValue(null, "body")    ?: ""
                    val date    = parser.getAttributeValue(null, "date")?.toLongOrNull() ?: 0L
                    val type    = parser.getAttributeValue(null, "type")?.toIntOrNull() ?: 1
                    val read    = parser.getAttributeValue(null, "read")?.toIntOrNull() ?: 1

                    if (address.isNotBlank() && body.isNotBlank() && date > 0) {
                        if (writeSms(ctx, address, body, date, type, read)) imported++ else skipped++
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {}
        return ImportResult(imported, skipped)
    }

    private fun writeSms(ctx: Context, address: String, body: String, date: Long,
                         type: Int, read: Int): Boolean {
        // type 1 = received, type 2 = sent (SyncTech format)
        val targetUri = if (type == 2) Telephony.Sms.Sent.CONTENT_URI else Telephony.Sms.Inbox.CONTENT_URI
        return try {
            ctx.contentResolver.insert(targetUri, ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, date)
                put(Telephony.Sms.DATE_SENT, date)
                put(Telephony.Sms.READ, read)
                put(Telephony.Sms.TYPE,
                    if (type == 2) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_INBOX)
            }) != null
        } catch (_: Exception) { false }
    }

    private fun appVersionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }
}
