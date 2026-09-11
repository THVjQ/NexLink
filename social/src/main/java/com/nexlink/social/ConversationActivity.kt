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
import kotlinx.coroutines.launch
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
        val send = Button(this).apply { text = "Send"; isAllCaps = false }
        composer.addView(input); composer.addView(send)
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
            lifecycleScope.launch {
                val s = SessionProvider.manager(this@ConversationActivity).current() ?: return@launch
                // §13.5.1 — a failed send is surfaced, never swallowed. The
                // offline queue makes "failed" rarer, not impossible.
                s.send(rid, MessageBody.Text(body)).onFailure { e ->
                    render(listOf(), error = "Couldn't send: ${e.message}")
                }
            }
        }

        lifecycleScope.launch {
            val s = SessionProvider.manager(this@ConversationActivity).current() ?: return@launch
            val t = s.timeline(rid)
            timeline = t
            t.paginateBack(30)
            t.items.collectLatest { items -> render(items); scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } }
        }
    }

    override fun onDestroy() { timeline?.close(); super.onDestroy() }

    private fun render(items: List<TimelineItem>, error: String? = null) {
        list.removeAllViews()
        if (items.isEmpty() && error == null) {
            list.addView(text("No messages yet.", 14f, UiR.color.social_muted))
        }
        items.forEach { list.addView(bubble(it)) }
        error?.let { list.addView(text(it, 14f, UiR.color.social_danger)) }
    }

    private fun bubble(item: TimelineItem): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        addView(text(item.senderDisplayName, 12f, UiR.color.social_muted))
        when (val c = item.content) {
            is TimelineContent.Text -> addView(text(c.body, 16f, UiR.color.social_text))
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
        if (item.state == MessageState.SENDING) {
            addView(text("Sending…", 12f, UiR.color.social_muted))
        }
        if (item.reactions.isNotEmpty()) {
            addView(text(item.reactions.entries.joinToString(" ") { "${it.key} ${it.value.size}" },
                14f, UiR.color.social_text2))
        }
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
