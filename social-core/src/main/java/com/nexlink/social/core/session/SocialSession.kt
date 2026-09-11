package com.nexlink.social.core.session

import kotlinx.coroutines.flow.Flow

/**
 * The seam — docs/social/11-sdk-selection.md §11.6.
 *
 * `:social-ui` depends on this, never on the SDK. The SDK is an implementation
 * detail of `:social-core`.
 *
 * **What this buys:** an SDK change (§11) becomes a rewrite of one module rather
 * than of the application, and the UI is testable against [com.nexlink.social.core.fake.FakeSocialSession]
 * with no network (§34.3).
 *
 * **What it costs:** a translation layer, and the standing temptation to leak
 * SDK types through it for convenience. §11.6 is explicit that the goal is *a
 * seam at one module boundary, not a protocol-agnostic messaging framework* —
 * anything resembling the latter is scope creep and should be rejected.
 *
 * So: this interface grows when the application needs something, and not
 * before. It is not a place to model Matrix.
 */
interface SocialSession {

    val state: Flow<SessionState>

    fun rooms(): Flow<List<RoomSummary>>

    /**
     * Suspends. §11.6 sketched this as a plain function; the SDK's own
     * `Room.timeline()` suspends and the listener must be attached before any
     * item exists, so it cannot be. Changed deliberately in phase 3 — see
     * §14.2.4.
     */
    suspend fun timeline(roomId: RoomId): Timeline

    suspend fun send(roomId: RoomId, body: MessageBody): Result<EventId>

    /**
     * §14.5.1 — send an image. Separate from [send] because the upload is a
     * long-running operation with its own failure modes, and because the image
     * is prepared (downscaled, EXIF-stripped) before it gets here.
     */
    suspend fun sendImage(roomId: RoomId, localUri: String): Result<Unit>

    /**
     * §14.5.3 — send any other file. Returns a failure naming the limit if it is
     * over the cap, rather than letting the upload fail at the edge with a 413
     * after the user has waited (§25.2).
     */
    suspend fun sendFile(roomId: RoomId, localUri: String): Result<Unit>

    /**
     * §14.5.2 — fetch and decrypt media for display.
     *
     * Returns the decrypted bytes rather than a file path deliberately.
     * §12.6.1's plaintext problem: decrypted media on disk is the one place
     * message content exists in the clear on the device. Keeping it in memory
     * for rendering avoids creating that file at all.
     */
    suspend fun loadMedia(mediaId: String): Result<ByteArray>

    /**
     * §14.6 — edit a message you sent. Propagated to everyone; the original is
     * replaced in every client that renders relations.
     */
    suspend fun edit(roomId: RoomId, eventId: EventId, newText: String): Result<Unit>

    /**
     * §14.6 — delete (redact) a message.
     *
     * The honest limit, which the UI must state: redaction asks every
     * participant's client to remove it and asks the server to drop the
     * content. It cannot reach a copy someone already screenshotted, and the
     * pre-redaction original lingers server-side for
     * `redaction_retention_period` (§22.4, 7 days).
     */
    suspend fun delete(roomId: RoomId, eventId: EventId, reason: String? = null): Result<Unit>

    /**
     * §14.4 — toggle a reaction. Takes the room as well as the event: §11.6
     * sketched this without one, but reactions are sent on a room's timeline
     * and there is no way to reach a timeline from an event id alone.
     */
    suspend fun react(roomId: RoomId, eventId: EventId, emoji: String): Result<Unit>

    fun devices(): Flow<List<DeviceInfo>>

    suspend fun verifyDevice(deviceId: DeviceId): VerificationFlow

    /**
     * §6.6 — find another user. Exact-match lookup within this homeserver, which
     * is the whole discovery mechanism: there is no phone number to match on and
     * no public directory to browse (§1.3).
     */
    suspend fun findUsers(query: String): Result<List<UserSummary>>

    /**
     * §14.8 — open (or reuse) a one-to-one conversation. Encrypted by default,
     * which the server enforces (§22.4) rather than the client asking nicely.
     */
    suspend fun startDirectMessage(userId: UserId): Result<RoomId>

    /**
     * §14.8 — accept an invitation.
     *
     * §9.1's invite-only posture is about *the service*; this is about a room.
     * They are unrelated controls and conflating them would be a mistake: a
     * member of the service can still be added to a conversation they did not
     * ask for, which is why declining has to be as easy as accepting.
     */
    /**
     * §14.8 — create a group conversation.
     *
     * A group is named because it has no other identity: a DM can be titled
     * after the other person, a group cannot. Everyone invited starts as an
     * invitation they must accept (§14.8's accept/decline path).
     */
    suspend fun createGroup(name: String, invite: List<UserId>): Result<RoomId>

    /** §14.8 — add someone to an existing conversation. */
    suspend fun inviteToRoom(roomId: RoomId, userId: UserId): Result<Unit>

