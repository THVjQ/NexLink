package com.nexlink.social

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexlink.social.core.rust.RustSocialSession
import com.nexlink.social.core.rust.RustVerification
import com.nexlink.social.core.session.VerificationStep
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.nexlink.social.ui.R as UiR

/**
 * §8.4 — verify this device against another of your own.
 *
 * §8.3.2 is why this matters and why the copy is worded the way it is: a
 * malicious homeserver's cheapest attack is to add a device to someone's
 * account. Cross-signing means that device shows as unverified everywhere, and
 * the emoji comparison is the step where a human decides whether it is theirs.
 *
 * **The comparison is the security boundary, so the screen must not help the
 * user through it.** No "looks right?" prompt, no pre-selected confirm, and the
 * decline action is not styled as the lesser option. A person who taps
 * "They match" without looking has performed no verification at all, and a UI
 * that nudges them there has quietly removed the only protection in §8.
 */
class VerifyActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private var flow: RustVerification? = null
    private var step: VerificationStep = VerificationStep.Requested
    private var incoming: RustVerification.IncomingRequest? = null
    private var status: String? = null
    private var started = false

    /**
     * True once this device has asked, or has accepted someone else's ask.
     *
     * Needed because [VerificationStep.Requested] is the step for *both* "we
     * have not begun" and "we are mid-handshake, waiting for the emoji". Without
     * it, accepting a request cleared [incoming] and the screen fell back to the
     * opening "Start" prompt — which reads as though the accept did nothing, and
     * invites the user to press Start and collide with the flow they just
     * joined (see the collision note on `RustVerification.request`).
     */
    private var engaged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        root.padForSystemBars(dp(20), dp(24), dp(24))
        render()
        attach()
    }

    private fun session(): RustSocialSession? =
        SessionProvider.manager(this).current() as? RustSocialSession

    private fun attach() {
        lifecycleScope.launch {
            val s = session() ?: run { status = "Not signed in"; render(); return@launch }
            val v = runCatching { s.startVerification() }
                .getOrElse { status = "Couldn't start: ${it.message}"; render(); return@launch }
            flow = v
            launch { v.steps.collectLatest { step = it; render() } }
            launch { v.incoming.collectLatest { incoming = it; render() } }
        }
    }

    private fun render() {
        root.removeAllViews()
        root.addView(title("Verify this device"))

        when (val st = step) {
            is VerificationStep.ShowEmoji -> renderEmoji(st)
            is VerificationStep.Verified -> {
                root.addView(body(
                    "Verified. Both devices now trust each other, and this one can " +
                    "read your history."))
                root.addView(button("Done") { finish() })
            }
            is VerificationStep.Cancelled -> {
                root.addView(body(
                    "Verification did not complete (${st.reason}).\n\n" +
                    "If you did not cancel this yourself, do not retry — an " +
                    "unexpected verification request is exactly what §8.3.2 " +
                    "warns about. Check your device list first."))
                root.addView(button("Your devices") {
                    startActivity(DevicesActivity.intent(this)); finish()
                })
            }
            else -> renderIdle()
        }
        status?.let { root.addView(body(it)) }
    }

    private fun renderIdle() {
        val req = incoming
        if (req != null) {
            // §8.3.2 — this device is being asked to vouch for another. Show
            // everything known about the asker and make refusing the easy path.
            root.addView(body(
                "Another device is asking to be verified:\n\n" +
                "  ${req.deviceDisplayName ?: "Unnamed device"}\n" +
                "  ${req.deviceId}\n\n" +
                "If this is not you, on a device you are holding right now, " +
                "refuse it and change your password."))
            root.addView(button("It's me — continue") {
                engaged = true; render()
                lifecycleScope.launch {
                    runCatching { flow?.accept() }
                        .onFailure {
                            engaged = false
                            status = "Couldn't accept: ${it.message}"; render()
                        }
                }
            })
            root.addView(button("Refuse") {
                lifecycleScope.launch { flow?.cancel(); finish() }
            })
            return
        }

        if (engaged) {
            root.addView(body(
                "Connecting to your other device…\n\n" +
                "Both screens will show the same row of emoji in a moment. Keep " +
                "this screen open — if you leave it, the other device is left " +
                "waiting."))
            root.addView(button("Cancel") {
                lifecycleScope.launch { flow?.cancel(); finish() }
            })
            return
        }

        root.addView(body(
            "Open NexLink Social on your other device and choose to verify. " +
            "Both screens will then show the same row of emoji, and you compare " +
            "them.\n\n" +
            "If they don't match, someone else is trying to add a device to " +
            "your account."))
        root.addView(button(if (started) "Waiting for your other device…" else "Start") {
            if (started) return@button
            started = true; engaged = true; render()
            lifecycleScope.launch {
                runCatching { flow?.request() }
                    .onFailure { status = "Couldn't send the request: ${it.message}"; started = false; render() }
            }
        })
    }

    private fun renderEmoji(st: VerificationStep.ShowEmoji) {
        root.addView(body(
            "Check that your other device is showing exactly these, in this order."))
        root.addView(emojiGrid(st.emoji))
        root.addView(body(
            "Only confirm if every one matches. If any differ, or your other " +
            "device shows nothing, refuse."))
        root.addView(button("They match") {
            lifecycleScope.launch {
                status = "Confirming…"; render()
                runCatching { flow?.confirmMatch() }
                    .onFailure { status = "Couldn't confirm: ${it.message}"; render() }
            }
        })
        root.addView(button("They don't match") {
            lifecycleScope.launch { flow?.declineMatch(); finish() }
        })
    }

    /**
     * Two rows of emoji with their names underneath.
     *
     * The name is shown because the symbols are small, several look alike at a
     * glance ("cat" and "lion"), and the two people are often reading them to
     * each other rather than looking at both screens.
     */
    private fun emojiGrid(emoji: List<Pair<String, String>>): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            emoji.chunked(4).forEach { rowItems ->
                addView(LinearLayout(this@VerifyActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    rowItems.forEach { (symbol, name) ->
                        addView(LinearLayout(this@VerifyActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                            setPadding(dp(4), dp(8), dp(4), dp(8))
                            addView(TextView(this@VerifyActivity).apply {
                                // §14.4.3 — an opaque grapheme. Never indexed
                                // into, never truncated.
                                text = symbol
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
                                gravity = Gravity.CENTER
                            })
                            addView(TextView(this@VerifyActivity).apply {
                                text = name
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                                gravity = Gravity.CENTER
                                setTextColor(ContextCompat.getColor(
                                    this@VerifyActivity, UiR.color.social_muted))
                            })
                            contentDescription = if (name.isBlank()) symbol else "$symbol, $name"
                        })
                    }
                })
            }
        }

    private fun title(t: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setTypeface(null, Typeface.BOLD)
        setTextColor(ContextCompat.getColor(this@VerifyActivity, UiR.color.social_text))
        setPadding(0, 0, 0, dp(12))
    }

    private fun body(t: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(ContextCompat.getColor(this@VerifyActivity, UiR.color.social_text2))
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun button(t: String, onTap: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        minHeight = dp(48)   // §14.10
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        setOnClickListener { onTap() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun intent(c: Context) = Intent(c, VerifyActivity::class.java)
    }
}
