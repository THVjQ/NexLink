package com.nexlink.app.ui.sms

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexlink.app.R
import com.nexlink.app.databinding.ActivityConversationBinding
import com.nexlink.app.db.NotificationPrefs
import com.nexlink.app.db.ReadTracker
import com.nexlink.app.db.SentMmsStore
import com.nexlink.app.db.SimInfo
import com.nexlink.app.db.SmsHelper
import com.nexlink.app.db.SmsMessage
import com.nexlink.app.receivers.SmsNotifier
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class ConversationActivity : AppCompatActivity() {

    private lateinit var b: ActivityConversationBinding
    private lateinit var adapter: BubbleAdapter
    private lateinit var address: String

    private var threadId     = 0L
    private var participants = listOf<String>()
    private val isGroup get() = participants.size > 1

    private var sims        = listOf<SimInfo>()
    private var selectedSimId = -1

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var cameraUri: android.net.Uri? = null

    private val loading = AtomicBoolean(false)
    private val debounceHandler = Handler(Looper.getMainLooper())
    private val debounceLoad = Runnable { loadMessages() }

    // Debounced: rapid-fire DB notifications (e.g. during a batch insert) collapse to one reload
    private fun scheduleLoad() {
        debounceHandler.removeCallbacks(debounceLoad)
        debounceHandler.postDelayed(debounceLoad, 150)
    }

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { scheduleLoad() }
    }
    private val mmsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { scheduleLoad() }
    }

    // ── Launchers ─────────────────────────────────────────────────────────────

    private val reqCameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() else Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show() }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraUri?.let { sendAttachment(it, "image/jpeg") } }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { sendAttachment(it, contentResolver.getType(it) ?: "image/*") } }

    private val videoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { sendAttachment(it, contentResolver.getType(it) ?: "video/*") } }

    private val audioLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { sendAttachment(it, contentResolver.getType(it) ?: "audio/*") } }

    private val fileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { sendAttachment(it, contentResolver.getType(it) ?: "application/octet-stream") } }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(b.root)

        // On Android 15+, edge-to-edge is enforced so adjustResize no longer works.
        // Apply bottom padding equal to the keyboard (IME) height so the compose bar
        // always stays visible above the keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, maxOf(imeBottom, navBottom))
            insets
        }

        address      = intent.getStringExtra("address") ?: ""
        threadId     = intent.getLongExtra("thread_id", 0L)
        participants = intent.getStringArrayListExtra("participants") ?: listOf(address)
        val name     = intent.getStringExtra("contact_name") ?: address

        setSupportActionBar(b.toolbar)
        supportActionBar?.title = name
        val rcsOn = NotificationPrefs.isRcsEnabled(this)
        supportActionBar?.subtitle = when {
            isGroup -> participants.size.toString() + " participants"
            rcsOn   -> "RCS · $address"
            else    -> address
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        adapter = BubbleAdapter(
            isGroup   = isGroup,
            onForward = { msg -> showForwardPicker(msg) },
            onDelete  = { id, isMms ->
                Thread {
                    SmsHelper.deleteMessage(this, id, isMms)
                    loadMessages()
                }.start()
            }
        )
        b.recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        b.recycler.adapter = adapter
        b.btnLoadEarlier.visibility = View.GONE
        b.dividerLoadEarlier.visibility = View.GONE

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
        b.btnAttach.setOnClickListener { showAttachPicker() }

        b.btnVoice.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
                Toast.makeText(this, "Microphone permission granted — hold to record", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Hold to record a voice message", Toast.LENGTH_SHORT).show()
            }
        }
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

        // Pre-fill forwarded message if any
        intent.getStringExtra("forward_text")?.let { b.etInput.setText(it) }

        loadMessages()
        if (threadId > 0) {
            SmsHelper.markReadByThread(this, threadId)
        } else {
            SmsHelper.markRead(this, address)
        }
        ReadTracker.markRead(this, address)
        SmsNotifier.clearPending(this, address)
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)
        contentResolver.registerContentObserver(android.net.Uri.parse("content://mms"), true, mmsObserver)
        loadMessages()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(smsObserver)
        contentResolver.unregisterContentObserver(mmsObserver)
        cancelRecording()
    }

    override fun onDestroy() { super.onDestroy(); cancelRecording() }

    // ── Options menu (delete conversation) ───────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_conversation, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_call) {
            if (address.isBlank()) return true
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
                startActivity(android.content.Intent(android.content.Intent.ACTION_CALL,
                    Uri.parse("tel:$address")))
            } else {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.CALL_PHONE), 200)
            }
            return true
        }
        if (item.itemId == R.id.action_delete_conversation) {
            AlertDialog.Builder(this)
                .setTitle("Delete conversation")
                .setMessage("This will delete all messages in this conversation.")
                .setPositiveButton("Delete") { _, _ ->
                    Thread {
                        if (threadId > 0) SmsHelper.deleteThread(this, threadId)
                        runOnUiThread { finish() }
                    }.start()
                }
                .setNegativeButton("Cancel", null)
                .show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // ── Default SMS app check ─────────────────────────────────────────────────

    private fun requireDefaultSmsApp(): Boolean {
        if (SmsHelper.isDefaultSmsApp(this)) return true
        AlertDialog.Builder(this)
            .setTitle("Default SMS app required")
            .setMessage("To send media, voice messages, or group texts, NexLink must be set as your default SMS app.")
            .setPositiveButton("Open settings") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val rm = getSystemService(android.app.role.RoleManager::class.java)
                    startActivity(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS))
                } else {
                    startActivity(android.content.Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        return false
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    private fun loadMessages() {
        if (!loading.compareAndSet(false, true)) return
        Thread {
            // Auto-resolve thread ID for new group conversations
            if (threadId == 0L && isGroup) {
                threadId = runCatching {
                    android.provider.Telephony.Threads.getOrCreateThreadId(this, participants.toSet())
                }.getOrDefault(0L)
            }
            val msgs = if (threadId > 0) SmsHelper.getMessagesByThread(this, threadId, address)
                       else SmsHelper.getMessages(this, address)
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
        if (isGroup && !requireDefaultSmsApp()) return
        b.etInput.text?.clear()
        Thread {
            try {
                if (isGroup) {
                    val resolvedTid = SmsHelper.sendGroupText(this, threadId, participants, text, selectedSimId)
                    if (threadId == 0L && resolvedTid > 0L) threadId = resolvedTid
                } else {
                    SmsHelper.sendSms(this, address, text, selectedSimId)
                }
                runOnUiThread { loadMessages() }
            } catch (e: SecurityException) {
                runOnUiThread { requireDefaultSmsApp() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    // ── Attachments ───────────────────────────────────────────────────────────

    private fun showAttachPicker() {
        val options = arrayOf("📷 Camera", "🖼 Photo / Gallery", "🎬 Video", "🎵 Audio file", "📎 File")
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) launchCamera()
                        else reqCameraPermLauncher.launch(Manifest.permission.CAMERA)
                    }
                    1 -> galleryLauncher.launch("image/*")
                    2 -> videoLauncher.launch("video/*")
                    3 -> audioLauncher.launch("audio/*")
                    4 -> fileLauncher.launch("*/*")
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
        if (!requireDefaultSmsApp()) return

        // Add an optimistic outgoing bubble immediately — visible before content://mms updates
        val optimisticMsg = SmsMessage(
            id          = -(System.currentTimeMillis()),
            threadId    = threadId,
            address     = address,
            body        = when {
                mimeType.startsWith("audio/") -> "🎤 Voice message"
                mimeType.startsWith("video/") -> "🎬 Video"
                else                          -> ""
            },
            timestamp   = System.currentTimeMillis(),
            isIncoming  = false,
            isMms       = true,
            isVoice     = mimeType.startsWith("audio/"),
            mediaUri    = uri.toString(),
            mimeType    = if (mimeType.startsWith("image/")) "image/jpeg" else mimeType
        )
        adapter.addOptimistic(optimisticMsg)
        b.recycler.scrollToPosition(adapter.itemCount - 1)

        Thread {
            try {
                SentMmsStore.save(this, optimisticMsg)
                SmsHelper.sendMediaMms(this, address, uri, mimeType, selectedSimId,
                    extraRecipients = if (isGroup) participants.drop(1) else emptyList())
                runOnUiThread { loadMessages() }
            } catch (e: SecurityException) {
                runOnUiThread { requireDefaultSmsApp() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    // ── Forward ───────────────────────────────────────────────────────────────

    private fun showForwardPicker(msg: SmsMessage) {
        Thread {
            val convs = SmsHelper.getConversations(this, 20)
            runOnUiThread {
                val names = convs.map { it.contactName.ifBlank { it.address } }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Forward to…")
                    .setItems(names) { _, i ->
                        val c = convs[i]
                        android.content.Intent(this, ConversationActivity::class.java).also { intent ->
                            intent.putExtra("address", c.address)
                            intent.putExtra("contact_name", c.contactName)
                            intent.putExtra("thread_id", c.threadId)
                            intent.putStringArrayListExtra("participants", ArrayList(c.participants))
                            intent.putExtra("forward_text", msg.body)
                            startActivity(intent)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
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
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
                            else @Suppress("DEPRECATION") MediaRecorder()
            mediaRecorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile!!.absolutePath)
                prepare(); start()
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
        mediaRecorder = null; isRecording = false
        b.btnVoice.clearColorFilter()
        val file = audioFile ?: return
        audioFile = null
        if (!file.exists() || file.length() < 500) { file.delete(); return }
        if (!requireDefaultSmsApp()) { file.delete(); return }

        // Optimistic bubble — show immediately; keep file so play button works
        val audioUri = android.net.Uri.fromFile(file)
        val optimisticVoice = com.nexlink.app.db.SmsMessage(
            id         = -System.currentTimeMillis(),
            address    = address,
            body       = "🎤 Voice message",
            timestamp  = System.currentTimeMillis(),
            isIncoming = false,
            isMms      = true,
            isVoice    = true,
            mediaUri   = audioUri.toString(),
            mimeType   = "audio/amr"
        )
        adapter.addOptimistic(optimisticVoice)
        b.recycler.scrollToPosition(adapter.itemCount - 1)

        Thread {
            try {
                SentMmsStore.save(this, optimisticVoice)
                SmsHelper.sendVoiceMms(this, address, file, selectedSimId)
                // Keep file in cache so the optimistic play button still works;
                // OS will evict it when cache needs space
                runOnUiThread { loadMessages() }
            } catch (e: SecurityException) {
                file.delete()
                runOnUiThread { Toast.makeText(this,
                    "Voice messages require NexLink as the default SMS app.",
                    Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                file.delete()
                runOnUiThread { Toast.makeText(this, "Voice send failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun cancelRecording() {
        try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null; isRecording = false
        b.btnVoice.clearColorFilter()
        audioFile?.delete(); audioFile = null
    }
}
