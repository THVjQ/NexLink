package com.nexlink.social.core.session

import com.nexlink.social.core.fake.FakeSocialSession
import com.nexlink.social.core.fake.FakeTimeline
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §13.5.1 — "a queued message is never silently lost".
 *
 * These pin the distinctions the UI depends on. The bug they exist to prevent
 * is not dramatic: before this, [RustTimeline] mapped state as
 * `if (isRemote) SENT else SENDING`, so a message that had permanently failed
 * rendered as "Sending…" forever. Nothing crashed and nothing logged — the
 * message simply never arrived while the UI said it was on its way. A spinner
 * that never resolves is indistinguishable from a lost message, and that is the
 * outcome §13.5.1 forbids.
 */
class SendFailureTest {

    /**
     * **The regression test.** This is the exact case the old
     * `if (isRemote) SENT else SENDING` got wrong: not remote, has failed,
     * will not be retried — and it reported SENDING, forever.
     */
    @Test
    fun `a permanently failed message is FAILED, never SENDING`() {
        val state = SendState.classify(
            isRemote = false,
            failure = SendFailure.SERVER_REJECTED,
            recoverable = false
        )
        assertEquals(MessageState.FAILED, state)
        assertFalse(
            "a failed message must never render as in-flight — a spinner that " +
                "never resolves is indistinguishable from a lost message (§13.5.1)",
            state == MessageState.SENDING
        )
    }

    /**
     * The whole point of splitting QUEUED_OFFLINE from FAILED: one of them
     * resolves by itself and the other needs the user. A UI that cannot tell
     * them apart either nags about messages that are fine or stays silent about
     * messages that are stuck.
     */
    @Test
    fun `recoverable means queued, unrecoverable means failed`() {
        assertEquals(
            MessageState.QUEUED_OFFLINE,
            SendState.classify(false, SendFailure.OFFLINE, recoverable = true)
        )
        assertEquals(
            MessageState.FAILED,
            SendState.classify(false, SendFailure.OFFLINE, recoverable = false)
        )
    }

    /** No failure: the ordinary path, unchanged. */
    @Test
    fun `without a failure it is sent when remote and sending otherwise`() {
        assertEquals(MessageState.SENT, SendState.classify(true, null, false))
        assertEquals(MessageState.SENDING, SendState.classify(false, null, false))
    }

    @Test
    fun `the fake models the same distinction the real mapping does`() = runTest {
        assertEquals(MessageState.QUEUED_OFFLINE, stateFor(SendFailure.OFFLINE))
        assertEquals(MessageState.FAILED, stateFor(SendFailure.SERVER_REJECTED))
        assertEquals(MessageState.FAILED, stateFor(SendFailure.MEDIA_REJECTED))
    }

    /**
     * §8 — a send held because a device is unverified is a security decision,
     * not a transport error. Offering a bare "Retry" would train the user to
     * tap past it, and it would not send anyway.
     */
    @Test
    fun `verification-required is the one failure that is not retryable`() {
        SendFailure.entries.forEach { f ->
            assertEquals(
                "retryable for $f",
                f != SendFailure.VERIFICATION_REQUIRED,
                f.retryable
            )
        }
    }

    /** A retry that reports success must actually clear the failure. */
    @Test
    fun `retry clears both the state and the reason`() = runTest {
        val (t, id) = failed(SendFailure.SERVER_REJECTED)
        assertTrue(t.retrySend(id).isSuccess)
        val item = t.items.first().single { it.eventId == id }
        assertEquals(MessageState.SENT, item.state)
        assertNull("a sent message must not keep a failure reason", item.sendFailure)
    }

    /** Discarding removes it — the explicit half of "never silently dropped". */
    @Test
    fun `cancel removes the message and reports whether there was one`() = runTest {
        val (t, id) = failed(SendFailure.SERVER_REJECTED)
        assertEquals(true, t.cancelSend(id).getOrNull())
        assertTrue(t.items.first().none { it.eventId == id })
        // Second cancel: nothing to remove. This models the race where the
        // message was sent between the tap and the cancel, which the UI reports
        // rather than hiding.
        assertEquals(false, t.cancelSend(id).getOrNull())
    }

    @Test
    fun `a healthy message carries no failure reason`() = runTest {
        val (t, _) = oneMessage()
        val item = t.items.first().single()
        assertNull(item.sendFailure)
        assertFalse(item.state == MessageState.FAILED)
    }

    private suspend fun stateFor(reason: SendFailure): MessageState =
        failed(reason).let { (t, id) -> t.items.first().single { it.eventId == id }.state }

    /** One sent message, then forced into [reason]'s failure state. */
    private suspend fun failed(reason: SendFailure): Pair<FakeTimeline, EventId> {
        val (t, id) = oneMessage()
        t.fail(id, reason)
        return t to id
    }

    private suspend fun oneMessage(): Pair<FakeTimeline, EventId> {
        val session = FakeSocialSession()
        val room = RoomId("!r:example")
        val id = session.send(room, MessageBody.Text("hello")).getOrThrow()
        return (session.timeline(room) as FakeTimeline) to id
    }
}
