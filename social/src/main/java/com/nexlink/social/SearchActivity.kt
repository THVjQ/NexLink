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
    private var hits: List<MessageSearchHit> = emptyList()
    private var searched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // §14 — the same title bar as every other screen, from :social-ui's
        // Chrome. It also takes the system-bar insets that padForSystemBars
        // used to take here, so a screen's last row still clears the gesture
        // bar (§14.10) — that is the one thing this replacement must not lose.
        val page = Chrome(this).page("Search", onBack = { finish() },
            horizontalPaddingDp = 20)
        root = page.content
        setContentView(page.root)
        render()
    }

    private fun render() {
        root.removeAllViews()
        root.addView(text(
            "Searches messages on this phone. Anything sent before you added " +
            "this device isn't here — nobody can search your messages on the " +
            "server, including us.",
            13f, UiR.color.social_muted))
        root.addView(gap(12))

        val field = EditText(this).apply {
            hint = "Search messages"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(query)
        }
        root.addView(field)

        root.addView(android.widget.Button(this).apply {
            text = "Search"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
            setOnClickListener {
                query = field.text.toString().trim()
                if (query.isEmpty()) return@setOnClickListener
                searched = true
                runSearch()
            }
        })

        if (searched) {
            root.addView(gap(12)); root.addView(divider()); root.addView(gap(8))
            if (hits.isEmpty()) {
                root.addView(text("Nothing found on this device.", 14f, UiR.color.social_muted))
            } else hits.forEach { root.addView(hitRow(it)) }
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
        setPadding(0, dp(10), 0, dp(10))
        isClickable = true
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
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        fun intent(c: Context) = Intent(c, SearchActivity::class.java)
    }
}
