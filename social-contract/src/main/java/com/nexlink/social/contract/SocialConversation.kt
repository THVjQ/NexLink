package com.nexlink.social.contract

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * One conversation, as NexLink's unified inbox sees it — §16.4.1.
 *
 * **[id] is opaque.** NexLink receives an identifier it can hand back, never a
 * Matrix room ID. That keeps protocol details out of `:app` entirely and means a
 * protocol change on the Social side does not reach across the boundary
 * (§16.4.1).
 *
 * [lastMessage] is decrypted plaintext crossing a process boundary. §16.4.3
 * accepts that — it is the same data the level-0 notification already carries,
 * travelling over a signature-protected channel instead of the notification
 * system — but bounds it: previews are truncated to [PREVIEW_MAX_CHARS], and a
 * user who has asked for previews to be suppressed gets null here. **The
 * suppression happens on the Social side, not by NexLink filtering afterwards.**
 */
@Parcelize
data class SocialConversation(
    val id: String,
    val title: String,
    val avatarUri: String?,
    val lastMessage: String?,
    val lastMessageAt: Long,
    val unreadCount: Int,
    val isGroup: Boolean,
    val isMuted: Boolean
) : Parcelable {

    init {
        require(id.isNotEmpty()) { "conversation id must not be empty" }
        require(unreadCount >= 0) { "unreadCount must not be negative" }
    }

    companion object {
        /**
         * Previews are truncated before they cross the boundary (§16.4.3). The
         * number is a judgement, not a protocol constant — long enough to be
         * useful in an inbox row, short enough that a leaked preview is a
         * sentence rather than a message.
         */
        const val PREVIEW_MAX_CHARS = 120

        /**
         * Truncate for transport. Call this on the Social side before
         * constructing the object; it is here rather than in `:social-core` so
         * that both sides agree on what "truncated" means.
         */
        fun truncatePreview(text: String?): String? {
            if (text == null) return null
            if (text.length <= PREVIEW_MAX_CHARS) return text
            return text.take(PREVIEW_MAX_CHARS - 1).trimEnd() + "…"
        }
    }
}
