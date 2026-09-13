package com.nexlink.social

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
    private var blocked: List<UserId> = emptyList()
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        root.padForSystemBars(dp(20), dp(24), dp(24))
        render()
        lifecycleScope.launch {
            SessionProvider.manager(this@BlockedActivity).current()
                ?.blockedUsers()?.collectLatest { blocked = it; loaded = true; render() }
        }
    }

    private fun render() {
        root.removeAllViews()
        root.addView(title("Blocked people"))
        if (!loaded) { root.addView(body("Loading…")); return }
        if (blocked.isEmpty()) {
            root.addView(body(
                "You haven't blocked anyone.\n\n" +
                "You can block someone from any of their messages, or by holding " +
                "down a conversation in your list."))
            return
        }
        root.addView(body(
            "Blocked people can't message you, invite you, or call you. " +
            "They aren't told."))
        blocked.forEach { u -> root.addView(rowFor(u)) }
    }

    private fun rowFor(u: UserId): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(12), 0, dp(12))
        addView(TextView(this@BlockedActivity).apply {
            text = u.value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(this@BlockedActivity, UiR.color.social_text))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        addView(Button(this@BlockedActivity).apply {
            text = "Unblock"
            isAllCaps = false
            minHeight = dp(48)          // §14.10
            contentDescription = "Unblock ${u.value}"
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
