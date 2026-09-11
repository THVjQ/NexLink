package com.nexlink.social.core.invite

import org.junit.Assert.*
import org.junit.Test

/**
 * §9.3, and §34.3 which lists this first: pure function, many edge cases, used
 * at the front door of the service.
 */
class InviteCodeTest {

    @Test fun `alphabet is Crockford base32`() {
        assertEquals(32, InviteCode.ALPHABET.length)
        listOf('I', 'L', 'O', 'U').forEach {
            assertFalse("$it must be excluded (§9.3)", it in InviteCode.ALPHABET)
        }
    }

    @Test fun `hyphens are cosmetic`() {
        assertEquals("X7K29QMF3BTD", InviteCode.normalise("X7K2-9QMF-3BTD"))
        assertTrue(InviteCode.isValid("X7K29QMF3BTD"))
        assertTrue(InviteCode.isValid("X7K2-9QMF-3BTD"))
    }

    @Test fun `case insensitive on entry`() {
        assertEquals("X7K29QMF3BTD", InviteCode.normalise("x7k2-9qmf-3btd"))
        assertTrue(InviteCode.isValid("x7k2 9qmf 3btd"))
    }

    @Test fun `whitespace is stripped — a read-aloud code arrives with it`() {
        assertTrue(InviteCode.isValid(" X7K2 9QMF 3BTD "))
        assertTrue(InviteCode.isValid("X7K2\t9QMF\n3BTD"))
    }

    @Test fun `confusable characters fold per Crockford`() {
        // I and L are 1; O is 0. This is the whole reason for the alphabet choice.
        assertEquals("1", InviteCode.normalise("I"))
        assertEquals("1", InviteCode.normalise("l"))
        assertEquals("0", InviteCode.normalise("O"))
        assertEquals("0", InviteCode.normalise("o"))
        assertTrue("a code typed with letter-O must still validate",
            InviteCode.isValid("X7K2-9QMF-3BTO".replace('O', 'O')))
    }

    @Test fun `U does not fold — it is excluded deliberately`() {
        // Guessing what a U was meant to be would be worse than rejecting it.
        assertEquals('U', InviteCode.normalise("U").single())
        assertFalse(InviteCode.isValid("U7K29QMF3BTD"))
        assertTrue(InviteCode.validate("U7K29QMF3BTD") is InviteCodeError.ExcludedCharacter)
    }

    @Test fun `length is exactly twelve`() {
        assertTrue(InviteCode.validate("X7K29QMF3BT") is InviteCodeError.TooShort)
        assertTrue(InviteCode.validate("X7K29QMF3BTDD") is InviteCodeError.TooLong)
        assertNull(InviteCode.validate("X7K29QMF3BTD"))
    }

    @Test fun `empty is reported as empty, not as too short`() {
        assertTrue(InviteCode.validate("") is InviteCodeError.Empty)
        assertTrue(InviteCode.validate("---") is InviteCodeError.Empty)
    }

    @Test fun `illegal characters are named so the message can be useful`() {
        val e = InviteCode.validate("X7K2-9QMF-3BT!")
        assertEquals('!', (e as InviteCodeError.IllegalCharacter).char)
    }

    @Test fun `format groups in fours`() {
        assertEquals("X7K2-9QMF-3BTD", InviteCode.format("X7K29QMF3BTD"))
    }

    @Test fun `format is safe on a partial code as the user types`() {
        assertEquals("X7K2-9Q", InviteCode.format("X7K29Q"))
        assertEquals("X", InviteCode.format("X"))
        assertEquals("", InviteCode.format(""))
    }

    @Test fun `round trip`() {
        val typed = "x7k2 9qmf 3btd"
        val normalised = InviteCode.normalise(typed)
        assertTrue(InviteCode.isValid(normalised))
        assertEquals("X7K2-9QMF-3BTD", InviteCode.format(normalised))
    }
}
