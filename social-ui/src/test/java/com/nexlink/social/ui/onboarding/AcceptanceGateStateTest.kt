package com.nexlink.social.ui.onboarding

import com.nexlink.social.ui.onboarding.AcceptanceGateState.Step
import org.junit.Assert.*
import org.junit.Test

/** §9.6's rules, tested where they live. */
class AcceptanceGateStateTest {

    private val validCode = "X7K2-9QMF-3BTD"

    private fun atTerms() = AcceptanceGateState().apply {
        submitInvite(validCode)
        advance() // WHAT_WE_SEE
        advance() // YOUR_DATA
        advance() // TERMS
    }

    @Test fun `a bad invite does not open the gate`() {
        val s = AcceptanceGateState()
        assertFalse(s.submitInvite("nope"))
        assertEquals(Step.INVITE, s.step)
    }

    @Test fun `a good invite opens the gate and is stored normalised`() {
        val s = AcceptanceGateState()
        assertTrue(s.submitInvite("x7k2 9qmf 3btd"))
        assertEquals(Step.WHAT_THIS_IS, s.step)
        assertEquals("X7K29QMF3BTD", s.inviteCode)
    }

    @Test fun `the gate is five screens`() {
        assertEquals(5, Step.gateSteps.size)
        val s = AcceptanceGateState().apply { submitInvite(validCode) }
        assertEquals(1, s.displayStep())
        s.advance(); assertEquals(2, s.displayStep())
        s.advance(); assertEquals(3, s.displayStep())
        s.advance(); assertEquals(4, s.displayStep())
    }

    /** §9.6.1 — "each requiring the user to have opened it before the checkbox enables". */
    @Test fun `a checkbox cannot be ticked before its document is opened`() {
        val s = atTerms()
        assertFalse(s.setTermsChecked(true))
        assertFalse(s.termsChecked)

        s.openTerms()
        assertTrue(s.setTermsChecked(true))
        assertTrue(s.termsChecked)
    }

    @Test fun `the warranty statement has no document to open`() {
        val s = atTerms()
        s.setWarrantyChecked(true)
        assertTrue(s.warrantyChecked)
    }

    /** §9.6.1 — "A single combined checkbox is legally weaker and is not used." */
    @Test fun `all three boxes are required`() {
        val s = atTerms().apply { openTerms(); openPrivacy() }

        s.setTermsChecked(true);   assertFalse(s.termsComplete)
        s.setPrivacyChecked(true); assertFalse(s.termsComplete)
        s.setWarrantyChecked(true); assertTrue(s.termsComplete)
    }

    @Test fun `cannot advance past terms until all three are ticked`() {
        val s = atTerms().apply { openTerms(); openPrivacy() }
        s.setTermsChecked(true); s.setPrivacyChecked(true)
        assertFalse(s.advance())
        assertEquals(Step.TERMS, s.step)

        s.setWarrantyChecked(true)
        assertTrue(s.advance())
        assertEquals(Step.AGE, s.step)
    }

    @Test fun `unticking a box re-blocks the gate`() {
        val s = completeToAge()
        s.setPrivacyChecked(false)
        assertFalse(s.termsComplete)
        assertNull(s.toOutcome())
    }

    @Test fun `back walks out of the gate at the first step`() {
        val s = AcceptanceGateState().apply { submitInvite(validCode) }
        assertTrue(s.back())
        assertEquals(Step.INVITE, s.step)
        assertFalse("back from INVITE leaves the gate", s.back())
    }

    // ── completion ───────────────────────────────────────────────────────────

    private fun completeToAge() = atTerms().apply {
        openTerms(); openPrivacy()
        setTermsChecked(true); setPrivacyChecked(true); setWarrantyChecked(true)
        advance()
        setDob(1, 1, 1990)
    }

    @Test fun `a complete gate produces an outcome`() {
        val outcome = completeToAge().toOutcome()
        assertNotNull(outcome)
        assertEquals("X7K29QMF3BTD", outcome!!.inviteCode)
        assertTrue(outcome.ageConfirmed)
    }

    @Test fun `an underage date produces no outcome`() {
        val s = completeToAge()
        s.setDob(1, 1, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - 10)
        assertNull(s.toOutcome())
    }

    @Test fun `an incomplete date produces no outcome`() {
        val s = completeToAge()
        s.setDob(null, null, null)
        assertNull(s.toOutcome())
    }

    /**
     * §9.6.2 and §32.2 — the outcome carries a boolean, never the date. This
     * test exists to fail if someone widens Outcome.
     */
    @Test fun `the outcome carries no date of birth`() {
        val fields = AcceptanceGateState.Outcome::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse("§9.6.2 — the DOB must not leave the gate",
            fields.any { "birth" in it || it == "dob" || it == "day" || it == "month" || it == "year" })
    }
}
