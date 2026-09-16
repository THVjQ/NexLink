package com.nexlink.social.ui.onboarding

import com.nexlink.social.core.invite.InviteCode

/**
 * The acceptance gate's state machine — §9.6.
 *
 * Kept separate from the Activity, and pure, for two reasons: the Activity
 * rebuilds its whole view hierarchy on every step change (the pattern
 * established by `BridgeSetupActivity`), so state cannot live in the views; and
 * the gate's rules are exactly the sort of thing that should be tested without
 * an emulator (§34.3).
 *
 * The rules this enforces, each from §9.6.1:
 *  - the invite code must validate before the gate starts;
 *  - a checkbox cannot be ticked until its document has been opened;
 *  - all three boxes, separately, none pre-ticked;
 *  - the age check passes on a date, not a checkbox;
 *  - **the date of birth is not retained** (§9.6.2) — [toOutcome] returns a
 *    boolean and the entered date dies with this object.
 */
class AcceptanceGateState {

    enum class Step {
        INVITE,          // not part of the gate proper; the door to it
        WHAT_THIS_IS,    // §9.6.1 screen 1
        WHAT_WE_SEE,     // screen 2 — the most important one
        YOUR_DATA,       // screen 3
        TERMS,           // screen 4
        AGE;             // screen 5

        companion object {
            /** Screens 1-5. INVITE is excluded from the count shown to the user. */
            val gateSteps = listOf(WHAT_THIS_IS, WHAT_WE_SEE, YOUR_DATA, TERMS, AGE)
        }
    }

    var step: Step = Step.INVITE
        private set

    /** Normalised, never formatted. Empty until the invite step passes. */
    var inviteCode: String = ""
        private set

    private var skipInviteStep = false

    var termsOpened = false;   private set
    var privacyOpened = false; private set

    var termsChecked = false;   private set
    var privacyChecked = false; private set
    var warrantyChecked = false; private set

    var dobDay: Int? = null;   private set
    var dobMonth: Int? = null; private set
    var dobYear: Int? = null;  private set

    // ── invite ───────────────────────────────────────────────────────────────

    /**
     * Start the gate with a code that has already been validated and redeemed
     * elsewhere (§22.10). The gate proper is §9.6.1's five consent screens; the
     * invite step is the door, and when the caller has already opened it there
     * is no reason to ask twice.
     */
    fun startWithRedeemedInvite(normalisedCode: String) {
        inviteCode = normalisedCode
        skipInviteStep = true
        step = Step.WHAT_THIS_IS
    }

    fun submitInvite(raw: String): Boolean {
        if (!InviteCode.isValid(raw)) return false
        inviteCode = InviteCode.normalise(raw)
        step = Step.WHAT_THIS_IS
        return true
    }

    // ── terms ────────────────────────────────────────────────────────────────

    fun openTerms() { termsOpened = true }
    fun openPrivacy() { privacyOpened = true }

    /**
     * §9.6.1 originally required each document to be opened *before* its
     * checkbox would enable. **Removed 2026-09-16 at the operator's
     * instruction** (§9.6.4).
     *
     * `termsOpened` / `privacyOpened` are still tracked, because whether the
     * documents were opened is worth knowing and is what a future version would
     * need to re-impose the rule. They no longer gate the tick.
     *
     * The Boolean return is kept so callers need not change and so re-imposing
     * the rule is a one-line change here rather than a signature change.
     */
    fun setTermsChecked(checked: Boolean): Boolean {
        termsChecked = checked
        return true
    }

    fun setPrivacyChecked(checked: Boolean): Boolean {
        privacyChecked = checked
        return true
    }

    /** No document to open for this one — it is a statement, not a policy. */
    fun setWarrantyChecked(checked: Boolean) { warrantyChecked = checked }

    val termsComplete: Boolean get() = termsChecked && privacyChecked && warrantyChecked

    // ── age ──────────────────────────────────────────────────────────────────

    fun setDob(day: Int?, month: Int?, year: Int?) {
        dobDay = day; dobMonth = month; dobYear = year
    }

    fun checkAge(): AgeCheck.Result = AgeCheck.check(dobDay, dobMonth, dobYear)

    // ── navigation ───────────────────────────────────────────────────────────

    /** @return false when the current step is not complete, so the UI can object. */
    fun advance(): Boolean {
        when (step) {
            Step.INVITE -> return false // only submitInvite() leaves this step
            Step.WHAT_THIS_IS -> step = Step.WHAT_WE_SEE
            Step.WHAT_WE_SEE -> step = Step.YOUR_DATA
            Step.YOUR_DATA -> step = Step.TERMS
            Step.TERMS -> { if (!termsComplete) return false; step = Step.AGE }
            Step.AGE -> return false // only toOutcome() completes the gate
        }
        return true
    }

    /** @return false at the first step, where back means leaving the gate. */
    fun back(): Boolean {
        step = when (step) {
            Step.INVITE -> return false
            // When the code came in already redeemed there is no invite screen
            // to go back to — back leaves the gate, and the caller decides what
            // that means (§9.6: abandoning creates no account).
            Step.WHAT_THIS_IS -> if (skipInviteStep) return false else Step.INVITE
            Step.WHAT_WE_SEE -> Step.WHAT_THIS_IS
            Step.YOUR_DATA -> Step.WHAT_WE_SEE
            Step.TERMS -> Step.YOUR_DATA
            Step.AGE -> Step.TERMS
        }
        return true
    }

    /** 1-based, for "Step 3 of 5". INVITE returns 0. */
    fun displayStep(): Int = Step.gateSteps.indexOf(step) + 1

    // ── completion ───────────────────────────────────────────────────────────

    /**
     * The gate's entire output. Note what is **not** in it: the date of birth.
     * §9.6.2 — *"The date of birth is not stored. Only the boolean outcome."*
     *
     * @return null if the gate is not actually complete.
     */
    fun toOutcome(): Outcome? {
        if (step != Step.AGE) return null
        if (!termsComplete) return null
        if (checkAge() !is AgeCheck.Result.Ok) return null
        return Outcome(inviteCode = inviteCode, ageConfirmed = true)
    }

    /**
     * Handed to the registration call. The invite code travels because the
     * account cannot be created without redeeming it (§2.8, invariant 3); the
     * token hash and the server timestamp are added server-side (§9.6.2).
     */
    data class Outcome(
        val inviteCode: String,
        val ageConfirmed: Boolean
    )
}
