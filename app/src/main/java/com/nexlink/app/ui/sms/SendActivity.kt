package com.nexlink.app.ui.sms

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nexlink.app.db.SmsHelper

class SendActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri  = intent.data
        val address = uri?.schemeSpecificPart?.trimStart('/') ?: ""
        startActivity(Intent(this, ConversationActivity::class.java).apply {
            putExtra("address", address)
            putExtra("contact_name", SmsHelper.getContactName(this@SendActivity, address))
        })
        finish()
    }
}
