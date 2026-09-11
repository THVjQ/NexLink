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
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(20))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        fun label(t: String, size: Float, bold: Boolean = false, c: Int = UiR.color.social_text) =
            TextView(this).apply {
                text = t
                setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(col(c))
            }

        root.addView(label("Sign in", 26f, bold = true))
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
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) }
        }
        root.addView(go)

        go.setOnClickListener {
            val u = user.text.toString().trim()
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
                    status.text = r.exceptionOrNull()?.message ?: "Sign-in failed."
                    go.isEnabled = true
                }
            }
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
