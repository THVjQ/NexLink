package com.nexlink.social

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexlink.social.core.session.MessageBody
import com.nexlink.social.core.session.MessageState
import com.nexlink.social.core.session.RoomId
import com.nexlink.social.core.session.Timeline
import com.nexlink.social.core.session.TimelineContent
import com.nexlink.social.core.session.TimelineItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nexlink.social.ui.R as UiR

/**
 * One conversation — §14.2 timeline, §14.3 composer.
 *
 * Deliberately plain: a scrolling list and a text field. §14's richer surface
 * (attachments, replies, edits, reactions) builds on this, and the shape that
 * matters first is that an encrypted message can be sent and read at all.
 */
class ConversationActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private var timeline: Timeline? = null
    private var roomId: RoomId? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rid = RoomId(intent.getStringExtra(EXTRA_ROOM) ?: run { finish(); return })
        roomId = rid
        title = intent.getStringExtra(EXTRA_TITLE) ?: rid.value

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        val scroll = ScrollView(this).apply {
            addView(list)
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        root.addView(scroll)

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            gravity = Gravity.CENTER_VERTICAL
        }
        val input = EditText(this).apply {
            hint = "Message"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val people = Button(this).apply { text = "☰"; isAllCaps = false }
        people.setOnClickListener { showParticipants() }
        val attach = Button(this).apply { text = "+"; isAllCaps = false }
        val send = Button(this).apply { text = "Send"; isAllCaps = false }
        composer.addView(people); composer.addView(attach); composer.addView(input); composer.addView(send)
        attach.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setItems(arrayOf("Photo", "File")) { _, i ->
                    if (i == 0) pickImage.launch("image/*") else pickFile.launch("*/*")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // §14.7 — announce typing while there is text, and stop when it is sent
        // or cleared. Debounced to one notice per few seconds.
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                val rid = roomId ?: return
                val typing = !s.isNullOrBlank()
                val now = System.currentTimeMillis()
                if (typing && now - lastTypingNotice < 4000) return
                lastTypingNotice = now
                lifecycleScope.launch {
                    SessionProvider.manager(this@ConversationActivity).current()
                        ?.setTyping(rid, typing)
                }
            }
        })
        root.addView(composer)
        setContentView(root)

        // Without this the composer sits underneath the gesture navigation bar,
        // and a tap on Send is consumed as a back gesture — the activity closes
        // and the message is never sent. Found on a real device; invisible on
        // anything with three-button navigation. §14.10.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.ime()
            )
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        send.setOnClickListener {
            val body = input.text.toString().trim()
            if (body.isEmpty()) return@setOnClickListener
            input.setText("")
            val reply = replyingTo?.eventId
            replyingTo = null
            lastTypingNotice = 0L
            lifecycleScope.launch {
                SessionProvider.manager(this@ConversationActivity).current()?.setTyping(rid, false)
            }
            lifecycleScope.launch {
                val s = SessionProvider.manager(this@ConversationActivity).current() ?: return@launch
                // §13.5.1 — a failed send is surfaced, never swallowed. The
                // offline queue makes "failed" rarer, not impossible.
                s.send(rid, MessageBody.Text(body, replyTo = reply)).onFailure { e ->
                    render(lastItems, error = "Couldn't send: ${e.message}")
                }
            }
        }

        lifecycleScope.launch {
            val s = SessionProvider.manager(this@ConversationActivity).current() ?: return@launch
            s.typingUsers(rid).collectLatest { who ->
                typingNow = who.filter { it != myUserId() }
                render(lastItems)
            }
        }

        lifecycleScope.launch {
            val s = SessionProvider.manager(this@ConversationActivity).current() ?: return@launch
            val t = s.timeline(rid)
            timeline = t
            t.paginateBack(30)
            t.items.collectLatest { items ->
                lastItems = items
                render(items)
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                // §14.7 — the conversation is open and on screen, so it has been
                // read. Marking on arrival rather than on scroll keeps the
                // unread count honest for the common case.
                if (isResumed && items.isNotEmpty()) s.markRead(rid)
            }
        }
    }

    /**
     * §14.5 — the photo picker. Uses GetContent rather than a media permission:
     * the user chooses one file and the app receives exactly that, so §2.8's
     * promise that NexLink gains no new permissions extends to this app asking
     * for none it can avoid.
     */
    private val pickImage = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        val rid = roomId ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val s = SessionProvider.manager(this@ConversationActivity).current() ?: return@launch
            sending = true; render(lastItems, null)
            s.sendImage(rid, uri.toString())
                .onFailure { render(lastItems, error = "Couldn't send that image: ${it.message}") }
            sending = false
        }
    }

    /** §14.5.3 — anything that is not a photo. Sent as-is, size-capped. */
    private val pickFile = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        val rid = roomId ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val s = SessionProvider.manager(this@ConversationActivity).current() ?: return@launch
            sending = true; render(lastItems, null)
            s.sendFile(rid, uri.toString())
                .onFailure { render(lastItems, error = it.message ?: "Couldn't send that file.") }
            sending = false
        }
    }

    private var lastItems: List<TimelineItem> = emptyList()
    private var sending = false
    /** §14.6 — flat replies. Threads are out of scope (§1.6). */
    private var replyingTo: TimelineItem? = null
    private var lastTypingNotice = 0L
    private var typingNow: List<String> = emptyList()

    private var isResumed = false

    override fun onResume() {
        super.onResume()
        isResumed = true
        val rid = roomId ?: return
        lifecycleScope.launch {
            SessionProvider.manager(this@ConversationActivity).current()?.markRead(rid)
        }
    }

    override fun onPause() {
        isResumed = false
        // §14.7 — stop claiming to type the moment the screen is not in front of
        // the user. A typing indicator that outlives the screen is a small lie.
        val rid = roomId
        if (rid != null) lifecycleScope.launch {
            SessionProvider.manager(this@ConversationActivity).current()?.setTyping(rid, false)
        }
        super.onPause()
    }

    override fun onDestroy() {
        timeline?.close()
        // §12.6.1 — drop decrypted media from memory with the screen.
        mediaCache.clear()
        super.onDestroy()
    }

    private fun render(items: List<TimelineItem>, error: String? = null) {
        list.removeAllViews()
        if (items.isEmpty() && error == null) {
            list.addView(text("No messages yet.", 14f, UiR.color.social_muted))
        }
        items.forEach { list.addView(bubble(it)) }
        replyingTo?.let { r ->
            list.addView(text(
                "Replying to ${r.senderDisplayName}: " +
                    ((r.content as? TimelineContent.Text)?.body?.take(60) ?: "message") +
                    "  — long-press again to cancel",
                13f, UiR.color.social_accent))
        }
        if (typingNow.isNotEmpty()) {
            val who = typingNow.joinToString(", ") { it.substringAfter('@').substringBefore(':') }
            list.addView(text(
                if (typingNow.size == 1) "$who is typing…" else "$who are typing…",
                14f, UiR.color.social_muted))
        }
        if (sending) list.addView(text("Sending image…", 14f, UiR.color.social_muted))
        error?.let { list.addView(text(it, 14f, UiR.color.social_danger)) }
    }

    private fun bubble(item: TimelineItem): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        // §14.4.2 — long-press is the entry point. A visible button per message
        // would crowd the timeline; a long-press is what people already try.
        isLongClickable = true
        setOnLongClickListener { showMessageMenu(item); true }
        addView(text(item.senderDisplayName, 12f, UiR.color.social_muted))
        when (val c = item.content) {
            is TimelineContent.Text -> addView(text(c.body, 16f, UiR.color.social_text))
            is TimelineContent.Image -> {
                addView(imageView(c))
                c.caption?.takeIf { it.isNotBlank() }
                    ?.let { addView(text(it, 15f, UiR.color.social_text2)) }
            }
            is TimelineContent.File ->
                addView(text(
                    "📎 ${c.displayName}" + (c.sizeBytes?.let { " · " + humanSize(it) } ?: ""),
                    16f, UiR.color.social_text))
            is TimelineContent.Video ->
                addView(text("🎬 Video" + (c.durationMs?.let { " · ${it / 1000}s" } ?: ""),
                    16f, UiR.color.social_text))
            is TimelineContent.Audio ->
                addView(text("🎵 Audio" + (c.durationMs?.let { " · ${it / 1000}s" } ?: ""),
                    16f, UiR.color.social_text))
            is TimelineContent.Redacted ->
                addView(text("Message deleted", 15f, UiR.color.social_muted))
            // §14.2.3 — say what happened and why it is usually expected. An
            // empty bubble here is the failure mode that makes users think the
            // app is broken when it is working as designed (§8.5).
            is TimelineContent.Undecryptable ->
                addView(text(
                    "Can't decrypt this message. It was probably sent before " +
                    "this device was added to your account.",
                    15f, UiR.color.social_muted))
            else -> addView(text("[${c::class.simpleName}]", 15f, UiR.color.social_muted))
        }
        if (item.isEdited) addView(text("edited", 12f, UiR.color.social_muted))
        if (item.state == MessageState.SENDING) {
            addView(text("Sending…", 12f, UiR.color.social_muted))
        }
        if (item.reactions.isNotEmpty()) {
            addView(reactionStrip(item))
        }
    }

    /**
     * §14.4.4 — each reaction is its own tappable chip showing the count.
     * Tapping one toggles your own, which is how a user removes a reaction
     * without hunting for a separate control.
     */
    private fun reactionStrip(item: TimelineItem): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, 0)
        item.reactions.forEach { (emoji, senders) ->
            addView(TextView(this@ConversationActivity).apply {
                // §14.4.3 — the emoji is rendered as-is. Never index into it,
                // never truncate it: a ZWJ sequence is one grapheme made of
                // several code points and splitting it produces broken boxes.
                text = "$emoji ${senders.size}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(ContextCompat.getColor(this@ConversationActivity, UiR.color.social_text2))
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener { react(item, emoji) }
            })
        }
    }

    /**
     * §14.6 — what you can do to a message. Edit and delete appear only on your
     * own messages, because they are the only ones you can change; offering them
     * everywhere and failing at the server is worse than not offering them.
     */
    private fun showMessageMenu(item: TimelineItem) {
        val mine = item.sender.value == myUserId()
        val actions = buildList {
            add("React")
            add("Reply")
            if (mine) { add("Edit"); add("Delete") }
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(actions.toTypedArray()) { _, i ->
                when (actions[i]) {
                    "React" -> showReactionPicker(item)
                    "Reply" -> { replyingTo = item; render(lastItems) }
                    "Edit" -> showEdit(item)
                    "Delete" -> confirmDelete(item)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun myUserId(): String? =
        (SessionProvider.manager(this).current() as? com.nexlink.social.core.rust.RustSocialSession)
            ?.currentState()?.let {
                (it as? com.nexlink.social.core.session.SessionState.SignedIn)?.userId?.value
            }

    private fun showEdit(item: TimelineItem) {
        val current = (item.content as? TimelineContent.Text)?.body ?: return
        val input = EditText(this).apply { setText(current); setSelection(current.length) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Edit message")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val rid = roomId ?: return@setPositiveButton
                val text = input.text.toString().trim()
                if (text.isEmpty() || text == current) return@setPositiveButton
                lifecycleScope.launch {
                    val s = SessionProvider.manager(this@ConversationActivity).current() ?: return@launch
                    s.edit(rid, item.eventId, text).onFailure {
                        render(lastItems, error = "Couldn't edit: ${it.message}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(item: TimelineItem) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete this message?")
            // §14.6 — state the limit. "Deleted for everyone" is an overclaim:
            // it asks clients to remove it and the server to drop the content.
            // It cannot reach a screenshot, and the original lingers server-side
            // for the redaction retention period (§22.4, 7 days).
            .setMessage(
                "It will be removed for everyone in this conversation.\n\n" +
                "It can't reach a copy someone already saved or screenshotted."
            )
            .setPositiveButton("Delete") { _, _ ->
                val rid = roomId ?: return@setPositiveButton
                lifecycleScope.launch {
                    val s = SessionProvider.manager(this@ConversationActivity).current() ?: return@launch
                    s.delete(rid, item.eventId, null).onFailure {
                        render(lastItems, error = "Couldn't delete: ${it.message}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** §14.4.2 — a short list plus the keyboard, per §1.2 ("any emoji"). */
    private fun showReactionPicker(item: TimelineItem) {
        val quick = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("React")
            .setItems(quick.toTypedArray()) { _, i -> react(item, quick[i]) }
            .setNeutralButton("Other…") { _, _ -> showCustomEmoji(item) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * §1.2 promises "any emoji the user's keyboard can produce", so the picker
     * cannot be a fixed list. This takes whatever the keyboard gives.
     */
    private fun showCustomEmoji(item: TimelineItem) {
        val input = EditText(this).apply { hint = "Any emoji" }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("React")
            .setView(input)
            .setPositiveButton("React") { _, _ ->
                val e = input.text.toString().trim()
                if (e.isNotEmpty()) react(item, e)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun react(item: TimelineItem, emoji: String) {
        val rid = roomId ?: return
        lifecycleScope.launch {
            val s = SessionProvider.manager(this@ConversationActivity).current() ?: return@launch
            s.react(rid, item.eventId, emoji).onFailure {
                render(listOf(), error = "Couldn't react: ${it.message}")
            }
        }
    }

    /**
     * §14.5.2 — render an inline image.
     *
     * §12.6.1: the decrypted bytes are held in memory and handed straight to a
     * bitmap. They are deliberately never written to a file — a decrypted photo
     * on disk is the one place message content would exist in the clear on the
     * device, reachable by another app or a backup.
     *
     * A tiny in-memory cache keeps scrolling from re-downloading, and is cleared
     * with the activity.
     */
    private fun imageView(c: TimelineContent.Image): View {
        val iv = android.widget.ImageView(this).apply {
            adjustViewBounds = true
            maxHeight = dp(320)
            scaleType = android.widget.ImageView.ScaleType.FIT_START
            contentDescription = c.caption ?: "Image"
        }
        mediaCache[c.mediaId]?.let { iv.setImageBitmap(it); return iv }

        lifecycleScope.launch {
            val s = SessionProvider.manager(this@ConversationActivity).current() ?: return@launch
            s.loadMedia(c.mediaId)
                .onSuccess { bytes ->
                    val bmp = withContext(Dispatchers.Default) {
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    if (bmp != null) { mediaCache[c.mediaId] = bmp; iv.setImageBitmap(bmp) }
                }
                .onFailure {
                    // §14.2.2 — say what happened rather than showing a blank box.
                    iv.contentDescription = "Image unavailable"
                }
        }
        return iv
    }

    private val mediaCache = mutableMapOf<String, android.graphics.Bitmap>()

    /**
     * §14.8 — who is in this conversation.
     *
     * Invited-but-not-joined is shown distinctly. In an encrypted room that
     * distinction is not cosmetic: someone who has not joined cannot read what
     * is being said, and a member list that implies otherwise would mislead
     * people about who their messages are reaching.
     */
    private fun showParticipants() {
        val rid = roomId ?: return
        lifecycleScope.launch {
            val s = SessionProvider.manager(this@ConversationActivity).current() ?: return@launch
            s.members(rid)
                .onSuccess { list ->
                    val lines = list
                        .filter { it.membership == "joined" || it.membership == "invited" }
                        .sortedBy { it.membership }
                        .map {
                            val who = it.displayName ?: it.id.value
                            val tag = when {
                                it.isSelf -> " (you)"
                                it.membership == "invited" -> " — invited, hasn't joined"
                                else -> ""
                            }
                            "$who$tag\n${it.id.value}"
                        }
                    androidx.appcompat.app.AlertDialog.Builder(this@ConversationActivity)
                        .setTitle("In this conversation")
                        .setItems(lines.toTypedArray(), null)
                        .setNeutralButton("Add someone") { _, _ -> promptInvite(rid) }
                        .setPositiveButton("Close", null)
                        .show()
                }
                .onFailure { render(lastItems, error = "Couldn't load participants: ${it.message}") }
        }
    }

    private fun promptInvite(rid: com.nexlink.social.core.session.RoomId) {
        val input = EditText(this).apply { hint = "username" }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add to this conversation")
            // §14.8 — adding someone does not give them the past. Say so, rather
            // than letting people assume either way.
            .setMessage("They'll be invited. They won't be able to read messages sent before they join.")
            .setView(input)
            .setPositiveButton("Invite") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    val s = SessionProvider.manager(this@ConversationActivity).current() ?: return@launch
                    val mxid = if (name.startsWith("@")) name
                               else "@$name:${SignInActivity.HOMESERVER.substringAfter("://")}"
                    s.inviteToRoom(rid, com.nexlink.social.core.session.UserId(mxid))
                        .onFailure { render(lastItems, error = "Couldn't invite: ${it.message}") }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** "0 KB" for a real file is a bug report waiting to happen. */
    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(v: CharSequence, size: Float, colour: Int) = TextView(this).apply {
        text = v
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(ContextCompat.getColor(this@ConversationActivity, colour))
        if (size >= 16f) setTypeface(typeface, Typeface.NORMAL)
    }

    companion object {
        private const val EXTRA_ROOM = "room_id"
        private const val EXTRA_TITLE = "title"
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        fun intent(c: Context, roomId: String, title: String) =
            Intent(c, ConversationActivity::class.java)
                .putExtra(EXTRA_ROOM, roomId).putExtra(EXTRA_TITLE, title)
    }
}
