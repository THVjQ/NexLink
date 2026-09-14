package com.nexlink.social.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nexlink.social.Notifications
import com.nexlink.social.SessionProvider
import com.nexlink.social.core.session.RoomId
import com.nexlink.social.core.rust.RustSocialSession
import com.nexlink.social.call.IncomingCallNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * §13.3 — the device end of the push chain.
 *
 * ```
 * sender → homeserver → Sygnal → FCM → here → sync, decrypt, notify
 * ```
 *
 * ## What arrives, and what does not
 *
 * The pusher is registered with `EVENT_ID_ONLY` (§13.3.1), so the payload
 * carries a room id and an event id and **nothing else**. No sender name, no
 * text — because the homeserver never had them in readable form. That is the
 * design working, not a limitation to route around: anything richer would hand
 * Sygnal and FCM exactly what §2.8 #1 exists to withhold.
 *
 * §13.3.3 is honest about what FCM still learns: *that* a notification was sent
 * to this device, and when. That belongs in the §9.6.1 disclosure.
 *
 * ## §13.3.2's resolution problem
 *
 * The user sees a notification before the app knows what it says. That section
 * gives the order, and the last step is the one that matters:
 *
 * > **If decryption fails** the notification must say something honest and
 * > useful. *Never leave a placeholder that says "Encrypted message"
 * > indefinitely; that is the visible symptom users report as the app being
 * > broken.*
 *
 * So this waits a short bounded time for real content and posts **once**, and
 * posts an honest fallback if the budget expires. One notification, never a
 * placeholder that has to be corrected.
 */
class SocialPushService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * §13.3 — the token changed, so the homeserver's routing is now stale.
     *
     * FCM rotates on its own schedule: reinstall, restore to a new device, or
     * Firebase simply deciding to. A token that is not re-registered means push
     * stops silently, which presents as "messages only arrive when I open the
     * app" — near-undiagnosable from a user report.
     */
    override fun onNewToken(token: String) {
        // §27.5.1 — the token routes to a specific device. Length only.
        Log.i(TAG, "FCM token rotated (len=${token.length}), re-registering")
        scope.launch { PushRegistration.register(applicationContext, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val roomId = message.data["room_id"] ?: run {
            Log.w(TAG, "push with no room_id — ignoring")
            return
        }
        val eventId = message.data["event_id"]

        // FCM allows a short window before the process may be killed, so every
        // path below is bounded and ends in a posted notification.
        runBlocking {
            val mgr = SessionProvider.manager(applicationContext)

            // **A cold push has no session yet, and that is the normal case.**
            //
            // FCM revives a killed process to deliver this, so nothing has run
            // `restore()` — `current()` is null even though the credentials are
            // sitting in EncryptedSharedPreferences. The first version read that
            // as "signed out" and called `unregister`, which would have torn
            // down push permanently on the first cold delivery. It survived only
            // because unregister needs a session too and quietly did nothing.
            //
            // Restoring here is the whole point of the wake-up: §13.3.2's chain
            // is "wake, sync, decrypt, notify", and this is the wake.
            var session = mgr.current()
            if (session == null && mgr.hasStoredSession) {
                runCatching { mgr.restore() }
                session = mgr.current()
            }

            if (session == null) {
                // Genuinely signed out — no stored credentials at all. Post
                // nothing, and do NOT unregister from here: a push handler is
                // the wrong place to make a destructive, hard-to-observe
                // change to server state. Synapse retires a pusher that keeps
                // failing on its own.
                Log.w(TAG, "push with no stored session — ignoring")
                return@runBlocking
            }

            // §15.6 — is this a ring rather than a message?
            //
            // It has to be asked here, after decryption, and it cannot be
            // asked earlier: the ring travels as `m.room.encrypted` like
            // everything else, so neither the homeserver nor the
            // `EVENT_ID_ONLY` payload can tell the two apart. See
            // [RustSocialSession.incomingCall].
            val call = eventId?.let { id ->
                withTimeoutOrNull(RESOLVE_BUDGET_MS) {
                    runCatching {
                        (session as? RustSocialSession)?.incomingCall(RoomId(roomId), id)
                    }.getOrNull()
                }
            }
            if (call != null) {
                IncomingCallNotification.show(applicationContext, call)
                return@runBlocking
            }

            val resolved = withTimeoutOrNull(RESOLVE_BUDGET_MS) {
                runCatching { Resolver.resolve(session, RoomId(roomId), eventId) }.getOrNull()
            }

            Notifications.show(
                context = applicationContext,
                roomId = RoomId(roomId),
                roomTitle = resolved?.roomTitle ?: "NexLink Social",
                senderName = resolved?.senderName ?: "",
                // §13.3.2's honest fallback. "New message" says less than we
                // would like and nothing that is untrue.
                body = resolved?.body ?: "New message",
                timestamp = System.currentTimeMillis(),
                showContent = true
            )
        }
    }

    companion object {
        private const val TAG = "SocialPush"

        /**
         * How long to wait for real content before posting the fallback.
         *
         * §13.3.2 says to post a placeholder only if resolution will take more
         * than about a second. Rather than predicting that, this waits and then
         * posts whichever it has — same outcome, one notification instead of
         * two, and no flicker to correct.
         */
        const val RESOLVE_BUDGET_MS = 2_500L
    }
}
