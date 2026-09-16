package com.nexlink.social

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nexlink.social.ui.chrome.Chrome
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexlink.social.core.session.UserId
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.nexlink.social.ui.R as UiR

/**
 * §31.3.1 — who you have blocked, and the way back.
 *
 * A block with no way to undo it is a trap rather than a control, and §31.3.1
 * puts blocking under the affected user's control. That means they must be able
 * to see the list and change their mind — including for a block made months ago
 * on a different device, which is why the list is read live from the server's
 * ignore list rather than from anything local.
 *
 * Deliberately plain. This screen is most likely to be opened by someone who is
 * upset, and it should answer one question — *who is blocked?* — without
 * requiring them to read anything.
 */
class BlockedActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var chrome: Chrome
    private var blocked: List<UserId> = emptyList()
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // §14 — the same title bar as every other screen, from :social-ui's
        // Chrome. It also takes the system-bar insets that padForSystemBars
        // used to take here, so a screen's last row still clears the gesture
        // bar (§14.10) — that is the one thing this replacement must not lose.
        chrome = Chrome(this)
        val page = chrome.page("Blocked people", onBack = { finish() })
        root = page.content
        setContentView(page.root)
        render()
        lifecycleScope.launch {
            SessionProvider.manager(this@BlockedActivity).current()
                ?.blockedUsers()?.collectLatest { blocked = it; loaded = true; render() }
        }
    }

    private fun render() {
        root.removeAllViews()
        if (!loaded) { root.addView(chrome.note("Loading…")); return }
        if (blocked.isEmpty()) {
            root.addView(chrome.emptyState(
                "You haven't blocked anyone",
                "You can block someone from any of their messages, or by holding " +
                "down a conversation in your list."))
            return
        }
        root.addView(chrome.note(
            "Blocked people can't message you, invite you, or call you. " +
            "They aren't told."))
        val card = chrome.card()
        blocked.forEachIndexed { i, u ->
            if (i > 0) card.addView(chrome.rowDivider(insetStartDp = 16))
            card.addView(rowFor(u))
        }
        root.addView(card)
    }

    private fun rowFor(u: UserId): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(10), dp(8), dp(10))
        addView(chrome.avatar(u.value, sizeDp = 38))
        addView(TextView(this@BlockedActivity).apply {
            text = u.value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(this@BlockedActivity, UiR.color.social_text))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                .apply { marginStart = dp(12) }
        })
        addView(TextView(this@BlockedActivity).apply {
            text = "Unblock"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@BlockedActivity, UiR.color.social_accent))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            minHeight = dp(48)          // §14.10
            minWidth = dp(48)
            contentDescription = "Unblock ${u.value}"
            background = chrome.ripple(chrome.rounded(ContextCompat.getColor(
                this@BlockedActivity, UiR.color.social_surface2), 12f))
            setOnClickListener {
                lifecycleScope.launch {
                    SessionProvider.manager(this@BlockedActivity).current()
                        ?.unblockUser(u)
                }
            }
        })
    }

    private fun title(t: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setTypeface(null, Typeface.BOLD)
        setTextColor(ContextCompat.getColor(this@BlockedActivity, UiR.color.social_text))
        setPadding(0, 0, 0, dp(12))
    }

    private fun body(t: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(ContextCompat.getColor(this@BlockedActivity, UiR.color.social_text2))
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object { fun intent(c: Context) = Intent(c, BlockedActivity::class.java) }
}
