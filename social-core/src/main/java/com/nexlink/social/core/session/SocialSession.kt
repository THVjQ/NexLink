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

    suspend fun react(eventId: EventId, emoji: String): Result<Unit>

    fun devices(): Flow<List<DeviceInfo>>

    suspend fun verifyDevice(deviceId: DeviceId): VerificationFlow
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
    val lastMessageUndecryptable: Boolean = false
)

/** §14.5.3 — the send path's payload. Attachments arrive as a local URI string. */
sealed interface MessageBody {
    data class Text(val text: String, val replyTo: EventId? = null) : MessageBody
    data class Image(val localUri: String, val caption: String? = null) : MessageBody
    data class Video(val localUri: String, val caption: String? = null) : MessageBody
    data class Audio(val localUri: String) : MessageBody
    data class File(val localUri: String, val displayName: String) : MessageBody
}

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
