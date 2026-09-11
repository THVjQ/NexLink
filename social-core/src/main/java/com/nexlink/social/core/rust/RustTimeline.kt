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
import org.matrix.rustcomponents.sdk.EventOrTransactionId
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

    suspend fun send(body: MessageBody): Result<EventId> = runCatching {
        val text = (body as? MessageBody.Text)?.text
            ?: error("attachments land later in phase 3 — §14.5")
        inner.send(messageEventContentFromMarkdown(text))
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
        reactions = msgLike.content.reactions.associate { r ->
            r.key to r.senders.map { UserId(it.senderId) }
        }
    )
}
