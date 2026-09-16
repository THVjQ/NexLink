package com.nexlink.social

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.nexlink.social.core.Invite
import com.nexlink.social.core.rust.RustSocialSession
import com.nexlink.social.ui.chrome.Chrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import com.nexlink.social.ui.R as UiR

/**
 * §9.5 — invite someone.
 *
 * Until 2026-09-16 this did not exist: invites were operator-only (§9.8), one
 * SSH command per person. That is the right control for the first twenty users
 * and the wrong one for the twenty-first.
 *
 * The screen is deliberately one button and one result. An invite is a thing
 * you do *for* someone who is standing next to you or waiting on a message, so
 * the whole flow is: tap, read the code out or share it, done.
 *
 * **The code is shown once, in full, and can be re-shown.** Unlike a recovery
 * key (§7.4) there is no reason to hide it — it creates one account, it expires,
 * and it is worthless once used. Treating it as a secret to be guarded would
 * teach the wrong instinct about the key that genuinely is one.
 */
class InviteActivity : AppCompatActivity() {

    private lateinit var chrome: Chrome
    private lateinit var root: LinearLayout
    private var invite: Invite? = null
    private var busy = false
    private var error: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chrome = Chrome(this)
        val page = chrome.page("Invite someone", onBack = { finish() })
        root = page.content
        setContentView(page.root)
        render()
    }

    private fun render() {
        root.removeAllViews()
        val got = invite

        if (got == null) {
            root.addView(chrome.note(
                "NexLink Social is invite only. Create a code and give it to the " +
                    "person you want to add — they'll need it to make an account.\n\n" +
                    "Each code works once and expires after two weeks."))
            root.addView(with(chrome) { spacer(6) })
            root.addView(chrome.filledButton(
                if (busy) "Creating…" else "Create an invite code"
            ) { if (!busy) create() }.apply {
                isEnabled = !busy
                layoutParams = wide()
            })
        } else {
            root.addView(codeCard(got))
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), dp(10), dp(12), 0)
                addView(chrome.filledButton("Share") { share(got) }
                    .apply { layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) })
                addView(chrome.quietButton("Copy") { copy(got) }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                        .also { it.marginStart = dp(10) }
                })
            })
            root.addView(chrome.note(
                "Give this to one person. It stops working once they've used it" +
                    (got.expiresAt.takeIf { it > 0 }?.let {
                        ", and expires on " +
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                    } ?: "") + "."))
            root.addView(with(chrome) { spacer(8) })
            root.addView(chrome.quietButton(
                if (busy) "Creating…" else "Create another"
            ) { if (!busy) create() }.apply {
                isEnabled = !busy
                layoutParams = wide()
            })
        }

        error?.let {
            root.addView(chrome.note(it).apply {
                setTextColor(ContextCompat.getColor(this@InviteActivity, UiR.color.social_danger))
            })
        }
    }

    /**
     * The code itself, at a size someone can read aloud across a room.
     *
     * Monospaced and grouped, because every character matters and `8`/`B` and
     * `5`/`S` are the pairs people get wrong — §9.3 already removed `I`, `L`,
     * `O` and `U` for the same reason.
     */
    private fun codeCard(i: Invite): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = chrome.rounded(
            ContextCompat.getColor(this@InviteActivity, UiR.color.social_surface), 20f)
        setPadding(dp(18), dp(22), dp(18), dp(22))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
            marginStart = dp(12); marginEnd = dp(12)
        }
        addView(TextView(this@InviteActivity).apply {
            text = "INVITE CODE"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.12f
            setTextColor(ContextCompat.getColor(this@InviteActivity, UiR.color.social_muted))
        })
        addView(TextView(this@InviteActivity).apply {
            text = i.formatted
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.05f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@InviteActivity, UiR.color.social_text))
            setPadding(0, dp(10), 0, 0)
            // §14.10 — a screen reader reading "8WGJ-RYJV-4MZ0" as a word is
            // useless for something that has to be transcribed exactly.
            contentDescription = "Invite code, " + i.formatted.replace("-", ", ")
                .toCharArray().joinToString(" ")
        })
    }

    private fun create() {
        busy = true; error = null; render()
        lifecycleScope.launch {
            val s = SessionProvider.manager(this@InviteActivity).current()
                as? RustSocialSession
            if (s == null) {
                busy = false; error = "You're not signed in."; render(); return@launch
            }
            val r = withContext(Dispatchers.IO) { s.inviteIssuer().create() }
            busy = false
            r.onSuccess { invite = it }
                .onFailure { error = it.message ?: "Couldn't create an invite." }
            render()
        }
    }

    private fun share(i: Invite) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT,
                "Here's your invite code for NexLink Social: ${i.formatted}\n\n" +
                    "Install the app, choose \"I have an invite code\", and enter it.")
        }, "Share invite code"))
    }

    private fun copy(i: Invite) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Invite code", i.formatted))
        Snackbar.make(findViewById(android.R.id.content), "Code copied", Snackbar.LENGTH_SHORT)
            .show()
    }

    private fun wide() = LinearLayout.LayoutParams(MATCH, WRAP).apply {
        marginStart = dp(12); marginEnd = dp(12)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        fun intent(c: Context) = Intent(c, InviteActivity::class.java)
    }
}
