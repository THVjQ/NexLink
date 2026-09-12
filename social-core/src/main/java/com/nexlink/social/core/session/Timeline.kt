package com.nexlink.social.core.session

import kotlinx.coroutines.flow.Flow

/**
 * One room's message list — §14.2.
 *
 * Paginating rather than a plain list because a timeline is unbounded and the
 * local store is the primary copy (§12.2): the UI asks for more as the user
 * scrolls, and the store answers without a network round trip where it can.
 */
interface Timeline {
    val items: Flow<List<TimelineItem>>

    /** @return false when there is nothing older to fetch. */
    suspend fun paginateBack(count: Int = 30): Boolean

    suspend fun markRead(upTo: EventId)

    /**
     * §13.5.1 — push a failed or queued message at the server again, now.
     *
     * The user's "Retry" on a failed bubble. Also the right response to
     * regaining network while a queued message waits: the SDK retries on its
     * own schedule, and this shortcuts the wait.
     *
     * Idempotent: the transaction ID is persisted with the queue entry, so a
     * retry after an *ambiguous* failure cannot produce a second message. That
     * is the §13.5.1 requirement, and it is the SDK that satisfies it — do not
     * reimplement it here by generating a new ID.
     */
    suspend fun retrySend(id: EventId): Result<Unit>

    /**
     * §13.5.1 — remove a message from the send queue without sending it.
     *
     * The other half of "never silently dropped": a permanently failed message
     * stays visible until the user decides. This is that decision, made
     * explicitly. Returns false when the message had already been sent by the
     * time the user tapped, which is a race worth surfacing rather than lying
     * about.
     */
    suspend fun cancelSend(id: EventId): Result<Boolean>

    fun close()
}

data class TimelineItem(
    val eventId: EventId,
    val sender: UserId,
    val senderDisplayName: String,
    val timestamp: Long,
    val content: TimelineContent,
    val state: MessageState,
    /** §14.4 — reactions keyed by emoji, valued by who reacted. */
    val reactions: Map<String, List<UserId>> = emptyMap(),
    val isEdited: Boolean = false,
    val replyTo: EventId? = null,
    /**
     * §13.5.2 — why a [MessageState.FAILED] or [MessageState.QUEUED_OFFLINE]
     * message is not sent, so the UI can say something true instead of
     * "Not delivered". Null for every other state.
     */
    val sendFailure: SendFailure? = null
)

/**
 * §13.5.2 — the reasons a send does not complete, reduced to the ones the user
 * can act on differently.
 *
 * The SDK distinguishes more cases than this, but most of them share one
 * remedy ("try again"), and a list of distinct-but-identical errors is worse
 * for the user than a short list that maps to distinct actions.
 */
enum class SendFailure {
    /** No usable network. The SDK will retry by itself; [recoverable] is true. */
    OFFLINE,
    /** The server rejected it or the request failed. Retryable by hand. */
    SERVER_REJECTED,
    /**
     * §8 — someone in the room has an unverified device, or an identity that
     * changed, and the send is held rather than encrypting to a device the user
     * has not vouched for. **This is a security decision, not an error**, and
     * the UI must not offer a bare "Retry" for it: the remedy is to verify or
     * to explicitly accept, which is §8.4's flow, not this one.
     */
    VERIFICATION_REQUIRED,
    /** The attachment could not be read or was of a type the server refused. */
    MEDIA_REJECTED,
    UNKNOWN;

    /** True when a plain retry is the right offer. */
    val retryable: Boolean get() = this != VERIFICATION_REQUIRED
}

sealed interface TimelineContent {
    data class Text(val body: String) : TimelineContent
    /**
     * [mediaId] is an opaque handle the app hands back to fetch the bytes —
     * never a URL the UI can fetch itself. §12.6.1: media is encrypted on the
     * server, so only the SDK can decrypt it, and the decrypted bytes must not
     * land anywhere another app or a backup can read.
     */
    data class Image(
        val mediaId: String,
        val caption: String?,
        val width: Int?,
        val height: Int?
    ) : TimelineContent
    data class Video(val url: String, val caption: String?, val durationMs: Long?) : TimelineContent
    data class Audio(val url: String, val durationMs: Long?) : TimelineContent
    data class File(val url: String, val displayName: String, val sizeBytes: Long?) : TimelineContent
    data object Redacted : TimelineContent

    /**
     * §14.2.3 — decryption failed. Rendered as a placeholder that says what
     * happened and what the user can do, never as an empty bubble and never
     * silently dropped. The commonest cause is a message sent before this
     * device existed (§8.5).
     */
    data class Undecryptable(val reason: UndecryptableReason) : TimelineContent
}

enum class UndecryptableReason {
    /** Sent before this device joined. Expected; history restore (§8.5) is the fix. */
    SENT_BEFORE_DEVICE_EXISTED,
    /** The Megolm session never arrived. May resolve on its own. */
    KEYS_NOT_YET_RECEIVED,
    /** The sender's device was not verified and the user asked not to see such messages. */
    SENDER_UNVERIFIED,
    UNKNOWN
}

/** §14.2.2 — and every failure transition, which §34.3 tests. */
enum class MessageState { SENDING, SENT, DELIVERED, READ, FAILED, QUEUED_OFFLINE }
