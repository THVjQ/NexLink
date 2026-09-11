package com.nexlink.social.core.invite

/**
 * Invite code format and normalisation — docs/social/09-invites.md §9.3.
 *
 * The code is typed by a human, frequently from a screenshot or a read-aloud
 * message. Every rule here follows from that:
 *
 *  - **12 characters, three groups of four:** `X7K2-9QMF-3BTD`.
 *  - **Crockford base32:** digits and uppercase letters, excluding `I`, `L`,
 *    `O` and `U`. That removes the 1/I/L and 0/O confusions; `U` is excluded to
 *    avoid accidental profanity.
 *  - **Case-insensitive**, normalised to uppercase.
 *  - **Hyphens are cosmetic** — stripped before validation, so a user pasting
 *    without them succeeds. So is whitespace, which is what a read-aloud code
 *    tends to arrive with.
 *  - **Entropy:** 12 characters over 32 symbols is 60 bits. Brute force is
 *    infeasible; rate limiting (§9.7) covers the rest.
 *
 * This object is pure and has no Android dependency, which is why §34.3 lists
 * it first among the unit tests: it is at the front door, it is a pure
 * function, and it has many edge cases.
 */
object InviteCode {

    /** Crockford base32: 0-9 and A-Z less I, L, O, U. Exactly 32 symbols. */
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    const val LENGTH = 12
    const val GROUP_SIZE = 4
    const val SEPARATOR = '-'

    init {
        // A typo in ALPHABET would silently change the code space. Cheap to assert.
        check(ALPHABET.length == 32) { "Crockford base32 must have 32 symbols" }
        check(ALPHABET.toSet().size == 32) { "alphabet must not repeat a symbol" }
        check(ALPHABET.none { it in "ILOU" }) { "I, L, O and U are excluded (§9.3)" }
    }

    /**
     * Strip decoration and fold confusable characters, per Crockford's decoding
     * rules. Does **not** validate — [isValid] does that.
     *
     * `I` and `L` fold to `1`, `O` folds to `0`. `U` does **not** fold: it is
     * excluded from the alphabet deliberately, so a `U` in the input is a typo
     * for something, and guessing which would be worse than rejecting it.
     */
    fun normalise(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            when {
                c == SEPARATOR || c.isWhitespace() -> Unit
                else -> sb.append(
                    when (c.uppercaseChar()) {
                        'I', 'L' -> '1'
                        'O' -> '0'
                        else -> c.uppercaseChar()
                    }
                )
            }
        }
        return sb.toString()
    }

    fun isValid(raw: String): Boolean {
        val n = normalise(raw)
        return n.length == LENGTH && n.all { it in ALPHABET }
    }

    /**
     * Group a normalised code for display: `X7K29QMF3BTD` → `X7K2-9QMF-3BTD`.
     * Safe to call on a partial code — the input field uses it as the user
     * types.
     */
    fun format(normalised: String): String =
        normalised.chunked(GROUP_SIZE).joinToString(SEPARATOR.toString())

    /**
     * What went wrong, for a message the user can act on. Returning a reason
     * rather than a boolean is why the field can say "that's a letter O — try
     * a zero" instead of "invalid code".
     */
    fun validate(raw: String): InviteCodeError? {
        val n = normalise(raw)
        if (n.isEmpty()) return InviteCodeError.Empty
        n.firstOrNull { it !in ALPHABET }?.let { bad ->
            return if (bad == 'U') InviteCodeError.ExcludedCharacter(bad)
            else InviteCodeError.IllegalCharacter(bad)
        }
        return when {
            n.length < LENGTH -> InviteCodeError.TooShort(n.length)
            n.length > LENGTH -> InviteCodeError.TooLong(n.length)
            else -> null
        }
    }
}

sealed interface InviteCodeError {
    data object Empty : InviteCodeError
    data class TooShort(val length: Int) : InviteCodeError
    data class TooLong(val length: Int) : InviteCodeError
    data class IllegalCharacter(val char: Char) : InviteCodeError
    /** `U` specifically — excluded on purpose (§9.3), so it deserves its own message. */
    data class ExcludedCharacter(val char: Char) : InviteCodeError
}
