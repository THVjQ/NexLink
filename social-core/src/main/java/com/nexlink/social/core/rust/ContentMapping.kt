package com.nexlink.social.core.rust

import com.nexlink.social.core.session.TimelineContent
import com.nexlink.social.core.session.UndecryptableReason
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.TimelineItemContent

/**
 * One translation of the SDK's event content into the application's vocabulary.
 *
 * Shared by the timeline (§14.2) and the room-list preview (§14.1), because they
 * must agree. A room row that says "No messages yet" next to a conversation that
 * plainly has messages is the kind of inconsistency users read as "this app is
 * broken", and it happens when two call sites translate the same type twice.
 */
internal fun TimelineItemContent.toAppContent(): TimelineContent? {
    val msgLike = this as? TimelineItemContent.MsgLike ?: return null
    return when (val kind = msgLike.content.kind) {
        is MsgLikeKind.Message -> when (val m = kind.content.msgType) {
            is MessageType.Text -> TimelineContent.Text(m.content.body)
            else -> TimelineContent.Text(kind.content.body)
        }
        is MsgLikeKind.Redacted -> TimelineContent.Redacted
        is MsgLikeKind.UnableToDecrypt ->
            TimelineContent.Undecryptable(UndecryptableReason.SENT_BEFORE_DEVICE_EXISTED)
        else -> null
    }
}

/**
 * A one-line preview for the inbox — §14.1.
 *
 * §16.4.3 caps preview length at the IPC boundary; this is the same idea applied
 * inside the app. A preview is a hint, not the message.
 */
/**
 * A preview for the inbox row, covering events the timeline does not render as
 * bubbles — §14.1.
 *
 * `latestEvent()` returns the room's most recent event of **any** type, which is
 * frequently a membership or state change rather than a message. Mapping those
 * to null makes the row say "No messages yet" next to a conversation where
 * something visibly just happened, which reads as a bug.
 */
internal fun TimelineItemContent.toRoomPreview(): String? {
    toAppContent()?.let { return it.toPreview() }
    return when (this) {
        is TimelineItemContent.RoomMembership -> "Joined the conversation"
        is TimelineItemContent.ProfileChange -> "Updated their profile"
        is TimelineItemContent.State -> null      // room name, topic, avatar: noise
        is TimelineItemContent.CallInvite -> "Call"
        is TimelineItemContent.FailedToParseMessageLike,
        is TimelineItemContent.FailedToParseState -> null
        else -> null
    }
}

internal fun TimelineContent.toPreview(): String = when (this) {
    // §14.4.3 — take() counts UTF-16 code units and will split a surrogate
    // pair. Graphemes.truncate walks grapheme clusters instead.
    is TimelineContent.Text ->
        com.nexlink.social.core.Graphemes.truncate(body.replace('\n', ' '), 120, "")
    is TimelineContent.Image -> "Photo"
    is TimelineContent.Video -> "Video"
    is TimelineContent.Audio -> "Audio message"
    is TimelineContent.File -> "File"
    is TimelineContent.Redacted -> "Message deleted"
    // §14.2.3 — the inbox says the same thing the timeline does, in fewer words.
    is TimelineContent.Undecryptable -> "Can't decrypt this message"
}
