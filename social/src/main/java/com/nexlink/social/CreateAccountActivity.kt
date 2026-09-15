package com.nexlink.social

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.nexlink.social.ui.chrome.Chrome
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexlink.social.core.Registration
import com.nexlink.social.core.invite.InviteCode
import com.nexlink.social.ui.onboarding.AcceptanceGateActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nexlink.social.ui.R as UiR

/**
 * Account creation — §6, §9.6, §22.10.
 *
 * **The order of operations here is a §2.8 invariant.** §22.10 established that
 * Synapse redeems the invite token in a User-Interactive Auth step that
 * completes *before* the account exists, and that is the only window the
 * acceptance gate can occupy:
 *
 * ```
 * username + password + invite code
 *        -> redeem the token        (no account yet)
 *        -> the acceptance gate     (§9.6, five screens)
 *        -> complete registration   (account created)
 * ```
 *
 * The username and password are collected first only because `/register`
 * requires them in the very first request. Nothing is created by that step.
 *
 * If the user abandons the gate, **no account is created** — the token stays
 * `pending` and expires. That is the correct outcome and is what §2.8's third
 * invariant demands.
 */
class CreateAccountActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private var pending: Registration.Pending? = null
    private val reg by lazy { Registration(SignInActivity.HOMESERVER) }

    private val gate = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val p = pending
        if (result.resultCode != Activity.RESULT_OK || p == null) {
            // §9.6 — abandoning the gate must not leave an account behind.
            pending = null
            render(status = "Account not created. Your invite code is still unused.")
            return@registerForActivityResult
        }
        render(status = "Creating your account…")
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { reg.complete(p) }
            r.onSuccess { mxid ->
                render(status = "Account created: $mxid\nSigning you in…")
                val signIn = SessionProvider.manager(this@CreateAccountActivity)
                    .signIn(SignInActivity.HOMESERVER, p.username, p.password)
                if (signIn.isSuccess) { setResult(Activity.RESULT_OK); finish() }
                else render(status = "Account created, but sign-in failed. Try signing in.")
            }.onFailure { render(status = it.message ?: "Could not finish creating the account.") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // §14 — the same title bar as every other screen, from :social-ui's
        // Chrome. It also takes the system-bar insets that padForSystemBars
        // used to take here, so a screen's last row still clears the gesture
        // bar (§14.10) — that is the one thing this replacement must not lose.
        val page = Chrome(this).page("Create your account", onBack = { finish() },
            horizontalPaddingDp = 20)
        root = page.content
        setContentView(page.root)
        render()
    }

    private var user = ""
    private var pass = ""
    private var code = ""

    private fun render(status: String? = null) {
        root.removeAllViews()
        root.addView(text(
            "Your username is permanent — it can't be changed later, because " +
            "changing it would let someone impersonate you.",
            14f, UiR.color.social_muted))
        root.addView(gap(12))

        val u = field("Username", user, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        val p = field("Password", pass, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val c = field("Invite code", code, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        root.addView(u); root.addView(p); root.addView(c)

        status?.let { root.addView(gap(8)); root.addView(text(it, 14f, UiR.color.social_accent)) }

        root.addView(Button(this).apply {
            text = "Continue"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) }
            setOnClickListener {
                user = u.text.toString().trim().lowercase()
                pass = p.text.toString()
                code = InviteCode.normalise(c.text.toString())
                start()
            }
        })
    }

    private fun start() {
        if (user.isEmpty() || pass.length < 8) {
            render(status = "Pick a username and a password of at least 8 characters.")
            return
        }
        if (!InviteCode.isValid(code)) { render(status = "That invite code isn't valid."); return }
        render(status = "Checking…")
        lifecycleScope.launch {
            val available = withContext(Dispatchers.IO) { reg.isUsernameAvailable(user) }
            if (available.getOrDefault(false) != true) {
                render(status = "That username is taken."); return@launch
            }
            // §22.10 — redeem the token, then hand over to the gate. No account
            // exists between here and the gate completing.
            val redeemed = withContext(Dispatchers.IO) { reg.redeemInvite(user, pass, code) }
            redeemed.onSuccess {
                pending = it
                gate.launch(AcceptanceGateActivity.intent(this@CreateAccountActivity, code))
            }.onFailure { render(status = it.message ?: "That invite code didn't work.") }
        }
    }

    private fun field(hint: String, value: String, type: Int) = EditText(this).apply {
        this.hint = hint; setText(value); inputType = type
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun gap(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(h))
    }
    private fun text(v: CharSequence, size: Float, c: Int = UiR.color.social_text, bold: Boolean = false) =
        TextView(this).apply {
            text = v
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@CreateAccountActivity, c))
        }

    companion object {
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        fun intent(c: Context) = Intent(c, CreateAccountActivity::class.java)
    }
}
