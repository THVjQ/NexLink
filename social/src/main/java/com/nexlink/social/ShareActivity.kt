package com.nexlink.social

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nexlink.social.core.session.SessionState
import com.nexlink.social.core.session.MessageBody
import com.nexlink.social.core.session.RoomId
import com.nexlink.social.core.session.RoomSummary
import com.nexlink.social.ui.chrome.Chrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * §14.15 — the Android share sheet.
 *
 * Until this existed, NexLink Social did not appear when you shared a photo or a
 * link from another app: the share sheet lists only apps that *declare* they
 * accept ACTION_SEND, and nothing in the manifest did. There was no bug to find
 * in the app because the app was never asked.
 *
 * THE ONE NON-OBVIOUS PART — the URI grant is borrowed, and it expires.
 *
 * A content:// URI arriving in a share Intent carries a read grant scoped to
 * THIS activity's task. Passing that URI to the SDK and finishing would be a
 * race: the upload reads the file on a background thread, and by then the grant
 * can be gone, which fails as a generic "could not read" long after the screen
 * that had permission has closed. So every stream is **copied into our own cache
 * while this activity is alive and the grant is valid**, and what gets sent is
 * the copy. [ImagePrep] still downscales and strips EXIF on top of that (§14.5.1).
 */
class ShareActivity : AppCompatActivity() {

    private lateinit var chrome: Chrome
    private lateinit var page: Chrome.Page

    private var sharedText: String? = null
    private var staged: List<Staged> = emptyList()
    private var rooms: List<RoomSummary> = emptyList()
    private var state: SessionState = SessionState.SignedOut
    private var filter: String = ""
    private var sending = false
    private var stagingFailed = 0

    private data class Staged(val file: File, val isImage: Boolean, val label: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chrome = Chrome(this)
        page = chrome.page("Share to…", onBack = { finish() })
        setContentView(page.root)

        sharedText = intent.extractText()
        val uris = intent.extractStreams()

        val mgr = SessionProvider.manager(this)
        lifecycleScope.launch { mgr.state.collectLatest { state = it; render(); observeRooms() } }
        lifecycleScope.launch { if (mgr.hasStoredSession) mgr.restore() }

        // Copy first, ask questions later. See the class comment.
        if (uris.isEmpty()) { render() } else {
            render()
            lifecycleScope.launch {
                staged = stage(uris)
                stagingFailed = uris.size - staged.size
                render()
            }
        }
    }

    // ── incoming intent ─────────────────────────────────────────────────────

    private fun Intent.extractText(): String? = ShareIntake.messageFor(
        getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
        getStringExtra(Intent.EXTRA_SUBJECT),
    )

    private fun Intent.extractStreams(): List<Uri> = when (action) {
        Intent.ACTION_SEND -> listOfNotNull(parcelable<Uri>(Intent.EXTRA_STREAM))
        Intent.ACTION_SEND_MULTIPLE -> parcelableList<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else -> emptyList()
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> Intent.parcelable(key: String): T? =
        if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java)
        else getParcelableExtra(key)

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> Intent.parcelableList(key: String): List<T>? =
        if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(key, T::class.java)
        else getParcelableArrayListExtra(key)

