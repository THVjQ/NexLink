package com.nexlink.social.core.rust

import com.nexlink.social.core.session.EventId
import com.nexlink.social.core.session.MessageBody
import com.nexlink.social.core.session.MessageState
import com.nexlink.social.core.session.Timeline
import com.nexlink.social.core.session.TimelineContent
import com.nexlink.social.core.session.TimelineItem
import com.nexlink.social.core.session.UndecryptableReason
import com.nexlink.social.core.session.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.matrix.rustcomponents.sdk.EditedContent
import org.matrix.rustcomponents.sdk.EventOrTransactionId
import org.matrix.rustcomponents.sdk.FileInfo
import org.matrix.rustcomponents.sdk.ImageInfo
import org.matrix.rustcomponents.sdk.UploadParameters
import org.matrix.rustcomponents.sdk.UploadSource
import org.matrix.rustcomponents.sdk.EventTimelineItem
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.TimelineListener
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdown
import java.util.Collections
import org.matrix.rustcomponents.sdk.Timeline as SdkTimeline
import org.matrix.rustcomponents.sdk.TimelineItem as SdkTimelineItem

/**
 * One room's timeline, translated into the application's vocabulary — §14.2.
 *
 * Imports here are deliberately explicit and aliased. Wildcard-importing both
 * `com.nexlink.social.core.session.*` and `org.matrix.rustcomponents.sdk.*`
 * collides on `Timeline`, `TimelineItem` and more, and the resulting errors
 * point at the wrong line. This is the one file where both vocabularies meet.
 *
 * The SDK has **no `getItems()`** — the only way to read a timeline is to attach
 * a listener and fold its diffs. That alone justifies the §11.6 seam.
 */
internal class RustTimeline(private val inner: SdkTimeline) : Timeline {

    private val _items = MutableStateFlow<List<TimelineItem>>(emptyList())
    override val items: Flow<List<TimelineItem>> = _items.asStateFlow()

    private val raw: MutableList<SdkTimelineItem> = Collections.synchronizedList(mutableListOf())
    private var handle: TaskHandle? = null

    suspend fun start() {
        handle = inner.addListener(object : TimelineListener {
            override fun onUpdate(diff: List<TimelineDiff>) {
                synchronized(raw) {
                    diff.forEach { apply(it) }
                    _items.value = raw.mapNotNull { it.toAppItem() }
                }
            }
        })
    }

    private fun apply(d: TimelineDiff) {
        when (d) {
            is TimelineDiff.Append -> raw.addAll(d.values)
            is TimelineDiff.Reset -> { raw.clear(); raw.addAll(d.values) }
            is TimelineDiff.PushBack -> raw.add(d.value)
            is TimelineDiff.PushFront -> raw.add(0, d.value)
            is TimelineDiff.Insert -> raw.add(d.index.toInt().coerceIn(0, raw.size), d.value)
            is TimelineDiff.Set -> {
                val i = d.index.toInt()
                if (i in raw.indices) raw[i] = d.value else raw.add(d.value)
            }
            is TimelineDiff.Remove -> { val i = d.index.toInt(); if (i in raw.indices) raw.removeAt(i) }
            is TimelineDiff.PopBack -> { if (raw.isNotEmpty()) raw.removeAt(raw.size - 1) }
            is TimelineDiff.PopFront -> { if (raw.isNotEmpty()) raw.removeAt(0) }
            else -> raw.clear()   // Clear, Truncate
        }
    }

    override suspend fun paginateBack(count: Int): Boolean =
        inner.paginateBackwards(count.toUShort())

    override suspend fun markRead(upTo: EventId) {
        runCatching { inner.sendReadReceipt(ReceiptType.READ, upTo.value) }
    }

    override fun close() { handle?.cancel(); handle = null }

    /**
     * §14.4 — toggle, not add. Reacting twice with the same emoji removes it,
     * which is what every messenger does and what users expect.
     *
     * §14.4.3: the emoji is passed through as an opaque string. A ZWJ sequence
     * or a skin-tone modifier is several code points and one grapheme; anything
     * that indexes into it, truncates it, or "normalises" it will split it and
     * produce the broken boxes that section warns about.
     */
    suspend fun toggleReaction(eventId: EventId, emoji: String): Result<Unit> = runCatching {
        inner.toggleReaction(EventOrTransactionId.EventId(eventId.value), emoji)
        Unit
    }

