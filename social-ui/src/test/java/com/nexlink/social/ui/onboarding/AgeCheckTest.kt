package com.nexlink.social.ui.onboarding

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/** §9.6.1 screen 5. The clock is injected so these do not rot. */
class AgeCheckTest {

    private fun today(y: Int, m: Int, d: Int): Calendar =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d) }

    private val now = today(2026, 9, 11)

    @Test fun `comfortably old enough`() {
        assertEquals(AgeCheck.Result.Ok, AgeCheck.check(1, 1, 1990, now))
    }

    @Test fun `exactly the minimum age today`() {
        assertEquals(AgeCheck.Result.Ok, AgeCheck.check(11, 9, 2026 - AgeCheck.MINIMUM_AGE, now))
    }

    @Test fun `one day short of the minimum age`() {
        val r = AgeCheck.check(12, 9, 2026 - AgeCheck.MINIMUM_AGE, now)
        assertTrue(r is AgeCheck.Result.TooYoung)
    }

    @Test fun `clearly too young`() {
        assertTrue(AgeCheck.check(1, 1, 2020, now) is AgeCheck.Result.TooYoung)
    }

    @Test fun `incomplete is distinguished from invalid`() {
        assertEquals(AgeCheck.Result.Incomplete, AgeCheck.check(null, 1, 1990, now))
        assertEquals(AgeCheck.Result.Incomplete, AgeCheck.check(1, null, 1990, now))
        assertEquals(AgeCheck.Result.Incomplete, AgeCheck.check(1, 1, null, now))
    }

    @Test fun `a date in the future is named as such`() {
        assertEquals(AgeCheck.Result.InFuture, AgeCheck.check(1, 1, 2030, now))
    }

    @Test fun `impossible dates are rejected`() {
        assertEquals(AgeCheck.Result.NotADate, AgeCheck.check(32, 1, 1990, now))
        assertEquals(AgeCheck.Result.NotADate, AgeCheck.check(1, 13, 1990, now))
        assertEquals(AgeCheck.Result.NotADate, AgeCheck.check(31, 4, 1990, now))
        assertEquals(AgeCheck.Result.NotADate, AgeCheck.check(0, 1, 1990, now))
    }

    @Test fun `leap years`() {
        assertEquals(AgeCheck.Result.Ok, AgeCheck.check(29, 2, 2000, now))
        assertEquals(AgeCheck.Result.NotADate, AgeCheck.check(29, 2, 1900, now))  // not a leap year
        assertEquals(AgeCheck.Result.Ok, AgeCheck.check(29, 2, 1996, now))
    }
}
