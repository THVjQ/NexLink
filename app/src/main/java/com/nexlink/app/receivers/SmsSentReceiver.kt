package com.nexlink.app.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val msgId = intent.getLongExtra("message_id", -1L)
        if (msgId <= 0) return
        // SMS left the device — move from PENDING(32) to NONE(-1, single tick).
        // If send failed, mark FAILED(64). SmsDeliveredReceiver upgrades to COMPLETE(0) if carrier
        // sends a delivery report.
        val status = if (resultCode == Activity.RESULT_OK) Telephony.Sms.STATUS_NONE
                     else Telephony.Sms.STATUS_FAILED
        val values = ContentValues().apply { put(Telephony.Sms.STATUS, status) }
        try {
            ctx.contentResolver.update(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, msgId),
                values, null, null
            )
        } catch (_: Exception) {}
    }
}
