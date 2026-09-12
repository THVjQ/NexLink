package com.nexlink.social.rtc

/**
 * §17.7 — the call lifecycle, as types.
 *
 * "Independent of which option wins" is §17.7's first line, and it is the reason
 * this exists before §17.6 is decided. Whether the media path turns out to be
 * Element Call in a WebView (Option A) or livekit-android natively (Option B),
 * the *states a call moves through* are the same, because they are dictated by
 * MatrixRTC's membership model rather than by the transport.
 *
 * Writing them down now means the §17.6.1 spike builds both options against one
 * vocabulary and the comparison is about the media path, not about two
 * different ideas of what a call is.
 */
sealed interface CallState {

    /** No call. */
    data object Idle : CallState

    /**
     * §17.7 "Place" — we have written our membership state and are connecting.
     *
     * The foreground service does **not** start here (§15.4.1). It starts on
     * answer, for the outgoing side when media actually begins.
     */
    data class Placing(val roomId: String) : CallState

    /**
     * §17.7 "Ring" — membership state for a call we are not in has appeared.
     *
     * [membershipAgeMs] is carried because §17.7.1 makes it a decision rather
     * than a detail: a call to an offline device is **not lost**, the state
     * simply persists, so a device can sync hours later and find a call that
     * ended long ago. Ringing for that is wrong; silently dropping it is also
     * wrong. See [shouldRing].
     */
    data class Ringing(
        val roomId: String,
        val callerUserId: String,
        val callerDisplayName: String?,
        val membershipAgeMs: Long
    ) : CallState

    /** §17.7 "Answer" — our own membership written, connecting to the SFU. */
    data class Answering(val roomId: String) : CallState

    /** §17.7 "In call" — media flowing. The foreground service is running. */
    data class Connected(
        val roomId: String,
        val participantCount: Int,
        /** §17.5 — asserted from the session, never assumed. §33.5 requires it observed. */
        val encrypted: Boolean
    ) : CallState

    /** §17.7 "Leave" — tearing down. Membership removed, service stopping. */
    data object Leaving : CallState

    /**
     * The call was there and we did not take it.
     *
     * Distinct from [Idle] because §17.7.1's stale-membership case has to land
     * somewhere the user can see. A call that arrived while the phone was off
     * is missed, not absent.
     */
    data class Missed(val roomId: String, val callerUserId: String, val at: Long) : CallState

    data class Failed(val reason: String) : CallState
}

/**
 * §17.7.1 — ring, or present as missed?
 *
 * > *"Proposed: ring if the membership is under 60 seconds old, otherwise
 * > present it as missed."*
 *
 * The threshold is a product decision, not a protocol one, and it is here rather
 * than inline so it is visible and adjustable. It exists because **there is no
 * "ring" event** — a client rings because it *observes state*, and state has no
 * opinion about whether it is fresh.
 *
 * Getting this wrong is not cosmetic in either direction: too long and the phone
 * rings for a call nobody is on any more, which teaches people to ignore it; too
 * short and a legitimate call to a phone that was briefly out of signal is
 * silently downgraded to a missed-call line.
 */
object RingPolicy {
    /** §17.7.1's proposed threshold. */
    const val MAX_RING_AGE_MS = 60_000L

    /**
     * Negative ages ring.
     *
     * A negative age means the other device's clock is ahead of ours, which is
     * common and harmless. Treating "the future" as stale would silence calls
     * from anyone whose clock runs fast — a failure mode that presents as the
     * app randomly not ringing for one particular contact, which is close to
     * undiagnosable from a bug report.
     *
     * This is handled here rather than in [classify] so the two cannot drift:
     * an earlier draft had `membershipAgeMs in 0..MAX` here and the negative
     * case only in [classify], so a caller asking `shouldRing` directly got the
     * opposite answer from one calling `classify`.
     */
    fun shouldRing(membershipAgeMs: Long): Boolean =
        membershipAgeMs <= MAX_RING_AGE_MS

    fun classify(state: CallState.Ringing): CallState =
        if (shouldRing(state.membershipAgeMs)) state
        else CallState.Missed(
            roomId = state.roomId,
            callerUserId = state.callerUserId,
            at = System.currentTimeMillis() - state.membershipAgeMs
        )
}
