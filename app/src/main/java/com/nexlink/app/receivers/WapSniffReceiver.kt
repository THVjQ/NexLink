package com.nexlink.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Diagnostic-only receiver: logs every WAP push so we can verify the broadcast reaches NexLink. */
class WapSniffReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val data = intent.getByteArrayExtra("data")
        Log.d("NexLink_MMS", "WapSniff: action=${intent.action} type=${intent.type} dataLen=${data?.size} extras=${intent.extras?.keySet()}")
        // Log content-location URL (field 0x83 in M-Notification.ind)
        if (data != null) {
            val loc = extractContentLocation(data)
            Log.d("NexLink_MMS", "WapSniff: content-location=$loc")
        }
    }

    private fun extractContentLocation(pdu: ByteArray): String? {
        return try {
            var i = 0
            while (i < pdu.size - 1) {
                val fc = pdu[i++].toInt() and 0xFF
                if (fc == 0x83) {
                    val start = i
                    while (i < pdu.size && pdu[i] != 0.toByte()) i++
                    return String(pdu, start, i - start, Charsets.ISO_8859_1).trim().ifBlank { null }
                }
                // skip value bytes (short-int = 1, text = scan to null, length-val = 1+len)
                if (i >= pdu.size) break
                val v = pdu[i].toInt() and 0xFF
                i += when {
                    v and 0x80 != 0 -> 1
                    v in 1..30      -> 1 + v
                    else            -> { var e = i; while (e < pdu.size && pdu[e] != 0.toByte()) e++; e - i + 1 }
                }
            }
            null
        } catch (_: Exception) { null }
    }
}
