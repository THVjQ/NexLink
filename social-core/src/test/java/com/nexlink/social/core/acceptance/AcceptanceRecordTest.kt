package com.nexlink.social.core.acceptance

import org.junit.Assert.*
import org.junit.Test

class AcceptanceRecordTest {

    private fun record(terms: String = PolicyVersions.TERMS, privacy: String = PolicyVersions.PRIVACY) =
        AcceptanceRecord(
            mxid = "@a:example",
            acceptedAt = 1_757_000_000_000L,
            termsVersion = terms,
            privacyVersion = privacy,
            ageConfirmed = true,
            inviteTokenHash = "0".repeat(64),
            clientVersion = 1
        )

    @Test fun `current versions need no re-acceptance`() {
        assertFalse(PolicyVersions.requiresReAcceptance(record()))
    }

    @Test fun `a terms bump triggers re-acceptance`() {
        assertTrue(PolicyVersions.requiresReAcceptance(record(terms = "0.0.9")))
    }

    @Test fun `a privacy bump triggers re-acceptance`() {
        assertTrue(PolicyVersions.requiresReAcceptance(record(privacy = "0.0.9")))
    }

    /**
     * §9.6.2 and §32.2: the DOB is never retained, only the boolean outcome.
     * This test exists to fail loudly if someone adds the field back.
     */
    @Test fun `the record holds no date of birth`() {
        val fields = AcceptanceRecord::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse("§9.6.2 — the DOB must not be stored, only ageConfirmed",
            fields.any { "birth" in it || it == "dob" })
        assertTrue(fields.contains("ageconfirmed"))
    }
}
