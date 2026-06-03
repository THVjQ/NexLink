package com.nexlink.app.ui.sms

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexlink.app.R
import com.nexlink.app.databinding.ActivityConversationBinding
import com.nexlink.app.db.SmsHelper

class ConversationActivity : AppCompatActivity() {

    private lateinit var b: ActivityConversationBinding
    private lateinit var adapter: BubbleAdapter
    private lateinit var address: String

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { loadMessages() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(b.root)

        address = intent.getStringExtra("address") ?: ""
        val name = intent.getStringExtra("contact_name") ?: address

        setSupportActionBar(b.toolbar)
        supportActionBar?.title = name
        supportActionBar?.subtitle = address
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        adapter = BubbleAdapter()
        b.recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        b.recycler.adapter = adapter

        loadMessages()
        SmsHelper.markRead(this, address)

        b.btnSend.setOnClickListener { sendMessage() }
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)
        loadMessages()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(smsObserver)
    }

    private fun loadMessages() {
        Thread {
            val msgs = SmsHelper.getMessages(this, address)
            runOnUiThread {
                adapter.setData(msgs)
                if (msgs.isNotEmpty()) b.recycler.scrollToPosition(msgs.size - 1)
            }
        }.start()
    }

    private fun sendMessage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SMS permission required", Toast.LENGTH_SHORT).show()
            return
        }
        val text = b.etInput.text.toString().trim()
        if (text.isEmpty() || address.isEmpty()) return
        b.etInput.text?.clear()
        Thread {
            try {
                SmsHelper.sendSms(this, address, text)
                runOnUiThread { loadMessages() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
