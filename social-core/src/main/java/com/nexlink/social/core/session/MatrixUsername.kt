package com.nexlink.social.core.session

/**
 * What a person types when a screen asks for their username, turned into what
 * Matrix will accept.
 *
 * This is domain logic, not presentation: the rules come from the Matrix
 * identifier grammar, and both the sign-in screen and the create-account screen
 * have to apply the same ones or the two disagree about who you are.
 *
 * It exists because of a real failure (2026-09-16, the operator's own first
 * sign-in). The account is `thvjq`; the app displays the address
 * `@thvjq:nexlink.thvjq.com.au`, which is what people read back and type. Sent
 * verbatim, Synapse logged *"Attempted to login as @thvjq but they do not
 * exist"* and answered 403 — and the screen reported that as though the
 * password were wrong, which is the worst possible thing to tell someone whose
 * password is in fact correct.
 */
object MatrixUsername {

    /**
     * Accepts anything a person might reasonably type and returns the localpart.
     *
     * - `@thvjq:nexlink.thvjq.com.au` → `thvjq`
     * - `@thvjq` → `thvjq`
     * - `THVjQ` → `thvjq`, because a localpart **cannot** contain a capital.
     *   That is not a nicety: the display name may well be `THVjQ`, so the
     *   string someone copies off a profile is frequently uppercase and can
     *   never be a valid localpart.
     * - surrounding whitespace, which a paste or a read-aloud brings with it
     *
     * It deliberately does not validate. A name this rejects would be rejected
     * by the server too, with a better message than this could invent, and
     * guessing what someone meant is how you sign them in as the wrong person.
     */
    fun normalise(raw: String): String =
        raw.trim()
            .removePrefix("@")
            .substringBefore(':')
            .trim()
            .lowercase()
}
