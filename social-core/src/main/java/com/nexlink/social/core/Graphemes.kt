package com.nexlink.social.core

import java.text.BreakIterator

/**
 * Grapheme-safe truncation — §14.4.3.
 *
 * `String.take(n)` counts **UTF-16 code units**, so it will happily cut a
 * surrogate pair in half. Emoji outside the BMP are surrogate pairs, and a
 * ZWJ sequence (a family, a profession, a flag) is several of them joined by
 * zero-width joiners. Truncating one mid-sequence produces the broken boxes
 * §14.4.3 warns about, and it does so only for the users who happen to send
 * emoji near the cut-off — which is why it survives testing.
 *
 * `BreakIterator.getCharacterInstance()` walks grapheme clusters, which is the
 * unit a human sees as "one character".
 */
object Graphemes {

    /** Truncate to at most [max] grapheme clusters, never splitting one. */
    fun truncate(text: String, max: Int, ellipsis: String = "…"): String {
        if (text.length <= max) return text          // cheap path; length is an upper bound
        val it = BreakIterator.getCharacterInstance().apply { setText(text) }
        var count = 0
        var end = it.first()
        while (it.next() != BreakIterator.DONE) {
            if (count >= max) break
            count++
            end = it.current()
        }
        return if (end >= text.length) text else text.substring(0, end).trimEnd() + ellipsis
    }

    /** How many graphemes — i.e. how many characters a person would count. */
    fun count(text: String): Int {
        val it = BreakIterator.getCharacterInstance().apply { setText(text) }
        var n = 0
        while (it.next() != BreakIterator.DONE) n++
        return n
    }

    /** §14.4.3 — is this exactly one grapheme? A valid reaction should be. */
    fun isSingleGrapheme(text: String): Boolean = text.isNotEmpty() && count(text) == 1
}
