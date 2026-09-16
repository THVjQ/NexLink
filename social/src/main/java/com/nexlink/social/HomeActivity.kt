package com.nexlink.social

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.nexlink.social.core.DeviceLock
import com.nexlink.social.core.session.RoomSummary
import com.nexlink.social.core.session.SessionState
import com.nexlink.social.push.PushRegistration
import com.nexlink.social.ui.chrome.Chrome
import com.nexlink.social.ui.chrome.Icon
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.nexlink.social.ui.R as UiR

/**
 * The inbox — §14.
 *
 * **Rebuilt 2026-09-15 to look like a messenger.** What was here before worked
 * and was unusable: a column of eight identical full-width buttons — Sign out,
 * Search, Export, Devices, Verify, Blocked, Storage, New conversation — sat
 * above the conversations, so on a phone the actual messages started below the
 * fold, under two red paragraphs of warning. Every one of those controls has to
 * exist; none of them is what the screen is *for*.
 *
 * So the screen is now the conversation list, and everything else moved to
 * where its frequency says it belongs: search and the overflow into the title
 * bar, a new conversation onto a floating button, the two standing warnings
 * into compact cards, and the remaining six settings into the overflow menu.
 * **Sign out is last in that menu and now asks** — it is the one item there
 * that can cost you your history, and it used to sit at the top of the screen
 * one tap from Search.
 *
 * The data side is unchanged: it observes `SessionState` and `rooms()` exactly
 * as it did against the fake session, which is the §11.6 seam still paying for
 * itself — a full visual rewrite touched no session code at all.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var chrome: Chrome
    private lateinit var page: Chrome.Page
    private lateinit var newChat: View
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
        chrome = Chrome(this)
        page = chrome.page(
            title = "NexLink Social",
            actions = listOf(
                Chrome.Action(Icon.Kind.SEARCH, "Search messages") {
                    startActivity(SearchActivity.intent(this))
                },
                Chrome.Action(Icon.Kind.MORE, "More") { anchor -> showOverflow(anchor) },
            ),
        )
        // §14.10 — the floating button is lifted clear of the gesture bar by
        // Chrome.Page, which is the inset bug that made the old last row
        // untappable rather than merely low.
        newChat = chrome.fab(Icon.Kind.NEW_CHAT, "New conversation") {
            startActivity(NewChatActivity.intent(this))
        }
        page.float(newChat)
        setContentView(page.root)

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
            val granted = ContextCompat.checkSelfPermission(
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

    // ── the screen ──────────────────────────────────────────────────────────

    private fun render() {
        val content = page.content
        content.removeAllViews()
        val signedIn = state as? SessionState.SignedIn
        // The floating "new conversation" button only means anything when there
        // is a session to start one in.
        newChat.visibility = if (signedIn != null) View.VISIBLE else View.GONE
        page.subtitle(signedIn?.userId?.value)

        note?.let { content.addView(noticeLine(it)) }

        when (val st = state) {
            is SessionState.SignedOut -> content.addView(welcome())
            is SessionState.Restoring -> content.addView(
                chrome.emptyState("Opening your messages…", "This takes a moment on a cold start.")
            )
            is SessionState.Locked -> content.addView(
                chrome.emptyState(
                    "Locked",
                    "Unlock the device to read your messages. They stay encrypted until you do."
                )
            )
            is SessionState.Failed -> {
                content.addView(
                    chrome.emptyState("Couldn't open your account", st.reason)
                )
                content.addView(actionRow(chrome.filledButton("Sign in") {
                    signIn.launch(SignInActivity.intent(this))
                }))
            }
            is SessionState.SignedIn -> renderInbox(content, st)
        }
    }

    /** The signed-out screen: two doors, and nothing else to read. */
    private fun welcome(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(28), dp(56), dp(28), dp(24))
        addView(TextView(this@HomeActivity).apply {
            text = "NexLink Social"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(colour(UiR.color.social_text))
        })
        addView(TextView(this@HomeActivity).apply {
            text = "Private messages and calls, end to end encrypted. " +
                "Invite only — nobody can add you without a code."
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(colour(UiR.color.social_muted))
            setLineSpacing(dp(5).toFloat(), 1f)
            setPadding(0, dp(10), 0, dp(34))
        })
        addView(chrome.filledButton("I have an invite code") {
            gate.launch(CreateAccountActivity.intent(this@HomeActivity))
        }.apply { layoutParams = wide() })
        addView(TextView(this@HomeActivity).apply {
            text = "Already have an account?"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(colour(UiR.color.social_muted))
            setPadding(0, dp(22), 0, dp(8))
        })
        addView(chrome.quietButton("Sign in") {
            signIn.launch(SignInActivity.intent(this@HomeActivity))
        }.apply { layoutParams = wide() })
    }

    private fun renderInbox(content: LinearLayout, st: SessionState.SignedIn) {
        // §8.5.2 — backup health is surfaced, not buried in settings. A user
        // whose backup is unhealthy will lose history on their next phone and
        // has no other way to find out.
        if (!st.keyBackupHealthy) {
            content.addView(chrome.banner(
                headline = "Your messages aren't backed up",
                body = "If you lose this phone, your history goes with it. " +
                    "Setting up recovery takes a minute.",
                actionLabel = "Set up recovery",
            ) { startActivity(RecoverySetupActivity.intent(this)) })
        }
        // §12.4.4 — the other half of the bargain that section makes.
        //
        // The store key carries no authentication requirement, because demanding
        // one would stop push-woken sync whenever the phone is locked. §12.4.4
        // accepts that *on condition* the app warns when there is no screen
        // lock — without the warning it is not a trade, just the weaker half.
        DeviceLock.warning(this)?.let { w ->
            content.addView(chrome.banner(
                headline = "This phone has no screen lock",
                body = w,
                actionLabel = "Open security settings",
            ) { startActivity(DeviceLock.settingsIntent()) })
        }

        // §14.8 — invitations first, and visually separate. An invitation is a
        // decision the user has to make, not a conversation they are already in.
        val (invites, joined) = rooms.partition { it.isInvite }
        if (invites.isNotEmpty()) {
            content.addView(chrome.sectionHeader(
                if (invites.size == 1) "INVITATION" else "INVITATIONS"
            ))
            val card = chrome.card()
            invites.forEachIndexed { i, r ->
                if (i > 0) card.addView(chrome.rowDivider(insetStartDp = 14))
                card.addView(inviteRow(r))
            }
            content.addView(card)
        }

        if (joined.isEmpty()) {
            content.addView(chrome.emptyState(
                "No conversations yet",
                "Tap the pencil to start one. You'll need the other person's username — " +
                    "there is no directory to browse and no way to look someone up by " +
                    "phone number. That's deliberate."
            ))
            return
        }

        if (invites.isNotEmpty()) content.addView(chrome.sectionHeader("CONVERSATIONS"))
        else content.addView(chrome.spacer(4))
        val card = chrome.card()
        joined.forEachIndexed { i, r ->
            if (i > 0) card.addView(chrome.rowDivider())
            card.addView(conversationRow(r))
        }
        content.addView(card)
    }

    /**
     * One conversation.
     *
     * Avatar, name, time, preview, unread count — the five things every
     * messenger puts in this row, in the order everyone reads them. The
     * previous version had the name and the preview stacked with no avatar and
     * no visual anchor, so a list of six conversations was six indistinguishable
     * paragraphs.
     */
    private fun conversationRow(r: RoomSummary): View {
        val row = chrome.row(
            onClick = {
                startActivity(ConversationActivity.intent(this, r.id.value, r.title))
            },
            // §31.3.1 — "one tap from a message, from a profile, and from the
            // conversation list". This is the conversation-list one: long-press
            // gives block and leave without opening the conversation, which
            // matters when opening it is the thing you do not want to do.
            onLongClick = { showRoomSafetyMenu(r); true },
        )
        // §14.10 — the row is one target made of several TextViews, so a screen
        // reader would otherwise read the pieces separately and never say the
        // whole thing is tappable. The unread count is the part most easily
        // lost: it is a small coloured number that carries real meaning.
        row.contentDescription = buildString {
            append(r.title)
            if (r.unreadCount > 0) {
                append(", ")
                append(if (r.unreadCount == 1) "1 unread message"
                       else "${r.unreadCount} unread messages")
            }
            r.lastMessagePreview?.takeIf { it.isNotBlank() }?.let { append(". Latest: $it") }
        }
        row.addView(chrome.avatar(r.title))

        val unread = r.unreadCount > 0
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                marginStart = dp(12)
            }
        }
        column.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(r.title, 16f, bold = true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            if (r.lastMessageAt > 0) addView(text(
                chrome.relativeTime(r.lastMessageAt), 12f,
                c = if (unread) UiR.color.social_accent else UiR.color.social_muted,
            ).apply { setPadding(dp(8), 0, 0, 0) })
        })
        val preview = when {
            r.lastMessageUndecryptable -> "Can't decrypt — sent before this device"
            r.lastMessagePreview != null -> r.lastMessagePreview!!
            else -> "No messages yet"
        }
        column.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, 0)
            addView(text(preview, 14f,
                c = if (r.lastMessageUndecryptable) UiR.color.social_muted
                    else UiR.color.social_text2
            ).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                if (unread) setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            if (unread) addView(chrome.badge(r.unreadCount).apply {
                (layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(WRAP, WRAP))
                    .also { it.marginStart = dp(8); layoutParams = it }
            })
        })
        row.addView(column)
        return row
    }

    /**
     * §14.8 — an invitation. Accept and Decline are given equal weight on
     * purpose: being added to a conversation you did not ask for is exactly the
     * case where declining must be as easy as accepting. "Equal weight" is why
     * Decline is an outlined button of the same height rather than a text link.
     */
    private fun inviteRow(r: RoomSummary): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        addView(LinearLayout(this@HomeActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(chrome.avatar(r.title, sizeDp = 40))
            addView(LinearLayout(this@HomeActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                    marginStart = dp(12)
                }
                addView(text(r.title, 16f, bold = true))
                addView(text(
                    r.invitedBy?.let { "Invited by $it" } ?: "You have been invited",
                    13f, c = UiR.color.social_muted))
            })
        })
        addView(LinearLayout(this@HomeActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
            addView(chrome.filledButton("Accept") { respondToInvite(r, accept = true) }
                .apply { layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) })
            addView(chrome.quietButton("Decline") { respondToInvite(r, accept = false) }
                .apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                        .also { it.marginStart = dp(10) }
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

    // ── everything that is not a conversation ───────────────────────────────

    /**
     * The settings that used to be eight buttons across the top of the inbox.
     *
     * Ordered by how often a person opens them, with the two that are about
     * *this device's* safety (verify, devices) above the two that are about
     * data (export, storage). Sign out is last and separated by being last,
     * because it is the only one that can lose something.
     */
    private fun showOverflow(anchor: View) {
        val signedIn = state is SessionState.SignedIn
        val items = buildList<Pair<String, () -> Unit>> {
            if (!signedIn) {
                add("Sign in" to { signIn.launch(SignInActivity.intent(this@HomeActivity)) })
                return@buildList
            }
            // §9.5 — first, because inviting someone is the only item here a
            // user has a reason to open twice.
            add("Invite someone" to { startActivity(InviteActivity.intent(this@HomeActivity)) })
            add("Verify this device" to { startActivity(VerifyActivity.intent(this@HomeActivity)) })
            // §7.4 — reachable ALWAYS, not only through the warning banner.
            // The banner disappears the moment recovery is set up, which left
            // the only route to your recovery key behind a condition that is
            // false exactly when you have a key to worry about.
            add("Recovery key" to { startActivity(RecoverySetupActivity.intent(this@HomeActivity)) })
            add("Your devices" to { startActivity(DevicesActivity.intent(this@HomeActivity)) })
            add("Blocked people" to { startActivity(BlockedActivity.intent(this@HomeActivity)) })
            add("Export my messages" to { startActivity(ExportActivity.intent(this@HomeActivity)) })
            add("Storage" to { startActivity(StorageActivity.intent(this@HomeActivity)) })
            // The same link NexLink carries in its settings (§10.5 — two
            // products, one person paying for both). Above Sign out, because
            // Sign out stays last.
            add("Buy me a coffee" to { openCoffee() })
            add("Sign out" to { confirmSignOut() })
        }
        chrome.menu(anchor, items)
    }

    /**
     * §10.5 — the same link NexLink's settings screen uses.
     *
     * `resolveActivity` is not consulted first: a phone with no browser at all
     * is vanishingly rare, and `ActivityNotFoundException` is the honest way to
     * find out rather than hiding the row on a guess.
     */
    private fun openCoffee() {
        val i = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(COFFEE_URL)
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(i) }.onFailure {
            Snackbar.make(findViewById(android.R.id.content),
                "No app on this phone can open a link", Snackbar.LENGTH_LONG).show()
        }
    }

    /**
     * Signing out is not reversible from inside the app: without a recovery key
     * the message history on this device is gone with the store. It used to be
     * the first full-width button on the screen, one slip away from Search.
     */
    private fun confirmSignOut() {
        val healthy = (state as? SessionState.SignedIn)?.keyBackupHealthy == true
        AlertDialog.Builder(this)
            .setTitle("Sign out?")
            .setMessage(
                if (healthy)
                    "This device's copy of your messages is deleted. You can restore " +
                        "your history with your recovery key when you sign back in."
                else
                    "This device's copy of your messages is deleted, and recovery is " +
                        "NOT set up — your history cannot be restored. Set up recovery " +
                        "first if you want to keep it."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Sign out") { _, _ ->
                lifecycleScope.launch {
                    // §32.3 — stop push BEFORE the session goes, while the
                    // access token still exists. Signing out first leaves the
                    // pusher registered and the homeserver notifying a device
                    // that can no longer read anything.
                    runCatching { PushRegistration.unregister(this@HomeActivity) }
                    SessionProvider.manager(this@HomeActivity).signOut()
                    rooms = emptyList(); watching = false; pushRegistered = false; render()
                }
            }
            .show()
    }

    /**
     * §31.3.1 — block or leave, straight from the list.
     *
     * Blocking is offered only for a one-to-one conversation. In a group
     * "block the other person" has no single referent, and an option that
     * silently picks the wrong one is worse than an option that is absent.
     *
     * The other member is resolved when the action is taken rather than while
     * building the menu, so opening the menu costs no network call — this is a
     * long-press on a list someone may be scrolling.
     */
    private fun showRoomSafetyMenu(r: RoomSummary) {
        val actions = buildList {
            if (!r.isGroup) add("Block ${r.title}")
            add("Leave conversation")
        }
        AlertDialog.Builder(this)
            .setTitle(r.title)
            .setItems(actions.toTypedArray()) { _, i ->
                val chosen = actions[i]
                lifecycleScope.launch {
                    val s = SessionProvider.manager(this@HomeActivity).current()
                        ?: return@launch
                    if (chosen == "Leave conversation") {
                        s.leaveRoom(r.id)
                        return@launch
                    }
                    // isSelf rather than comparing ids: the summary already
                    // knows, and a mismatched-id comparison would silently pick
                    // the wrong person to block.
                    val other = s.members(r.id).getOrNull()
                        ?.firstOrNull { !it.isSelf }?.id
                    if (other == null) {
                        Snackbar.make(findViewById(android.R.id.content),
                            "Couldn't work out who to block", Snackbar.LENGTH_LONG).show()
                        return@launch
                    }
                    s.blockUser(other).onSuccess {
                        Snackbar.make(findViewById(android.R.id.content),
                            "Blocked ${r.title}", Snackbar.LENGTH_LONG)
                            .setAction("Undo") {
                                lifecycleScope.launch { s.unblockUser(other) }
                            }.show()
                    }.onFailure {
                        Snackbar.make(findViewById(android.R.id.content),
                            "Couldn't block: ${it.message ?: "unknown"}",
                            Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── small pieces ────────────────────────────────────────────────────────

    private fun noticeLine(t: CharSequence): View = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(colour(UiR.color.social_accent))
        setPadding(dp(26), dp(4), dp(26), dp(8))
    }

    private fun actionRow(v: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(28), 0, dp(28), 0)
        addView(v, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun wide() = LinearLayout.LayoutParams(MATCH, WRAP)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun colour(id: Int) = ContextCompat.getColor(this, id)

    private fun text(v: CharSequence, size: Float, bold: Boolean = false,
                     c: Int = UiR.color.social_text) = TextView(this).apply {
        text = v
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(colour(c))
    }

    private companion object {
        /** Kept identical to :app's SettingsFragment. */
        const val COFFEE_URL = "https://buymeacoffee.com/THVjQ"
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