    /** Copy each shared stream into cache. A stream we cannot read is dropped and counted. */
    private suspend fun stage(uris: List<Uri>): List<Staged> = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "share").apply { mkdirs() }
        // Anything left from a previous share is dead weight; this is the only
        // place that knows the directory's purpose, so it is the place to sweep.
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        uris.mapNotNull { uri ->
            runCatching {
                val type = contentResolver.getType(uri).orEmpty()
                val name = displayName(uri) ?: "shared"
                val out = File(dir, "${System.nanoTime()}-${name.take(64).replace('/', '_')}")
                contentResolver.openInputStream(uri)!!.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                Staged(out, type.startsWith("image/"), name)
            }.getOrNull()
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull()

    // ── rooms ───────────────────────────────────────────────────────────────

    private var watching = false
    private fun observeRooms() {
        if (watching) return
        val s = SessionProvider.manager(this).current() ?: return
        watching = true
        lifecycleScope.launch { s.rooms().collectLatest { rooms = it; render() } }
    }

    // ── ui ──────────────────────────────────────────────────────────────────

    private fun render() {
        val c = page.content
        c.removeAllViews()

        if (sending) {
            c.addView(chrome.emptyState("Sending…", "This closes on its own."))
            return
        }

        c.addView(summaryCard())

        when (state) {
            is SessionState.SignedIn -> renderPicker(c)
            is SessionState.Restoring ->
                c.addView(chrome.emptyState("Just a moment", "Opening your account."))
            else -> {
                c.addView(chrome.emptyState(
                    "Sign in first",
                    "You need to be signed in to NexLink Social to share into a conversation."))
                val b = android.widget.Button(this).apply {
                    text = "Open NexLink Social"
                    setOnClickListener {
                        startActivity(Intent(this@ShareActivity, HomeActivity::class.java))
                        finish()
                    }
                }
                c.addView(b); chrome.styleButton(b, primary = true)
            }
        }
    }

    /** What is about to be sent, so nobody shares the wrong photo into the wrong chat. */
    private fun summaryCard(): View {
        val card = chrome.card(marginTopDp = 4)
        val t = TextView(this).apply {
            setPadding(chromeDp(14), chromeDp(12), chromeDp(14), chromeDp(12))
            text = buildString {
                val parts = mutableListOf<String>()
                staged.count { it.isImage }.let { if (it > 0) parts += if (it == 1) "1 photo" else "$it photos" }
                staged.count { !it.isImage }.let { if (it > 0) parts += if (it == 1) "1 file" else "$it files" }
                sharedText?.let { parts += "a message" }
                if (parts.isEmpty()) append("Nothing to share")
                else append("Sharing ").append(parts.joinToString(" and "))
                if (stagingFailed > 0) {
                    append("\n")
                    append(if (stagingFailed == 1) "1 item could not be read and will be skipped."
                           else "$stagingFailed items could not be read and will be skipped.")
                }
            }
        }
        card.addView(t)
        sharedText?.let { card.addView(chrome.note(it.take(240))) }
        return card
    }

    private fun renderPicker(c: LinearLayout) {
        val joined = rooms.filterNot { it.isInvite }
        if (joined.isEmpty()) {
            c.addView(chrome.emptyState("No conversations yet",
                "Start a conversation in NexLink Social, then share into it."))
            return
        }

        if (joined.size > 6) {
            val search = EditText(this).apply {
                hint = "Search conversations"
                setSingleLine()
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) {
                        filter = s?.toString().orEmpty(); renderList()
                    }
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                })
            }
            c.addView(search); chrome.styleField(search)
        }
        c.addView(chrome.sectionHeader("Choose a conversation"))
        listHolder = chrome.card()
        c.addView(listHolder)
        renderList()
    }

    private var listHolder: LinearLayout? = null

    private fun renderList() {
        val holder = listHolder ?: return
        holder.removeAllViews()
        val q = filter.trim().lowercase()
        val shown = rooms.filterNot { it.isInvite }
            .filter { q.isEmpty() || it.title.lowercase().contains(q) }
            .sortedByDescending { it.lastMessageAt }
        if (shown.isEmpty()) {
            holder.addView(chrome.note("No conversation matches “$filter”.")); return
        }
        shown.forEachIndexed { i, r ->
            if (i > 0) holder.addView(chrome.rowDivider())
            holder.addView(roomRow(r))
        }
    }

    private fun roomRow(r: RoomSummary): View {
        val row = chrome.row(onClick = { confirmAndSend(r) })
        row.contentDescription = "Share to ${r.title}"
        row.addView(chrome.avatar(r.title))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(TextView(this).apply { text = r.title; textSize = 16f })
        r.lastMessagePreview?.takeIf { it.isNotBlank() }?.let {
            col.addView(TextView(this).apply { text = it; textSize = 13f; setSingleLine(); alpha = 0.7f })
        }
        row.addView(col)
        return row
    }

    // ── sending ─────────────────────────────────────────────────────────────

    private fun confirmAndSend(r: RoomSummary) {
        if (sending) return
        sending = true; render()
        val s = SessionProvider.manager(this).current()
        if (s == null) { sending = false; render(); return }
        val rid = RoomId(r.id.value)

        lifecycleScope.launch {
            var failures = 0
            for (item in staged) {
                val uri = Uri.fromFile(item.file).toString()
                val res = if (item.isImage) s.sendImage(rid, uri) else s.sendFile(rid, uri)
                if (res.isFailure) failures++
                runCatching { item.file.delete() }
            }
            sharedText?.let {
                if (s.send(rid, MessageBody.Text(it, replyTo = null)).isFailure) failures++
            }

            val ok = failures == 0
            Toast.makeText(
                this@ShareActivity,
                if (ok) "Sent to ${r.title}"
                else "Some items could not be sent to ${r.title}",
                Toast.LENGTH_SHORT,
            ).show()
            // On success go to the conversation, so the send is visible rather
            // than merely claimed. On failure stay out of the way.
            if (ok) startActivity(ConversationActivity.intent(this@ShareActivity, r.id.value, r.title))
            finish()
        }
    }

    private fun chromeDp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun intent(ctx: Context): Intent = Intent(ctx, ShareActivity::class.java)
    }
}
