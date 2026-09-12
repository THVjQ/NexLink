package com.nexlink.social.core.session

/**
 * §13.5.2 — the rule that decides how an unsent message renders.
 *
 * This lives here, on plain types, rather than inside `RustTimeline` where it is
 * used, for one reason: **the bug it replaces was not testable where it lived.**
 * The original mapping was a single expression against the SDK's
 * `EventTimelineItem`, and an `EventTimelineItem` cannot be constructed in a
 * unit test — it is an FFI object with a native peer. So the one line carrying
 * the whole "is this message lost?" decision had no test, and it was wrong:
 *
 * ```kotlin
 * state = if (ev.isRemote) MessageState.SENT else MessageState.SENDING
 * ```
 *
 * A permanently failed message is not remote, so it rendered as "Sending…"
 * indefinitely — no crash, no log line, just a spinner that never resolves and
 * a message that never arrives. §13.5.1 forbids exactly that.
 *
 * `RustTimeline` now reduces the SDK's types to the three arguments below and
 * calls this. The reduction is trivial and the judgement is here, where a test
 * can reach it.
 */
object SendState {

    /**
     * @param isRemote the event has an ID from the server — it definitely landed.
     * @param failure why the send did not complete, or null if it has not failed.
     * @param recoverable whether the SDK intends to retry by itself. Only
     *   meaningful when [failure] is non-null.
     */
    fun classify(
        isRemote: Boolean,
        failure: SendFailure?,
        recoverable: Boolean
    ): MessageState = when {
        failure == null -> if (isRemote) MessageState.SENT else MessageState.SENDING
        // The SDK will keep trying: that is "queued", not "failed". The
        // difference is the whole point — one needs the user to act and the
        // other explicitly does not, and telling a user to retry something
        // already retrying is how an app trains people to ignore it.
        recoverable -> MessageState.QUEUED_OFFLINE
        else -> MessageState.FAILED
    }
}
