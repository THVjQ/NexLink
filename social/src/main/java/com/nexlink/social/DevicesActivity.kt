package com.nexlink.social

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nexlink.social.ui.chrome.Chrome
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexlink.social.core.rust.RustSocialSession
import com.nexlink.social.core.session.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import com.nexlink.social.ui.R as UiR

/**
 * Your devices — §8.6.
 *
 * §8.3.2 is the reason this screen exists at all. A compromised or malicious
 * homeserver's cheapest attack is to quietly add a device to someone's account.
 * Cross-signing means that device shows as unverified; **this list is the only
 * place a person can ever see it.** A device-management screen nobody can find
 * is the same as not having one.
 *
 * §30.4.1 bounds what removal achieves, and the screen says so: it stops future
 * access, not past. Messages already on that device stay readable if someone can
 * unlock it. Pretending otherwise would be the kind of overclaiming §9.6.1
 * forbids everywhere else in this product.
 */
class DevicesActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var chrome: Chrome
    private var devices: List<DeviceInfo> = emptyList()
    private var status: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // §14 — the same title bar as every other screen, from :social-ui's
        // Chrome. It also takes the system-bar insets that padForSystemBars
        // used to take here, so a screen's last row still clears the gesture
        // bar (§14.10) — that is the one thing this replacement must not lose.
        chrome = Chrome(this)
        val page = chrome.page("Your devices", onBack = { finish() })
        root = page.content
        setContentView(page.root)
        render()
        refresh()
    }

    private fun session(): RustSocialSession? =
        SessionProvider.manager(this).current() as? RustSocialSession

    private fun refresh() {
        status = "Loading…"; render()
        lifecycleScope.launch {
            val s = session() ?: run { status = "Not signed in"; render(); return@launch }
            val r = withContext(Dispatchers.IO) { s.refreshDevices() }
            r.onFailure { status = "Couldn't load devices: ${it.message}"; render() }
            s.devices().collect {
                devices = it
                if (r.isSuccess) status = if (it.isEmpty()) "No devices returned." else null
                render()
            }
        }
    }

    private fun render() {
        root.removeAllViews()
        root.addView(chrome.note(
            "Every place you're signed in. If you see something here you don't " +
            "recognise, remove it and change your password."))
        status?.let { root.addView(chrome.note(it).apply {
            setTextColor(ContextCompat.getColor(this@DevicesActivity, UiR.color.social_accent))
        }) }
        if (devices.isEmpty()) return
        val card = chrome.card()
        devices.forEachIndexed { i, d ->
            if (i > 0) card.addView(chrome.rowDivider(insetStartDp = 16))
            card.addView(row(d))
        }
        root.addView(card)
    }

    /**
     * One signed-in device.
     *
     * **Remove is a trailing text action, not a full-width button.** It used to
     * be the latter, which gave a list of five devices five large blue slabs —
     * the destructive action was the loudest thing on a screen whose job is to
     * let you *read* the list and spot the one you do not recognise.
     */
    private fun row(d: DeviceInfo): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(8), dp(12))

        addView(LinearLayout(this@DevicesActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            val name = d.displayName ?: "Unnamed device"
            addView(text(name, 16f, UiR.color.social_text, bold = true))
            if (d.isCurrent) addView(TextView(this@DevicesActivity).apply {
                text = "This device"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(
                    this@DevicesActivity, UiR.color.social_on_accent_fill))
                setPadding(dp(8), dp(2), dp(8), dp(3))
                background = chrome.rounded(ContextCompat.getColor(
                    this@DevicesActivity, UiR.color.social_accent_fill), 9f)
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                    .apply { topMargin = dp(4) }
            })
            addView(text(d.id.value, 12.5f, UiR.color.social_muted))
            d.lastSeenAt?.let {
                val when_ = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(it))
                val ip = d.lastSeenIp?.let { i -> " · $i" } ?: ""
                addView(text("Last used $when_$ip", 12.5f, UiR.color.social_muted))
            }
        })

        if (!d.isCurrent) {
            addView(TextView(this@DevicesActivity).apply {
                text = "Remove"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(
                    this@DevicesActivity, UiR.color.social_danger))
                setPadding(dp(14), dp(10), dp(14), dp(10))
                minHeight = dp(48)      // §14.10
                minWidth = dp(48)
                contentDescription = "Remove ${d.displayName ?: "this device"}"
                background = chrome.ripple(chrome.rounded(ContextCompat.getColor(
                    this@DevicesActivity, UiR.color.social_surface2), 12f))
                setOnClickListener { confirmRemove(d) }
            })
        }
    }

    private fun confirmRemove(d: DeviceInfo) {
        val pw = EditText(this).apply {
            hint = "Your password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Remove ${d.displayName ?: d.id.value}?")
            // §30.4.1 — say exactly what this does and does not do.
            .setMessage(
                "That device will be signed out and won't receive new messages.\n\n" +
                "Messages it already downloaded stay on it. If the device is lost " +
                "or stolen, change your password as well."
            )
            .setView(pw)
            .setPositiveButton("Remove") { _, _ ->
                val password = pw.text.toString()
                status = "Removing…"; render()
                lifecycleScope.launch {
                    val s = session() ?: return@launch
                    val user = s.currentState().let {
                        (it as? com.nexlink.social.core.session.SessionState.SignedIn)
                            ?.userId?.value?.substringAfter('@')?.substringBefore(':')
                    } ?: return@launch
                    withContext(Dispatchers.IO) { s.deviceManager().delete(d.id, user, password) }
                        .onSuccess { status = "Removed."; refresh() }
                        .onFailure { status = it.message ?: "Could not remove."; render() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun gap(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(h))
    }
    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 1)
        setBackgroundColor(ContextCompat.getColor(this@DevicesActivity, UiR.color.social_divider))
    }
    private fun text(v: CharSequence, size: Float, c: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = v
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@DevicesActivity, c))
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    companion object {
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        fun intent(c: Context) = Intent(c, DevicesActivity::class.java)
    }
}
