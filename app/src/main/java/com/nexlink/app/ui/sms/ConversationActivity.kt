package com.nexlink.app.ui.sms

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexlink.app.databinding.ActivityConversationBinding
import com.nexlink.app.db.NotificationPrefs
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

    private var cameraUri: android.net.Uri? = null

    private val loading = AtomicBoolean(false)

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { loadMessages() }
    }

    // ── Activity result launchers ─────────────────────────────────────────────

    private val reqCameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show()
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraUri?.let { sendAttachment(it, "image/jpeg") }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { sendAttachment(it, contentResolver.getType(it) ?: "image/*") }
    }

    private val videoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { sendAttachment(it, contentResolver.getType(it) ?: "video/*") }
    }

    private val fileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { sendAttachment(it, contentResolver.getType(it) ?: "application/octet-stream") }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(b.root)

        address = intent.getStringExtra("address") ?: ""
        val name = intent.getStringExtra("contact_name") ?: address

        setSupportActionBar(b.toolbar)
        supportActionBar?.title = name
        val rcsOn = NotificationPrefs.isRcsEnabled(this)
        supportActionBar?.subtitle = if (rcsOn) "RCS · $address" else address
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        adapter = BubbleAdapter()
        b.recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        b.recycler.adapter = adapter

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

        // Attach button
        b.btnAttach.setOnClickListener { showAttachPicker() }

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
        if (!loading.compareAndSet(false, true)) return
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

    // ── Attachments ───────────────────────────────────────────────────────────

    private fun showAttachPicker() {
        val options = arrayOf("📷 Camera", "🖼 Photo / Gallery", "🎬 Video", "📎 File")
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {
                            launchCamera()
                        } else {
                            reqCameraPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    1 -> galleryLauncher.launch("image/*")
                    2 -> videoLauncher.launch("video/*")
                    3 -> fileLauncher.launch("*/*")
                }
            }
            .show()
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "attachments").also { it.mkdirs() }
        val tmp = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", tmp)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    private fun sendAttachment(uri: android.net.Uri, mimeType: String) {
        if (address.isEmpty()) return
        Thread {
            try {
                SmsHelper.sendMediaMms(this, address, uri, mimeType, selectedSimId)
                runOnUiThread { loadMessages() }
            } catch (e: SecurityException) {
                runOnUiThread {
                    Toast.makeText(this,
                        "Media messages require NexLink as the default SMS app. Go to Settings → Apps → Default apps.",
                        Toast.LENGTH_LONG).show()
                }
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
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            b.btnVoice.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
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
