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
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexlink.app.databinding.ActivityConversationBinding
import com.nexlink.app.db.ReadTracker
import com.nexlink.app.db.SimInfo
import com.nexlink.app.db.SmsHelper
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class ConversationActivity : AppCompatActivity() {

    private lateinit var b: ActivityConversationBinding
    private lateinit var adapter: BubbleAdapter
    private lateinit var address: String

    private var sims = listOf<SimInfo>()
    private var selectedSimId = -1

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false

    // Guard against concurrent loads (e.g. observer fires while first load still running)
    private val loading = AtomicBoolean(false)

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

        // Load-full-history banner — now only shown to load without the MMS query
        // (hide it since we always load everything)
        b.btnLoadEarlier.visibility = View.GONE
        b.dividerLoadEarlier.visibility = View.GONE

        // SIM detection
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

        b.tvSimIndicator.setOnClickListener { showSimPicker(sendAfter = false) }
        b.btnSend.setOnClickListener { sendMessage() }
        b.btnSend.setOnLongClickListener {
            if (sims.size > 1) showSimPicker(sendAfter = true) else sendMessage()
            true
        }

        // Single tap mic: request permission or remind user to hold
        b.btnVoice.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
                Toast.makeText(this, "Microphone permission granted — now hold to record", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Hold to record a voice message", Toast.LENGTH_SHORT).show()
            }
        }
        // Hold mic: record while pressed, send on release
        b.btnVoice.setOnTouchListener { _, event ->
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN   -> { startRecording(); true }
                MotionEvent.ACTION_UP     -> { stopAndSendRecording(); true }
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

    // ── Messages ──────────────────────────────────────────────────────────────

    private fun loadMessages() {
        if (!loading.compareAndSet(false, true)) return // skip if already loading
        Thread {
            val msgs = SmsHelper.getMessages(this, address)
            runOnUiThread {
                loading.set(false)
                adapter.setData(msgs)
                if (msgs.isNotEmpty()) b.recycler.scrollToPosition(adapter.itemCount - 1)
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

    // ── SIM picker ────────────────────────────────────────────────────────────

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

    // ── Voice recording ───────────────────────────────────────────────────────

    private fun startRecording() {
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
            b.btnVoice.setColorFilter(0xFFFF4444.toInt())
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot record: ${e.message}", Toast.LENGTH_SHORT).show()
            cancelRecording()
        }
    }

    private fun stopAndSendRecording() {
        if (!isRecording) return
        try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        isRecording = false
        b.btnVoice.clearColorFilter()

        val file = audioFile ?: return
        audioFile = null
        if (!file.exists() || file.length() < 500) { file.delete(); return }

        Thread {
            try {
                SmsHelper.sendVoiceMms(this, address, file, selectedSimId)
                file.delete()
                runOnUiThread { loadMessages() }
            } catch (e: SecurityException) {
                file.delete()
                runOnUiThread {
                    Toast.makeText(this,
                        "Voice messages require NexLink as the default SMS app. Go to Settings → Apps → Default apps.",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                file.delete()
                runOnUiThread { Toast.makeText(this, "Voice send failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun cancelRecording() {
        try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        isRecording = false
        b.btnVoice.clearColorFilter()
        audioFile?.delete(); audioFile = null
    }

    override fun onDestroy() { super.onDestroy(); cancelRecording() }
}
