package com.nexlink.social.contract

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Outcome of an inline reply sent from NexLink's inbox — §16.4.2.
 *
 * Deliberately not a boolean. An inline reply can fail for reasons the user
 * needs told apart: offline (it was queued and will send, §13.5.1) is a
 * different message from "you are signed out" or "that conversation is gone".
 */
@Parcelize
data class ReplyResult(
    val status: Int,
    /** Human-readable, already localised by the Social app. Null when [status] is [OK]. */
    val message: String? = null
) : Parcelable {

    val isOk: Boolean get() = status == OK
    /** Queued counts as success for UI purposes: the user's text is not lost. */
    val isAccepted: Boolean get() = status == OK || status == QUEUED

    companion object {
        const val OK = 0

        /** Accepted into the offline send queue (§13.5.1). It will go when there is a network. */
        const val QUEUED = 1

        /** No account, or the local store is locked. NexLink should refresh state. */
        const val NOT_SIGNED_IN = 2

        /** The conversation ID is not one this account has. Stale inbox cache (§16.5.2). */
        const val UNKNOWN_CONVERSATION = 3

        /** Encryption could not produce a sendable event. Never silently retried. */
        const val ENCRYPTION_FAILED = 4

        /** Anything else. [message] carries the detail. */
        const val FAILED = 5

        fun ok() = ReplyResult(OK)
        fun queued() = ReplyResult(QUEUED)
        fun failed(status: Int, message: String) = ReplyResult(status, message)
    }
}