    /** §14.8 — who is in this conversation, and in what state. */
    suspend fun members(roomId: RoomId): Result<List<RoomMemberSummary>>

    suspend fun acceptInvite(roomId: RoomId): Result<Unit>

    /** §14.8 — decline an invitation, or leave a conversation. */
    suspend fun leaveRoom(roomId: RoomId): Result<Unit>

    /**
     * §14.7 — mark a conversation read.
     *
     * Uses a **private** read receipt. §3.7 and §9.6.1 tell users what the
     * operator can see; a public read receipt additionally tells *other
     * participants* exactly when you opened a message, which is a disclosure
     * this product has never promised and users have not asked for. Private
     * receipts clear the unread count without broadcasting.
     *
     * §14.7 lists read state and typing indicators together; they are separate
     * decisions and this one defaults to the quieter option.
     */
    suspend fun markRead(roomId: RoomId): Result<Unit>

    /** §14.7 — tell the room you are typing. Opt-in, see [markRead]'s reasoning. */
    suspend fun setTyping(roomId: RoomId, typing: Boolean): Result<Unit>

    /** §14.7 — who else is typing, by display name. Empty when nobody is. */
    fun typingUsers(roomId: RoomId): kotlinx.coroutines.flow.Flow<List<String>>
}

/**
 * §16.5.2's account states, plus the transitional ones the UI has to render.
 * Maps onto [com.nexlink.social.contract.AccountState] at the IPC boundary —
 * deliberately a separate type, because the contract's states are the subset
 * NexLink is entitled to know about (§16.4.3).
 */
sealed interface SessionState {
    /** No account on this device. The acceptance gate (§9.6) is the way in. */
    data object SignedOut : SessionState

    /** Credentials exist, the store is opening. */
    data object Restoring : SessionState

    /**
     * Signed in, but the local store is locked (§12.4.4). Content is not
     * readable until the device is unlocked. Not an error.
     */
    data object Locked : SessionState

    data class SignedIn(
        val userId: UserId,
        val deviceId: DeviceId,
        /** §8.5.2 — backup health. False means new devices will not get history. */
        val keyBackupHealthy: Boolean,
        /** §8.6 — an unverified device of the user's own needs surfacing, loudly (§8.3.2). */
        val unverifiedDeviceCount: Int
    ) : SessionState

    data class Failed(val reason: String) : SessionState
}

data class RoomSummary(
    val id: RoomId,
    val title: String,
    val avatarUrl: String?,
    val lastMessagePreview: String?,
    val lastMessageAt: Long,
    val unreadCount: Int,
    val isGroup: Boolean,
    val isMuted: Boolean,
    /** §14.2.3 — a room whose latest event failed to decrypt still lists, with a placeholder. */
    val lastMessageUndecryptable: Boolean = false,
    /** §14.8 — an invitation is not a conversation yet; the inbox says so. */
    val isInvite: Boolean = false,
    val invitedBy: String? = null
)

/** §14.5.3 — the send path's payload. Attachments arrive as a local URI string. */
sealed interface MessageBody {
    data class Text(val text: String, val replyTo: EventId? = null) : MessageBody
    data class Image(val localUri: String, val caption: String? = null) : MessageBody
    data class Video(val localUri: String, val caption: String? = null) : MessageBody
    data class Audio(val localUri: String) : MessageBody
    data class File(val localUri: String, val displayName: String) : MessageBody
}

/** §14.8 — one participant. [membership] distinguishes joined from invited. */
data class RoomMemberSummary(
    val id: UserId,
    val displayName: String?,
    val membership: String,
    val isSelf: Boolean
)

/** §6.6 — one result from a directory lookup. */
data class UserSummary(
    val id: UserId,
    val displayName: String?,
    val avatarUrl: String?
)

/** §8.6 — the device-management surface. */
data class DeviceInfo(
    val id: DeviceId,
    val displayName: String?,
    val isCurrent: Boolean,
    val isVerified: Boolean,
    val lastSeenAt: Long?,
    val lastSeenIp: String?
)

/** §8.4 — emoji SAS, QR, or recovery key. Modelled as a flow of steps the UI renders. */
interface VerificationFlow {
    val steps: Flow<VerificationStep>
    suspend fun confirmMatch()
    suspend fun declineMatch()
    suspend fun cancel()
}

sealed interface VerificationStep {
    data object Requested : VerificationStep
    data class ShowEmoji(val emoji: List<Pair<String, String>>) : VerificationStep
    data class ShowQr(val payload: ByteArray) : VerificationStep {
        override fun equals(other: Any?) = other is ShowQr && payload.contentEquals(other.payload)
        override fun hashCode() = payload.contentHashCode()
    }
    data object Verified : VerificationStep
    data class Cancelled(val reason: String) : VerificationStep
}
