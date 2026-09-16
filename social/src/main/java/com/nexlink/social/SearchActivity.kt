package com.nexlink.social

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nexlink.social.ui.chrome.Chrome
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexlink.social.core.session.MessageSearchHit
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import com.nexlink.social.ui.R as UiR

/**
 * Search your messages — §14.9.
 *
 * §1.3: *"Server-side message search — incompatible with the encryption
 * posture. Search is client-side over the local store."* That is not a
 * limitation being worked around; the server holds ciphertext and could not
 * search even if it were asked to.
 *
 * The screen says so plainly, because the consequence is user-visible: search
 * only finds what **this device** has decrypted. A message from before this
 * device existed (§8.5) is genuinely not findable here, and a user who does not
 * know that will think search is broken.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    /** The query was too short to be indexed — see the note in the click handler. */
    private var shortQuery = false
    /** Search has been pressed at least once, so an empty field can be explained. */
    private var attempted = false
    private lateinit var chrome: Chrome
    private var hits: List<MessageSearchHit> = emptyList()
    private var searched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // §14 — the same title bar as every other screen, from :social-ui's
        // Chrome. It also takes the system-bar insets that padForSystemBars
        // used to take here, so a screen's last row still clears the gesture
        // bar (§14.10) — that is the one thing this replacement must not lose.
        chrome = Chrome(this)
        val page = chrome.page("Search", onBack = { finish() })
        root = page.content
        setContentView(page.root)
        render()
    }

    private fun render() {
        root.removeAllViews()
        root.addView(chrome.note(
            "Searches messages on this phone. Anything sent before you added " +
            "this device isn't here — nobody can search your messages on the " +
            "server, including us."))

        val field = EditText(this).apply {
            hint = "Search messages"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(query)
            minHeight = dp(48)      // §14.10 — measured at 45dp on hardware
        }
        root.addView(field, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            marginStart = dp(12); marginEnd = dp(12)
        })

        root.addView(android.widget.Button(this).apply {
            text = "Search"
            isAllCaps = false
            tag = Chrome.PRIMARY
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = dp(8); marginStart = dp(12); marginEnd = dp(12)
            }
            setOnClickListener {
                val typed = field.text.toString().trim()
                // Two dead ends found by walking the screen (§14.9.2):
                //
                //   * an empty field did nothing at all — no search, no message,
                //     a button that appears broken;
                //   * a one- or two-character query reported "Nothing found",
                //     which is a lie. The index ignores very short terms, so
                //     searching "Hi" reported nothing while the word sat on
                //     screen in the conversation behind it. Verified on the
                //     handset: "Test" finds its message, "Hi" finds nothing.
                //
                // Saying nothing was found is a claim about the messages. Only
                // make it when a search actually ran.
                shortQuery = typed.isNotEmpty() && typed.length < MIN_QUERY
                attempted = true
                if (typed.isEmpty() || shortQuery) {
                    query = typed; searched = false; hits = emptyList(); render()
                    return@setOnClickListener
                }
                query = typed
                searched = true
                runSearch()
            }
        })

        if (shortQuery) {
            root.addView(chrome.note(
                "Search needs at least $MIN_QUERY characters — shorter words are " +
                    "not indexed, so a search for them finds nothing even when " +
                    "the word is there."))
        } else if (query.isEmpty() && attempted) {
            root.addView(chrome.note("Type what you're looking for, then tap Search."))
        }

        if (searched) {
            if (hits.isEmpty()) {
                root.addView(chrome.emptyState("Nothing found",
                    "No message on this device matches that."))
            } else {
                root.addView(chrome.sectionHeader(
                    if (hits.size == 1) "1 RESULT" else "${hits.size} RESULTS"))
                val card = chrome.card()
                hits.forEachIndexed { i, h ->
                    if (i > 0) card.addView(chrome.rowDivider(insetStartDp = 16))
                    card.addView(hitRow(h))
                }
                root.addView(card)
            }
        }
    }

    private var query = ""
    private var collecting = false

    private fun runSearch() {
        render()
        if (collecting) return
        collecting = true
        lifecycleScope.launch {
            val s = SessionProvider.manager(this@SearchActivity).current() ?: return@launch
            s.searchMessages(query).collectLatest { hits = it; render() }
        }
    }

    private fun hitRow(h: MessageSearchHit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        isClickable = true
        background = chrome.ripple(null)
        addView(text(
            (h.senderDisplayName ?: h.sender.value) + " · " +
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(h.timestamp)),
            12f, UiR.color.social_muted))
        addView(text(h.body, 16f, UiR.color.social_text))
        h.roomId?.let { rid ->
            setOnClickListener {
                startActivity(ConversationActivity.intent(this@SearchActivity, rid.value, "Conversation"))
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun gap(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(h))
    }
    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 1)
        setBackgroundColor(ContextCompat.getColor(this@SearchActivity, UiR.color.social_divider))
    }
    private fun text(v: CharSequence, size: Float, c: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = v
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@SearchActivity, c))
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    companion object {
        /** Measured on the handset: 2 characters finds nothing, 4 works. */
        private const val MIN_QUERY = 3

        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        fun intent(c: Context) = Intent(c, SearchActivity::class.java)
    }
}
