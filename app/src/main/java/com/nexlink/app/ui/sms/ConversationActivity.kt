package com.nexlink.app.ui.sms

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexlink.app.R
import com.nexlink.app.databinding.ActivityConversationBinding
import com.nexlink.app.db.ReadTracker
import com.nexlink.app.db.SimInfo
import com.nexlink.app.db.SmsHelper
import java.io.File

class ConversationActivity : AppCompatActivity() {

    private lateinit var b: ActivityConversationBinding
    private lateinit var adapter: BubbleAdapter
    private lateinit var address: String

    private var currentLimit = 300
    private var allLoaded = false
    private var sims = listOf<SimInfo>()
    private var selectedSimId = -1

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false

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

        // SIM card detection — runs in background
        Thread {
            val s = SmsHelper.getSims(this)
            runOnUiThread {
                sims = s
                if (s.size > 1) {
                    selectedSimId = s[0].subscriptionId
                    b.tvSimIndicator.text = simLabel(s[0])
                    b.tvSimIndicator.visibility = View.VISIBLE
                }
            }
        }.start()

        // Tap SIM indicator → switch SIM
        b.tvSimIndicator.setOnClickListener { showSimPicker(sendAfter = false) }

        // Short tap send → send with current SIM
        b.btnSend.setOnClickListener { sendMessage() }
        // Long-press send → pick SIM then send immediately
        b.btnSend.setOnLongClickListener {
            if (sims.size > 1) showSimPicker(sendAfter = true) else sendMessage()
            true
        }

        // Hold voice button to record; release to send
        b.btnVoice.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN  -> { startRecording(); true }
                MotionEvent.ACTION_UP    -> { stopAndSendRecording(); true }
                MotionEvent.ACTION_CANCEL -> { cancelRecording(); true }
                else -> false
            }
        }

        loadMessages()
        SmsHelper.markRead(this, address)
        ReadTracker.markRead(this, address)
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)
        loadMessages()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(smsObserver)
        cancelRecording()
    }

    // ── SIM picker ──────────────────────────────────────────────────────────

    private fun showSimPicker(sendAfter: Boolean) {
        val items = sims.map { simLabel(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose SIM")
            .setItems(items) { _, i ->
                selectedSimId = sims[i].subscriptionId
                b.tvSimIndicator.text = simLabel(sims[i])
                b.tvSimIndicator.visibility = View.VISIBLE
                if (sendAfter) sendMessage()
            }
            .show()
    }

    private fun simLabel(sim: SimInfo): String {
        val num = sim.number?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        return "SIM ${sim.slotIndex + 1}: ${sim.displayName}$num"
    }

    // ── Messages ─────────────────────────────────────────────────────────────

    private fun loadMessages(limit: Int = currentLimit) {
        Thread {
            val msgs = SmsHelper.getMessages(this, address, limit)
            runOnUiThread {
                adapter.setData(msgs)
                if (msgs.isNotEmpty()) b.recycler.scrollToPosition(msgs.size - 1)
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

    // ── Voice recording ───────────────────────────────────────────────────────

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }
        try {
            audioFile = File(cacheDir, "voice_${System.currentTimeMillis()}.amr")
            @Suppress("DEPRECATION")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            b.btnVoice.setColorFilter(0xFFFF4444.toInt()) // red tint = recording
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot record: ${e.message}", Toast.LENGTH_SHORT).show()
            cancelRecording()
        }
    }

    private fun stopAndSendRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        isRecording = false
        b.btnVoice.clearColorFilter()

        val file = audioFile ?: return
        if (!file.exists() || file.length() < 500) { file.delete(); return } // discard < 0.5 s

        Thread {
            try {
                SmsHelper.sendVoiceMms(this, address, file, selectedSimId)
                file.delete()
                runOnUiThread { loadMessages() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Voice send failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun cancelRecording() {
        try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        isRecording = false
        b.btnVoice.clearColorFilter()
        audioFile?.delete()
        audioFile = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelRecording()
    }
}