    /** §14.5.1 — upload and send an image. Encrypted by the SDK before upload. */
    suspend fun sendImage(file: java.io.File, width: Int, height: Int, mimeType: String): Result<Unit> =
        runCatching {
            val info = ImageInfo(
                height = height.toULong(),
                width = width.toULong(),
                mimetype = mimeType,
                size = file.length().toULong(),
                thumbnailInfo = null,
                thumbnailSource = null,
                blurhash = null,
                isAnimated = false
            )
            val params = UploadParameters(
                source = UploadSource.File(file.absolutePath),
                caption = null,
                formattedCaption = null,
                mentions = null,
                inReplyTo = null,
                extraContentJson = null
            )
            // join() waits for the upload to finish so the caller can report a
            // real failure. §13.5.1's queue makes a dropped upload recoverable;
            // it does not make silence acceptable.
            inner.sendImage(params, UploadSource.File(file.absolutePath), info).join()
        }

    /** §14.6 — replace the body of a message you sent. */
    suspend fun edit(eventId: EventId, newText: String): Result<Unit> = runCatching {
        inner.edit(
            EventOrTransactionId.EventId(eventId.value),
            EditedContent.RoomMessage(messageEventContentFromMarkdown(newText))
        )
    }

    /** §14.6 — redact. See the interface doc for what this cannot undo. */
    suspend fun redact(eventId: EventId, reason: String?): Result<Unit> = runCatching {
        inner.redactEvent(EventOrTransactionId.EventId(eventId.value), reason)
    }

    /** §14.6 — flat replies only. Threads are explicitly out of scope (§1.6). */
    suspend fun sendReply(text: String, replyToEventId: EventId): Result<Unit> = runCatching {
        inner.sendReply(messageEventContentFromMarkdown(text), replyToEventId.value)
        Unit
    }

    /**
     * §14.5.3 — send any other file.
     *
     * Unlike an image this is sent as-is: there is nothing safe to strip from an
     * arbitrary file without corrupting it, and re-encoding a document is not
     * something a messenger should do silently. The size cap (§25.2) is the only
     * gate, and the caller enforces it before getting here.
     */
    suspend fun sendFile(
        file: java.io.File,
        displayName: String,
        mimeType: String
    ): Result<Unit> = runCatching {
        val info = FileInfo(
            mimetype = mimeType,
            size = file.length().toULong(),
            thumbnailInfo = null,
            thumbnailSource = null
        )
        val params = UploadParameters(
            source = UploadSource.File(file.absolutePath),
            caption = displayName,
            formattedCaption = null,
            mentions = null,
            inReplyTo = null,
            extraContentJson = null
        )
        inner.sendFile(params, info).join()
    }

    suspend fun send(body: MessageBody): Result<EventId> = runCatching {
        val msg = body as? MessageBody.Text ?: error("use sendImage for attachments — §14.5")
        val replyTo = msg.replyTo
        if (replyTo != null) inner.sendReply(messageEventContentFromMarkdown(msg.text), replyTo.value)
        else inner.send(messageEventContentFromMarkdown(msg.text))
        // send() returns a SendHandle, not an id. The real event id arrives on
        // the timeline when the local echo is replaced (§14.2.2).
        EventId("local-echo")
    }
}

private fun SdkTimelineItem.toAppItem(): TimelineItem? {
    val ev: EventTimelineItem = asEvent() ?: return null
    val msgLike = ev.content as? TimelineItemContent.MsgLike ?: return null
    // §14.2.3 and the inbox preview share one translation — see ContentMapping.
    val appContent = ev.content.toAppContent() ?: return null

    val id = when (val t = ev.eventOrTransactionId) {
        is EventOrTransactionId.EventId -> t.eventId
        is EventOrTransactionId.TransactionId -> t.transactionId
    }

    return TimelineItem(
        eventId = EventId(id),
        sender = UserId(ev.sender),
        senderDisplayName = (ev.senderProfile as? ProfileDetails.Ready)?.displayName ?: ev.sender,
        timestamp = ev.timestamp.toLong(),
        content = appContent,
        state = if (ev.isRemote) MessageState.SENT else MessageState.SENDING,
        // §14.6 — an edited message is marked. A silent replacement is how a
        // conversation ends up disputed: one person remembers what was said and
        // the record no longer shows it was changed.
        isEdited = ((msgLike.content.kind as? MsgLikeKind.Message)?.content?.isEdited) == true,
        reactions = msgLike.content.reactions.associate { r ->
            r.key to r.senders.map { UserId(it.senderId) }
        }
    )
}
