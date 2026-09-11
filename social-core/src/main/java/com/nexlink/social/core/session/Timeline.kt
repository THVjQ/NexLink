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
    val replyTo: EventId? = null
)

sealed interface TimelineContent {
    data class Text(val body: String) : TimelineContent
    data class Image(val url: String, val caption: String?, val width: Int?, val height: Int?) : TimelineContent
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
