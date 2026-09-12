package com.nexlink.social.core.transfer

/**
 * §7.5.2 — what may be used as an export passphrase.
 *
 * The section's rule is blunt: *"The passphrase must be distinct from the
 * recovery key and the UI must not allow reuse, because an exported file plus a
 * reused recovery key in the same cloud drive is a single point of total
 * compromise."*
 *
 * **That rule cannot be enforced the obvious way.** Comparing the passphrase
 * against the recovery key would require having the recovery key, and §7.2 is
 * emphatic that the app never keeps it — it is shown once and never stored.
 * Keeping a copy in order to police reuse would create exactly the asset the
 * design exists to avoid, and would be a worse outcome than the reuse.
 *
 * So the rule is enforced by **shape**, not by comparison. A Matrix recovery key
 * has a form nothing else has, confirmed by generating one on 2026-09-12:
 *
 * ```
 * EsU3 G5nq 5QkF N9Br Svbn uz7a xUHE pzVy xetu wLAh WSTQ UzFf
 * ```
 *
 * 48 base58 characters, shown in twelve groups of four, beginning `Es`. A user
 * who pastes that into the passphrase box is pasting their recovery key —
 * there is no other plausible reading — and it is refused.
 *
 * This is weaker than a comparison and it is the honest maximum: it cannot
 * catch someone who *retypes* the key with a character changed, and it does not
 * try to. It catches the copy-and-paste, which is how reuse actually happens.
 */
object Passphrase {

    /**
     * §7.5.2's archive is only as strong as this, and unlike the recovery key
     * it is chosen by a human. 12 is the floor at which a passphrase resists
     * offline attack on a file someone else holds — which is the whole threat
     * model for an archive sitting in a cloud drive.
     */
    const val MIN_LENGTH = 12

    /** Base58 — Bitcoin's alphabet, which is what Matrix uses. No 0, O, I, l. */
    private const val BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    sealed interface Verdict {
        data object Ok : Verdict
        data class Rejected(val reason: String) : Verdict
    }

    fun check(passphrase: String, confirmation: String? = null): Verdict = when {
        passphrase.isBlank() ->
            Verdict.Rejected("Enter a passphrase.")

        looksLikeRecoveryKey(passphrase) -> Verdict.Rejected(
            "That looks like your recovery key. Use something different — if " +
                "this file and your recovery key ever sit in the same place, " +
                "one of them opens both."
        )

        passphrase.length < MIN_LENGTH -> Verdict.Rejected(
            "Use at least $MIN_LENGTH characters. This file can be copied and " +
                "attacked offline for as long as someone likes, so its length " +
                "is the only thing protecting it."
        )

        confirmation != null && passphrase != confirmation ->
            Verdict.Rejected("The two passphrases don't match.")

        else -> Verdict.Ok
    }

    /**
     * True for anything with a recovery key's shape.
     *
     * Spacing is ignored because the key is *displayed* in groups of four and a
     * paste may carry them, collapse them, or wrap them into newlines.
     */
    fun looksLikeRecoveryKey(input: String): Boolean {
        val compact = input.filterNot { it.isWhitespace() }
        return compact.length == 48 &&
            compact.startsWith("Es") &&
            compact.all { it in BASE58 }
    }
}
