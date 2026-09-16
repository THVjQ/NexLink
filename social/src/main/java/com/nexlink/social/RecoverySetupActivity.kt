package com.nexlink.social

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nexlink.social.ui.chrome.Chrome
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.nexlink.social.ui.R as UiR

/**
 * Recovery key setup — §7.3, §7.4.
 *
 * This screen exists because of a promise the product makes and cannot take
 * back: **nobody can recover the user's history for them.** §7.3 is explicit
 * that a warning which fails here is worse than useless, because the user only
 * discovers the consequence on the day they lose their phone.
 *
 * So the design follows §7.3.3: the key is shown once, and the user must
 * demonstrate they have it before the screen will let them leave.
 */
class RecoverySetupActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private var key: String? = null
    private var status: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // §14 — the same title bar as every other screen, from :social-ui's
        // Chrome. It also takes the system-bar insets that padForSystemBars
        // used to take here, so a screen's last row still clears the gesture
        // bar (§14.10) — that is the one thing this replacement must not lose.
        val page = Chrome(this).page("Recovery key", onBack = { finish() },
            horizontalPaddingDp = 20)
        root = page.content
        setContentView(page.root)
        renderIntro()
    }

    /**
     * Has this account already got recovery set up?
     *
     * It matters because §7.4's flow **replaces** the key rather than showing
     * the existing one — that is inherent, the old key is not recoverable by
     * design. Reaching this screen from the warning banner meant it could only
     * be a first-time setup; now that it has a permanent menu entry (§7.4.1),
     * someone can arrive here already holding a key, and creating a new one
     * silently would break the key they have written down and every other
     * device relying on it.
     */
    private fun alreadySetUp(): Boolean =
        (SessionProvider.manager(this).current()
            as? com.nexlink.social.core.rust.RustSocialSession)
            ?.currentState()
            ?.let { it as? com.nexlink.social.core.session.SessionState.SignedIn }
            ?.keyBackupHealthy == true

    private fun renderIntro() {
        root.removeAllViews()
        val replacing = alreadySetUp()
        root.addView(text(
            if (replacing) "Replace your recovery key" else "Your recovery key",
            26f, UiR.color.social_text, bold = true))
        root.addView(text(
            if (replacing)
                "You already have a recovery key for this account.\n\n" +
                "There is no way to show it again — that is the point of it. " +
                "You can create a NEW one, but the old key stops working the " +
                "moment you do, and any device or note still holding it becomes " +
                "useless.\n\n" +
                "Only do this if you have lost the key you had."
            else
                "Your messages are encrypted. That means if you lose this phone, " +
                "nobody — including us — can get your history back for you.\n\n" +
                "A recovery key is the only way back in. We'll show it once. " +
                "Write it down or save it in a password manager.",
            16f, UiR.color.social_text2))
        root.addView(gap(12))
        status?.let { root.addView(text(it, 14f, UiR.color.social_accent)); root.addView(gap(8)) }

        val pass = EditText(this).apply {
            hint = "Your password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(text("Confirm your password to continue", 13f, UiR.color.social_muted))
        root.addView(pass)

        root.addView(button(
            if (replacing) "Create a new key (the old one stops working)"
            else "Create my recovery key"
        ) {
            val p = pass.text.toString()
            if (p.isEmpty()) { status = "Enter your password."; renderIntro(); return@button }
            status = "Setting up…"; renderIntro()
            lifecycleScope.launch {
                SessionProvider.manager(this@RecoverySetupActivity)
                    .setUpRecovery(p) { msg -> status = msg; renderIntro() }
                    .onSuccess { key = it; renderKey(it) }
                    .onFailure { status = it.message ?: "Couldn't set up recovery."; renderIntro() }
            }
        })
        root.addView(button("Not now") { finish() }.also {
            it.setTextColor(ContextCompat.getColor(this, UiR.color.social_muted))
        })
    }

    /** §7.3.3 — shown once, and the gate out requires proving it was kept. */
    private fun renderKey(k: String) {
        root.removeAllViews()
        root.addView(text("Save this key now", 26f, UiR.color.social_text, bold = true))
        root.addView(text(
            "This is the only time it will be shown. If you lose it and lose " +
            "your phone, your message history is gone permanently.",
            15f, UiR.color.social_danger))
        root.addView(gap(12))

        root.addView(TextView(this).apply {
            text = k
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(android.graphics.Typeface.MONOSPACE)
            setTextColor(ContextCompat.getColor(this@RecoverySetupActivity, UiR.color.social_text))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        })

        root.addView(button("Copy") {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("recovery key", k))
        })

        root.addView(gap(12))
        val confirm = CheckBox(this).apply {
            text = "I have saved my recovery key somewhere safe"
            tag = Chrome.PRIMARY
            setTextColor(ContextCompat.getColor(this@RecoverySetupActivity, UiR.color.social_text))
        }
        root.addView(confirm)

        val done = button("Done") { finish() }
        done.isEnabled = false
        confirm.setOnCheckedChangeListener { _, checked -> done.isEnabled = checked }
        root.addView(done)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun gap(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(h))
    }
    private fun text(v: CharSequence, size: Float, c: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = v
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@RecoverySetupActivity, c))
            setLineSpacing(dp(4).toFloat(), 1f)
        }
    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
    }

    companion object {
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        fun intent(c: Context) = Intent(c, RecoverySetupActivity::class.java)
    }
}
