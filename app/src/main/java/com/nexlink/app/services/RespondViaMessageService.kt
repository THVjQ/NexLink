package com.nexlink.app.services

import android.app.IntentService
import android.content.Intent
import com.nexlink.app.db.SmsHelper

@Suppress("DEPRECATION")
class RespondViaMessageService : IntentService("RespondViaMessageService") {
    override fun onHandleIntent(intent: Intent?) {
        val body    = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val address = intent.data?.schemeSpecificPart?.trimStart('/') ?: return
        if (body.isNotBlank() && address.isNotBlank()) {
            SmsHelper.sendSms(this, address, body)
        }
    }
}
