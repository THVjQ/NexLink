package com.nexlink.social

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nexlink.social.core.session.MatrixUsername
import com.nexlink.social.ui.chrome.Chrome
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.nexlink.social.ui.R as UiR

/**
 * Sign-in — §6, phase 3.
 *
 * Deliberately minimal, and deliberately **not** a registration form: §2.5 means
 * accounts are only created through the acceptance gate with an invite code
 * (§9.6), so this screen signs in an account that already exists. The homeserver
 * is fixed (§2.6 — there is one), so it is shown but not editable.
 */
class SignInActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        fun col(id: Int) = ContextCompat.getColor(this, id)

        // §14 — the same title bar as every other screen, from :social-ui's
        // Chrome. It also takes the system-bar insets that padForSystemBars
        // used to take here, so a screen's last row still clears the gesture
        // bar (§14.10) — that is the one thing this replacement must not lose.
        val page = Chrome(this).page("Sign in", onBack = { finish() },
            horizontalPaddingDp = 20)
        val root = page.content
        setContentView(page.root)

        fun label(t: String, size: Float, bold: Boolean = false, c: Int = UiR.color.social_text) =
            TextView(this).apply {
                text = t
                setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(col(c))
            }

        root.addView(label(HOMESERVER.removePrefix("https://"), 14f, c = UiR.color.social_muted))
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(16)) })

        val user = EditText(this).apply {
            hint = "Username"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val pass = EditText(this).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(user); root.addView(pass)

        val status = label("", 14f, c = UiR.color.social_muted)
        root.addView(status)

        val go = Button(this).apply {
            text = "Sign in"
            tag = Chrome.PRIMARY
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) }
        }
        root.addView(go)

        // §7.5.2 — the way back in for someone whose old phone is gone. It
        // belongs here because here is where that person is: they cannot sign
        // in on a device they no longer have, and a restore is not a sign-in.
        root.addView(Button(this).apply {
            text = "Restore from a backup"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
            setOnClickListener { startActivity(ImportActivity.intent(this@SignInActivity)) }
        })

        go.setOnClickListener {
            val u = normaliseUsername(user.text.toString())
            val p = pass.text.toString()
            if (u.isEmpty() || p.isEmpty()) { status.text = "Enter your username and password."; return@setOnClickListener }
            go.isEnabled = false
            status.text = "Signing in…"
            lifecycleScope.launch {
                val r = SessionProvider.manager(this@SignInActivity).signIn(HOMESERVER, u, p)
                if (r.isSuccess) {
                    setResult(Activity.RESULT_OK); finish()
                } else {
                    // §14.2.2's principle applied to sign-in: say what happened.
                    status.text = explain(r.exceptionOrNull())
                    go.isEnabled = true
                }
            }
        }
    }

    /**
     * People type their address, not their localpart.
     *
     * The server stores `thvjq`; someone reading their own ID off the app types
     * `@thvjq:nexlink.thvjq.com.au`, or just `@thvjq`, and both used to be sent
     * verbatim. Synapse then logged *"Attempted to login as @thvjq but they do
     * not exist"* and answered 403 — which the screen reported as though the
     * password were wrong. Observed on the operator's own first sign-in.
     *
     * Capitals are folded for the same reason: a Matrix localpart cannot
     * contain them, so `THVjQ` can only ever be a display name, and rejecting
     * it teaches nothing.
     */
    internal fun normaliseUsername(raw: String): String = MatrixUsername.normalise(raw)

    /**
     * §14.2.2 — say what happened, and what to do about it.
     *
     * The case that matters is Synapse's login rate limit (`rc_login.account`,
     * one attempt per 20 seconds). A second try straight after a *successful*
     * sign-in gets a 429, and the raw message reads like a server fault rather
     * than "wait a moment" — so someone who mistypes once and immediately
     * retries concludes their password is wrong when it is not.
     */
    internal fun explain(e: Throwable?): String {
        val m = e?.message ?: return "Sign-in failed."
        return when {
            m.contains("M_LIMIT_EXCEEDED", true) || m.contains("Too Many Requests", true) ||
                m.contains("429") ->
                "Too many attempts just now. Wait about 30 seconds and try again — " +
                    "this is the server's rate limit, not your password."
            m.contains("M_FORBIDDEN", true) || m.contains("Invalid username or password", true) ->
                "That username or password was not accepted. Your username is the " +
                    "short name, without the @ and without the server after it."
            else -> m
        }
    }

    companion object {
        /** §26.2 — one homeserver, permanent. */
        const val HOMESERVER = "https://nexlink.thvjq.com.au"
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        fun intent(c: Context) = Intent(c, SignInActivity::class.java)
    }
}
