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
import com.nexlink.social.core.DeviceLock
import com.nexlink.social.core.session.RoomSummary
import com.nexlink.social.core.session.SessionState
import com.nexlink.social.ui.onboarding.AcceptanceGateActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.nexlink.social.ui.R as UiR
import com.nexlink.social.push.PushRegistration

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
        note = if (r.resultCode == RESULT_OK) null
        else "No account was created."
        render()
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* §13.8 — declining is fine; the app works, it is just quieter. */ }

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
        // §14.10 — see Insets.kt.
        root.padForSystemBars(dp(20), dp(24), dp(24))

        val mgr = SessionProvider.manager(this)

        lifecycleScope.launch {
            mgr.state.collectLatest {
                state = it
                render()
                observeRooms()
                // §13.3 — register push when the session BECOMES signed in.
                //
                // The first version did this once in onCreate, which is wrong
                // in the commonest case: HomeActivity is created before the
                // user signs in, so the call ran against no session and never
                // ran again. The symptom was silent — the app worked, and push
                // simply never arrived.
                if (it is SessionState.SignedIn) ensurePushRegistered()
            }
        }
        lifecycleScope.launch {
            if (mgr.hasStoredSession) mgr.restore()
            // §12.5.2 — the media-cache limit lives in the SDK's store, and a
            // client that has just been constructed starts from the SDK's
            // default, not the user's choice. Re-applying on every session
            // start is what makes the setting stick across restarts; setting it
            // only when the user changes it would silently revert.
            mgr.current()?.applyMediaRetention(StoragePrefs.retention(this@HomeActivity))

        }

        // §13.4 — Android 13+ requires this at runtime. Asked for once, here,
        // because a messenger that cannot notify is not much of one.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        render()
    }

    private var watching = false
    private fun observeRooms() {
        if (watching) return
        val s = SessionProvider.manager(this).current() ?: return
        watching = true
        lifecycleScope.launch { s.rooms().collectLatest { rooms = it; render() } }
    }

    /** Guard so a re-emitted SignedIn state does not re-register on every render. */
    private var pushRegistered = false

    /**
     * §13.3 — idempotent by design.
     *
     * `append = false` replaces the pusher for this pushkey rather than
     * accumulating duplicates, so calling this more than once is harmless. The
     * guard exists only to avoid a network call per state emission.
     */
    private fun ensurePushRegistered() {
        if (pushRegistered) return
        pushRegistered = true
        lifecycleScope.launch {
            val ok = runCatching { PushRegistration.register(this@HomeActivity) }
                .getOrDefault(false)
            // Allow a retry on the next state change if it did not take —
            // otherwise a transient failure disables push for the process
            // lifetime and nothing ever says so.
            if (!ok) pushRegistered = false
        }
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
                    gate.launch(CreateAccountActivity.intent(this))
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
                    root.addView(button("Set up recovery") {
                        startActivity(RecoverySetupActivity.intent(this))
                    })
                }
                // §12.4.4 — the other half of the bargain that section makes.
                //
                // The store key carries no authentication requirement, because
                // demanding one would stop push-woken sync whenever the phone is
                // locked. §12.4.4 accepts that *on condition* the app warns when
                // there is no screen lock — without the warning it is not a
                // trade, just the weaker half.
                //
                // Here rather than in settings, for §8.5.2's reason: someone in
                // this position has no other way to find out.
                DeviceLock.warning(this)?.let { w ->
                    root.addView(text(w, 14f, c = UiR.color.social_danger))
                    root.addView(button("Open security settings") {
                        startActivity(DeviceLock.settingsIntent())
                    })
                }
                root.addView(gap(8))
                root.addView(button("Sign out") {
                    lifecycleScope.launch {
                        // §32.3 — stop push BEFORE the session goes, while the
                        // access token still exists. Signing out first leaves
                        // the pusher registered and the homeserver notifying a
                        // device that can no longer read anything.
                        runCatching { PushRegistration.unregister(this@HomeActivity) }
                        SessionProvider.manager(this@HomeActivity).signOut()
                        rooms = emptyList(); watching = false; render()
                    }
                })
                root.addView(button("Search") { startActivity(SearchActivity.intent(this)) })
                root.addView(button("Export my messages") {
                    startActivity(ExportActivity.intent(this))
                })
                root.addView(button("Your devices") {
                    startActivity(DevicesActivity.intent(this))
                })
                root.addView(button("Verify this device") {
                    startActivity(VerifyActivity.intent(this))
                })
                root.addView(button("Storage") {
                    startActivity(StorageActivity.intent(this))
                })
                root.addView(button("New conversation") {
                    startActivity(NewChatActivity.intent(this))
                })
                root.addView(gap(12)); root.addView(divider()); root.addView(gap(8))
                // §14.8 — invitations first, and visually separate. An
                // invitation is a decision the user has to make, not a
                // conversation they are already in.
                val (invites, joined) = rooms.partition { it.isInvite }
                if (invites.isNotEmpty()) {
                    root.addView(text("Invitations", 14f, bold = true, c = UiR.color.social_accent))
                    invites.forEach { root.addView(inviteRow(it)) }
                    root.addView(gap(8)); root.addView(divider()); root.addView(gap(8))
                }
                if (joined.isEmpty()) {
                    root.addView(text("No conversations yet.", 14f, c = UiR.color.social_muted))
                } else joined.forEach { root.addView(row(it)) }
            }
        }

        note?.let { root.addView(gap(12)); root.addView(text(it, 13f, c = UiR.color.social_accent)) }
    }

    /**
     * §14.8 — an invitation. Accept and Decline are given equal weight on
     * purpose: being added to a conversation you did not ask for is exactly the
     * case where declining must be as easy as accepting.
     */
    private fun inviteRow(r: RoomSummary): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        addView(text(r.title, 16f, bold = true))
        addView(text(
            r.invitedBy?.let { "Invited by $it" } ?: "You have been invited",
            13f, c = UiR.color.social_muted))
        addView(LinearLayout(this@HomeActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@HomeActivity).apply {
                text = "Accept"; isAllCaps = false
                setOnClickListener { respondToInvite(r, accept = true) }
            })
            addView(Button(this@HomeActivity).apply {
                text = "Decline"; isAllCaps = false
                setOnClickListener { respondToInvite(r, accept = false) }
            })
        })
    }

    private fun respondToInvite(r: RoomSummary, accept: Boolean) {
        note = if (accept) "Joining…" else "Declining…"
        render()
        lifecycleScope.launch {
            val s = SessionProvider.manager(this@HomeActivity).current() ?: return@launch
            val result = if (accept) s.acceptInvite(r.id) else s.leaveRoom(r.id)
            note = result.fold(
                onSuccess = { null },
                onFailure = { "Couldn't ${if (accept) "join" else "decline"}: ${it.message}" }
            )
            render()
        }
    }

    private fun row(r: RoomSummary): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        isClickable = true
        // §14.10 — the row is one target made of several TextViews, so a screen
        // reader would otherwise read the pieces separately and never say the
        // whole thing is tappable. The unread count is the part most easily
        // lost: it is a small coloured number that carries real meaning.
        contentDescription = buildString {
            append(r.title)
            if (r.unreadCount > 0) {
                append(", ")
                append(if (r.unreadCount == 1) "1 unread message"
                       else "${r.unreadCount} unread messages")
            }
            r.lastMessagePreview?.takeIf { it.isNotBlank() }?.let { append(". Latest: $it") }
        }
        setOnClickListener {
            startActivity(ConversationActivity.intent(this@HomeActivity, r.id.value, r.title))
        }
        addView(LinearLayout(this@HomeActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(text(r.title, 16f, bold = true).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            if (r.unreadCount > 0) addView(text("${r.unreadCount}", 14f, c = UiR.color.social_accent))
            else if (r.lastMessageAt > 0) addView(text(relativeTime(r.lastMessageAt), 12f,
                c = UiR.color.social_muted))
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

    /** §14.1 — an inbox that shows a full timestamp on every row is unreadable. */
    private fun relativeTime(ts: Long): String {
        val mins = (System.currentTimeMillis() - ts) / 60_000
        return when {
            mins < 1 -> "now"
            mins < 60 -> "${mins}m"
            mins < 60 * 24 -> "${mins / 60}h"
            mins < 60 * 24 * 7 -> "${mins / (60 * 24)}d"
            else -> android.text.format.DateFormat.getDateFormat(this).format(java.util.Date(ts))
        }
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
