package com.nexlink.social.push

import com.nexlink.social.core.session.RoomId
import com.nexlink.social.core.session.SocialSession
import com.nexlink.social.core.session.TimelineContent
import kotlinx.coroutines.flow.first

/**
 * §13.3.2 — turning a room id and an event id into something worth reading.
 *
 * The push carries no content (§13.3.1), so this is where the notification's
 * text comes from: the local store and the SDK's decryption, not the server.
 */
object Resolver {

    /**
     * @param senderIsMe §13.4.2 — a push for my own message must not become a
     *   notification. Carried as a flag rather than an id so the caller cannot
     *   get the comparison wrong.
     */
    data class Resolved(
        val roomTitle: String,
        val senderName: String,
        val body: String,
        val senderIsMe: Boolean = false,
    )

    /**
     * @return null when the event could not be resolved or decrypted, which the
     *   caller must treat as §13.3.2's honest-fallback case rather than
     *   retrying indefinitely.
     */
    suspend fun resolve(session: SocialSession, roomId: RoomId, eventId: String?): Resolved? {
        val summary = session.rooms().first().firstOrNull { it.id == roomId }
        val timeline = session.timeline(roomId)
        val items = timeline.items.first()

        // Prefer the exact event the push named. Fall back to the latest, because
        // a push that arrives after the timeline has already advanced is common
        // and notifying about the newest message is still correct.
        val item = items.firstOrNull { it.eventId.value == eventId } ?: items.lastOrNull()
        ?: return null

        val body = when (val c = item.content) {
            is TimelineContent.Text -> c.body
            is TimelineContent.Image -> c.caption ?: "Photo"
            is TimelineContent.Video -> c.caption ?: "Video"
            is TimelineContent.Audio -> "Voice message"
            is TimelineContent.File -> c.displayName
            // §13.3.2 is explicit: a message that will not decrypt must not sit
            // there saying "Encrypted message". Returning null sends the caller
            // to the honest fallback, which names the sender if it can.
            is TimelineContent.Undecryptable -> return Resolved(
                roomTitle = summary?.title ?: "NexLink Social",
                senderName = item.senderDisplayName,
                body = "New message",
                senderIsMe = mine(session, item.sender)
            )
            else -> return null
        }

        return Resolved(
            roomTitle = summary?.title ?: item.senderDisplayName,
            senderName = item.senderDisplayName,
            body = body,
            senderIsMe = mine(session, item.sender)
        )
    }

    private fun mine(
        session: com.nexlink.social.core.session.SocialSession,
        sender: com.nexlink.social.core.session.UserId,
    ): Boolean {
        val me = (session as? com.nexlink.social.core.rust.RustSocialSession)
            ?.currentState()
            ?.let { it as? com.nexlink.social.core.session.SessionState.SignedIn }
            ?.userId?.value
        return me != null && me == sender.value
    }
}
