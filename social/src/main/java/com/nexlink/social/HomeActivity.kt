package com.nexlink.social

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexlink.social.core.session.RoomSummary
import com.nexlink.social.core.session.SessionState
import com.nexlink.social.ui.onboarding.AcceptanceGateActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.nexlink.social.ui.R as UiR

/**
 * The inbox — §14.
 *
 * Phase 3: renders whatever [SessionProvider] gives it, which is now a real
 * Matrix session. The screen itself never learned that: it observes
 * `SessionState` and `rooms()` exactly as it did against the fake, which is the
 * §11.6 seam paying for itself.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private var rooms: List<RoomSummary> = emptyList()
    private var state: SessionState = SessionState.SignedOut
    private var note: CharSequence? = null

    private val gate = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        note = if (r.resultCode == RESULT_OK) {
            // §22.10 — the gate is complete; phase 3's remaining work is to
            // redeem the token against the homeserver's UIA flow and create the
            // account. Until that lands, the gate proves its own contract.
            "Gate passed for invite ${r.data?.getStringExtra(AcceptanceGateActivity.EXTRA_INVITE_CODE)}. " +
                "Account creation is the next piece (§22.10.2)."
        } else "Gate cancelled. No account was created."
        render()
    }

    private val signIn = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        val mgr = SessionProvider.manager(this)

        lifecycleScope.launch { mgr.state.collectLatest { state = it; render(); observeRooms() } }
        lifecycleScope.launch { if (mgr.hasStoredSession) mgr.restore() }
        render()
    }

    private var watching = false
    private fun observeRooms() {
        if (watching) return
        val s = SessionProvider.manager(this).current() ?: return
        watching = true
        lifecycleScope.launch { s.rooms().collectLatest { rooms = it; render() } }
    }

    private fun render() {
        root.removeAllViews()
        root.addView(text("NexLink Social", 26f, bold = true))

        when (val st = state) {
            is SessionState.SignedOut -> {
                root.addView(text("Not signed in", 14f, c = UiR.color.social_muted))
                root.addView(gap(12))
                root.addView(button("Sign in") { signIn.launch(SignInActivity.intent(this)) })
                root.addView(button("I have an invite code") {
                    gate.launch(AcceptanceGateActivity.intent(this))
                })
            }
            is SessionState.Restoring ->
                root.addView(text("Restoring your session…", 14f, c = UiR.color.social_muted))
            is SessionState.Locked ->
                root.addView(text("Locked — unlock the device to read messages", 14f,
                    c = UiR.color.social_muted))
            is SessionState.Failed -> {
                root.addView(text(st.reason, 14f, c = UiR.color.social_danger))
                root.addView(button("Sign in") { signIn.launch(SignInActivity.intent(this)) })
            }
            is SessionState.SignedIn -> {
                root.addView(text(st.userId.value, 14f, c = UiR.color.social_muted))
                // §8.5.2 — backup health is surfaced, not buried in settings. A
                // user whose backup is unhealthy will lose history on their next
                // phone and has no other way to find out.
                if (!st.keyBackupHealthy) {
                    root.addView(text(
                        "Your messages are not backed up. If you lose this phone, " +
                        "your history goes with it.", 14f, c = UiR.color.social_danger))
                }
                root.addView(gap(8))
                root.addView(button("Sign out") {
                    lifecycleScope.launch {
                        SessionProvider.manager(this@HomeActivity).signOut()
                        rooms = emptyList(); watching = false; render()
                    }
                })
                root.addView(gap(12)); root.addView(divider()); root.addView(gap(8))
                if (rooms.isEmpty()) {
                    root.addView(text("No conversations yet.", 14f, c = UiR.color.social_muted))
                } else rooms.forEach { root.addView(row(it)) }
            }
        }

        note?.let { root.addView(gap(12)); root.addView(text(it, 13f, c = UiR.color.social_accent)) }
    }

    private fun row(r: RoomSummary): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        isClickable = true
        setOnClickListener {
            startActivity(ConversationActivity.intent(this@HomeActivity, r.id.value, r.title))
        }
        addView(LinearLayout(this@HomeActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(text(r.title, 16f, bold = true).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            if (r.unreadCount > 0) addView(text("${r.unreadCount}", 14f, c = UiR.color.social_accent))
        })
        val preview = when {
            r.lastMessageUndecryptable ->
                "Can't decrypt — this message was sent before this device"
            r.lastMessagePreview != null -> r.lastMessagePreview!!
            else -> "No messages yet"
        }
        addView(text(preview, 14f,
            c = if (r.lastMessageUndecryptable) UiR.color.social_muted else UiR.color.social_text2))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(v: CharSequence, size: Float, bold: Boolean = false,
                     c: Int = UiR.color.social_text) = TextView(this).apply {
        text = v
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setTextColor(ContextCompat.getColor(this@HomeActivity, c))
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
    }

    private fun gap(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(h))
    }

    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 1)
        setBackgroundColor(ContextCompat.getColor(this@HomeActivity, UiR.color.social_divider))
    }

    private companion object {
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
