package com.nexlink.social.core.acceptance

/**
 * What is recorded when the acceptance gate completes — §9.6.2.
 *
 * **The date of birth is not here, and that is the point.** §9.6.2: *"The date
 * of birth is not stored. Only the boolean outcome. Retaining the DOB creates a
 * personal-data liability with no operational benefit once the check has
 * passed."* §32.2 points at this as data minimisation done correctly.
 *
 * If you are adding a `dateOfBirth` field to this class, stop and read §9.6.2
 * and §32.2 first.
 */
data class AcceptanceRecord(
    /** Filled in server-side once the account exists; null while the gate is running. */
    val mxid: String?,
    /** Server timestamp, authoritative. The client's clock is not trusted for a legal record. */
    val acceptedAt: Long,
    val termsVersion: String,
    val privacyVersion: String,
    /** The outcome of the §9.6.1 screen 5 check. Never the date itself. */
    val ageConfirmed: Boolean,
    /** SHA-256 of the invite token — links to the tree (§9.4) without storing the code. */
    val inviteTokenHash: String,
    /** App versionCode at acceptance. */
    val clientVersion: Int
)

/**
 * The documents whose versions are recorded, and against which re-acceptance is
 * triggered — §9.6.3.
 *
 * Semantic versions, compared rather than dated, so "has this user accepted the
 * current terms?" is a string comparison and not a judgement.
 */
object PolicyVersions {
    const val TERMS = "0.1.0"
    const val PRIVACY = "0.1.0"

    /**
     * §9.6.3: when terms change materially the gate is shown again at next
     * launch with a diff summary. Users who decline are **not** locked out
     * immediately — they get a read-only grace period and an export path,
     * because immediate lockout on a terms change is both hostile and,
     * for a service holding their conversations, arguably unlawful.
     */
    const val GRACE_PERIOD_DAYS = 30

    fun requiresReAcceptance(accepted: AcceptanceRecord): Boolean =
        accepted.termsVersion != TERMS || accepted.privacyVersion != PRIVACY
}
