package com.nexlink.app.ui.sms

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexlink.app.R
import com.nexlink.app.databinding.ActivityConversationBinding
import com.nexlink.app.db.ReadTracker
import com.nexlink.app.db.SimInfo
import com.nexlink.app.db.SmsHelper

class ConversationActivity : AppCompatActivity() {

    private lateinit var b: ActivityConversationBinding
    private lateinit var adapter: BubbleAdapter
    private lateinit var address: String

    private var currentLimit = 300
    private var allLoaded = false

    private var sims = listOf<SimInfo>()
    private var selectedSimId = -1

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

        b.btnLoadEarlier.setOnClickListener {
            allLoaded = true
            loadMessages(limit = 0)
            b.btnLoadEarlier.isVisible = false
            b.dividerLoadEarlier.isVisible = false
        }

        // SIM card detection
        Thread {
            val s = SmsHelper.getSims(this)
            runOnUiThread {
                sims = s
                if (s.size > 1) {
                    selectedSimId = s[0].subscriptionId
                    b.tvSimIndicator.text = "SIM: ${s[0].displayName}"
                    b.tvSimIndicator.visibility = View.VISIBLE
                }
            }
        }.start()

        b.tvSimIndicator.setOnClickListener {
            val names = sims.map { it.displayName }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Choose SIM")
                .setItems(names) { _, i ->
                    selectedSimId = sims[i].subscriptionId
                    b.tvSimIndicator.text = "SIM: ${sims[i].displayName}"
                }
                .show()
        }

        loadMessages()
        SmsHelper.markRead(this, address)
        ReadTracker.markRead(this, address)

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

    private fun loadMessages(limit: Int = currentLimit) {
        Thread {
            val msgs = SmsHelper.getMessages(this, address, limit)
            runOnUiThread {
                adapter.setData(msgs)
                if (msgs.isNotEmpty()) b.recycler.scrollToPosition(msgs.size - 1)
                // Show "load earlier" banner if we hit the limit and haven't loaded all yet
                val hitLimit = !allLoaded && limit > 0 && msgs.size >= limit
                b.btnLoadEarlier.isVisible = hitLimit
                b.dividerLoadEarlier.isVisible = hitLimit
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
                SmsHelper.sendSms(this, address, text, selectedSimId)
                runOnUiThread { loadMessages() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
