package com.nexlink.social

/**
 * §14.15 — what a share Intent's text actually means.
 *
 * Pulled out of [ShareActivity] because it is the only part of sharing with
 * real edge cases, and the only part testable without a handset:
 *
 *  * A browser shares a page as **subject = title, text = URL**. Sending only
 *    the text loses the title; sending both blindly repeats it when the sender
 *    already put the title in the text, which many apps do.
 *  * Some apps send a subject and no text, some the reverse, some neither.
 *  * Whitespace-only is the same as absent, and a share sheet will hand it over.
 */
object ShareIntake {

    /**
     * The message body for a shared Intent, or null if there is nothing to say.
     *
     * Title and body are joined with a newline only when the body does not
     * already contain the title.
     */
    fun messageFor(text: String?, subject: String?): String? {
        val t = text?.trim()?.takeIf { it.isNotEmpty() }
        val s = subject?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            t == null && s == null -> null
            t == null -> s
            s == null -> t
            t.contains(s) -> t
            else -> "$s\n$t"
        }
    }
}
