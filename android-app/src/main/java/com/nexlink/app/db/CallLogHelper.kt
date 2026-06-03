package com.nexlink.app.db

import android.content.Context
import android.provider.CallLog

data class CallEntry(
    val name: String,
    val number: String,
    val type: Int,
    val timestamp: Long,
    val duration: Long
)

object CallLogHelper {
    fun getCalls(ctx: Context): List<CallEntry> {
        val list = mutableListOf<CallEntry>()
        val proj = arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI, proj, null, null, "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val raw = c.getString(1) ?: continue
                    list += CallEntry(
                        name      = c.getString(0)?.takeIf { it.isNotBlank() }
                                    ?: SmsHelper.getContactName(ctx, raw),
                        number    = raw,
                        type      = c.getInt(2),
                        timestamp = c.getLong(3),
                        duration  = c.getLong(4)
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }
}
