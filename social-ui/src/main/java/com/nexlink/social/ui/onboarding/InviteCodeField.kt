package com.nexlink.social.ui.onboarding

import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.text.TextWatcher
import android.widget.EditText
import com.nexlink.social.core.invite.InviteCode

/**
 * The invite-code input — §9.3.
 *
 * *"The client applies an input filter mirroring NexLink's existing pairing-code
 * field, which already uses `InputFilter.AllCaps()` and a length filter — the
 * same pattern, a different alphabet."*
 *
 * Two jobs, and they are separate on purpose:
 *  - the [InputFilter] refuses characters that cannot be part of a code;
 *  - the [TextWatcher] inserts the cosmetic hyphens as the user types.
 *
 * The watcher must not be the thing enforcing validity, because a paste arrives
 * as one edit and a filter sees it first.
 */
object InviteCodeField {

    /** `XXXX-XXXX-XXXX` — 12 characters plus 2 hyphens. */
    private const val FORMATTED_LENGTH =
        InviteCode.LENGTH + (InviteCode.LENGTH / InviteCode.GROUP_SIZE) - 1

    fun attach(field: EditText) {
        field.filters = arrayOf(Filter(), InputFilter.LengthFilter(FORMATTED_LENGTH))
        field.addTextChangedListener(Hyphenator())
    }

    /**
     * Accepts anything that normalises into the alphabet, so a user typing a
     * lowercase `l` or a letter `O` is not blocked — [InviteCode.normalise]
     * folds those to `1` and `0` per Crockford (§9.3). Blocking them would be
     * hostile: the whole reason for the alphabet is that those characters are
     * confusable, and the user typing one has read the code correctly.
     *
     * A `U` is refused outright. It is excluded deliberately and folding it
     * would mean guessing.
     */
    private class Filter : InputFilter {
        override fun filter(
            source: CharSequence, start: Int, end: Int,
            dest: Spanned, dstart: Int, dend: Int
        ): CharSequence? {
            val out = StringBuilder(end - start)
            var changed = false
            for (i in start until end) {
                val c = source[i]
                // The SEPARATOR passes through. It must: an InputFilter runs on
                // programmatic edits too, so stripping it here would silently
                // undo the hyphens [Hyphenator] has just inserted — the code
                // would stay as XXXXXXXXXXXX forever. [Hyphenator] owns hyphen
                // placement; a user-typed one is simply re-grouped.
                if (c == InviteCode.SEPARATOR) { out.append(c); continue }
                if (c.isWhitespace()) { changed = true; continue }
                val folded = InviteCode.normalise(c.toString())
                if (folded.length == 1 && folded[0] in InviteCode.ALPHABET) {
                    out.append(folded[0])
                    if (folded[0] != c) changed = true
                } else {
                    changed = true // dropped
                }
            }
            return if (changed) out else null
        }
    }

    /**
     * Re-groups on every edit. Guards against its own writes with [busy] —
     * without it the rewrite recurses.
     *
     * **Mutates the [Editable] in place; does NOT call `setText`.** That
     * distinction is not stylistic, it is a crash:
     *
     * ```
     * IndexOutOfBoundsException: setSpan (6 ... 6) ends beyond length 5
     *   at InviteCodeField$Hyphenator.afterTextChanged
     * ```
     *
     * `afterTextChanged` runs while the key listener is still inside
     * `SpannableStringBuilder.replace()` on the current buffer. `setText`
     * installs a *different* buffer, so the following `setSelection` indexes
     * into a stale, shorter one and throws. Found on a real device (SM-G990E,
     * Android 16) on the fifth keystroke — the first field a new user ever
     * touches.
     *
     * `s.replace(...)` edits the buffer the framework is already holding, so
     * the selection follows naturally and no explicit `setSelection` is needed.
     */
    private class Hyphenator : TextWatcher {
        private var busy = false

        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit

        override fun afterTextChanged(s: Editable?) {
            if (busy || s == null) return
            val current = s.toString()
            val formatted = InviteCode.format(InviteCode.normalise(current))
            if (formatted == current) return

            busy = true
            s.replace(0, s.length, formatted)
            busy = false
        }
    }
}
