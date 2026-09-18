package com.nexlink.social

import android.app.Application
import com.nexlink.social.core.SocialPlatform
import com.nexlink.social.core.SocialSessionManager
import com.nexlink.social.core.session.RoomId
import com.nexlink.social.core.session.RoomSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.nexlink.social.rtc.telecom.SocialConnectionService

/**
 * §11.7.4 — `SocialPlatform.init()` must run before any SDK network call, and
 * `Application.onCreate` is the only place that is reliably true. Skipping it
 * fails every request with an error naming a Rust crate.
 */
class SocialApplication : Application() {

    lateinit var sessions: SocialSessionManager
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Room id → last unread count we notified for. */
    private val seen = mutableMapOf<String, Int>()

    /** Set by ConversationActivity: no notification for the room you are in. */
    @Volatile var openRoomId: String? = null

    override fun onCreate() {
        super.onCreate()
        // §4.7 — the acceptance gate lives in :social-ui and cannot see this
        // activity, so the route is handed to it rather than imported.
        com.nexlink.social.ui.onboarding.AcceptanceGateActivity.policyOpener =
            { ctx, privacy ->
                ctx.startActivity(PolicyActivity.intent(
                    ctx,
                    if (privacy) PolicyActivity.DOC_PRIVACY else PolicyActivity.DOC_TERMS,
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        SocialPlatform.init()
        sessions = SocialSessionManager(this)
        // §19.2 — register the self-managed phone account before any call is
        // placed. Cheap, idempotent, and placing a call against an unregistered
        // account throws — which presents as a call that simply never starts,
        // an unpleasant thing to diagnose after the fact.
        runCatching { SocialConnectionService.register(this) }
        Notifications.ensureChannels(this)
        watchForNewMessages()
    }

    /**
     * §13.4 — turn a rising unread count into a notification.
     *
     * Push (§13.3) is not wired yet, so this only fires while the process is
     * alive. That is a real limitation and not a pretence: §13.3 is the gap.
     * What this does establish is the notification surface itself, which is also
     * what NexLink's unified inbox reads at Level 0 (§16.2).
     */
    private fun watchForNewMessages() {
        scope.launch {
            sessions.state.collectLatest {
                val s = sessions.current() ?: return@collectLatest
                // A new session means a new baseline.
                primed = false
                s.rooms().collectLatest { rooms ->
                    // §13.4.2 — **the first emission is a baseline, not news.**
                    //
                    // `seen` starts empty, so without this every unread room
                    // notifies again on every process start — including the
                    // starts caused by push. From the user's side that is a
                    // notification that will not go away: dismiss it, and the
                    // next launch brings it straight back.
                    if (!primed) {
                        rooms.forEach { seen[it.id.value] = it.unreadCount }
                        primed = true
                        return@collectLatest
                    }
                    rooms.forEach { notifyIfNew(it) }
                }
            }
        }
    }

    /** Whether [seen] holds a baseline yet. See the note in the collector. */
    private var primed = false

    /**
     * The newest message already notified about, per room.
     *
     * Separate from [seen] because they answer different questions: the count
     * says how much is unread, which fluctuates; this says what we have already
     * told the user about, which only ever moves forward.
     */
    private val notifiedAt = mutableMapOf<String, Long>()

    private fun notifyIfNew(room: RoomSummary) {
        val previous = seen[room.id.value] ?: 0
        seen[room.id.value] = room.unreadCount

        // §13.4.2 — nothing for a room the user is looking at, and nothing when
        // the count has not risen. A count that FELL means it was read
        // somewhere, so clear rather than notify.
        if (room.unreadCount <= previous) {
            if (room.unreadCount == 0) {
                Notifications.dismiss(this, room.id, room.title)
                notifiedAt[room.id.value] = room.lastMessageAt
            }
            return
        }
        if (room.isMuted) return
        if (room.id.value == openRoomId) {
            // Reading it counts as having dealt with it. Recording the message
            // here is what stops the *next* emission re-notifying about the
            // very message just read — see below.
            notifiedAt[room.id.value] = room.lastMessageAt
            return
        }
        // §13.4.2 — never for something I sent. Reported plainly: "I get a
        // notification when I send a message, don't want that."
        if (room.lastMessageIsMine) return

        // §13.4.2 — **notify about a MESSAGE, not about a number.**
        //
        // The unread count is not a reliable edge. After `markRead` it drops to
        // zero and a moment later a sync in flight reports the old value again,
        // so the count rises 0 -> 1 with no new message behind it — and the
        // notification the user just cleared by opening the conversation comes
        // straight back. Observed exactly that way: dismissed on open, re-posted
        // seconds later with the same message and a "2" badge.
        //
        // The message timestamp is the honest edge: it only moves when somebody
        // actually said something.
        val at = room.lastMessageAt
        if (at > 0 && at <= (notifiedAt[room.id.value] ?: 0L)) return
        notifiedAt[room.id.value] = at

        Notifications.show(
            context = this,
            roomId = room.id,
            roomTitle = room.title,
            senderName = room.title,
            body = room.lastMessagePreview ?: "New message",
            timestamp = if (room.lastMessageAt > 0) room.lastMessageAt else System.currentTimeMillis(),
            showContent = true
        )
    }
}
