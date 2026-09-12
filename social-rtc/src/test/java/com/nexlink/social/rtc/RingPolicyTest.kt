package com.nexlink.social.rtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §17.7.1 — "ringing is state, not a message".
 *
 * These pin a decision that has no protocol to fall back on. There is no "ring"
 * event in MatrixRTC: a client rings because it *observes membership state*, and
 * state has no opinion about whether it is fresh. So the app has to decide, and
 * the decision is wrong in both directions if it is careless.
 */
class RingPolicyTest {

    private fun ringing(ageMs: Long) = CallState.Ringing(
        roomId = "!r:example",
        callerUserId = "@a:example",
        callerDisplayName = "A",
        membershipAgeMs = ageMs
    )

    @Test
    fun `a fresh call rings`() {
        assertTrue(RingPolicy.shouldRing(0))
        assertTrue(RingPolicy.shouldRing(59_000))
        assertTrue(RingPolicy.shouldRing(RingPolicy.MAX_RING_AGE_MS))
    }

    /**
     * §17.7.1: "A call to an offline device is not lost. The state persists."
     * A phone that syncs an hour later must not ring for a call that is over.
     */
    @Test
    fun `a stale call does not ring, and becomes Missed rather than vanishing`() {
        assertFalse(RingPolicy.shouldRing(RingPolicy.MAX_RING_AGE_MS + 1))
        val out = RingPolicy.classify(ringing(60L * 60 * 1000))
        assertTrue("must be surfaced, not dropped", out is CallState.Missed)
        assertEquals("@a:example", (out as CallState.Missed).callerUserId)
    }

    /**
     * A clock skew must not silence a caller.
     *
     * This is the case the first draft got wrong: `shouldRing` used
     * `in 0..MAX`, so a negative age answered false, while `classify` special-
     * cased it and answered "ring". Two callers, two answers, from one policy
     * object — and the symptom would be the app not ringing for one specific
     * contact, which nobody would ever diagnose.
     */
    @Test
    fun `a clock ahead of ours still rings, and both entry points agree`() {
        val skewed = -5_000L
        assertTrue(RingPolicy.shouldRing(skewed))
        assertTrue(RingPolicy.classify(ringing(skewed)) is CallState.Ringing)
    }

    @Test
    fun `shouldRing and classify never disagree, across the range`() {
        listOf(-600_000L, -1L, 0L, 1L, 59_999L, 60_000L, 60_001L, 3_600_000L).forEach { age ->
            val rings = RingPolicy.shouldRing(age)
            val classified = RingPolicy.classify(ringing(age))
            assertEquals(
                "disagreement at age=$age",
                rings,
                classified is CallState.Ringing
            )
        }
    }

    /** The missed record has to carry when the call actually was, not now. */
    @Test
    fun `a missed call is dated from the membership, not from the moment we noticed`() {
        val ageMs = 30L * 60 * 1000
        val before = System.currentTimeMillis()
        val out = RingPolicy.classify(ringing(ageMs)) as CallState.Missed
        assertTrue("should be about ageMs in the past", out.at <= before - ageMs + 2_000)
    }
}
