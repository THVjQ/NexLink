package com.nexlink.social.ui.onboarding

import java.util.Calendar

/**
 * §9.6.1 screen 5 — age confirmation.
 *
 * A date of birth, not a yes/no checkbox: *"a checkbox is trivially defeated and
 * provides no defensible record."*
 *
 * **The date itself is never stored.** This object turns a date into a boolean
 * and the date is discarded (§9.6.2, §32.2). Nothing here writes anywhere.
 *
 * Pure and Android-free so it can be unit tested (§34.3).
 */
object AgeCheck {

    /**
     * **Decided 2026-09-15: sixteen.** §2.7 had left this open pending Q3
     * (§37.1), with 16 as the placeholder because §9.6.1 calls it *"the safe
     * default for EU users absent a specific analysis"*. The operator has now
     * chosen it, and the Terms of Service §3.1 says sixteen in as many words.
     *
     * The two must not drift: this constant is what the gate enforces and the
     * Terms are what the user agreed to. Changing one without the other makes
     * the product lie about itself.
     */
    const val MINIMUM_AGE = 16

    sealed interface Result {
        data object Ok : Result
        data object Incomplete : Result
        data object NotADate : Result
        data object InFuture : Result
        data class TooYoung(val minimumAge: Int) : Result
    }

    /**
     * @param today injected so the test suite does not depend on the wall clock.
     */
    fun check(
        day: Int?,
        month: Int?,
        year: Int?,
        today: Calendar = Calendar.getInstance()
    ): Result {
        if (day == null || month == null || year == null) return Result.Incomplete
        if (month !in 1..12) return Result.NotADate
        if (year < 1900 || year > today.get(Calendar.YEAR)) {
            return if (year > today.get(Calendar.YEAR)) Result.InFuture else Result.NotADate
        }
        if (day < 1 || day > daysInMonth(month, year)) return Result.NotADate

        val birth = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day)
        }
        if (birth.after(today)) return Result.InFuture

        return if (fullYearsBetween(birth, today) >= MINIMUM_AGE) Result.Ok
        else Result.TooYoung(MINIMUM_AGE)
    }

    private fun fullYearsBetween(birth: Calendar, today: Calendar): Int {
        var years = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        // Not had this year's birthday yet.
        if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) years--
        return years
    }

    private fun daysInMonth(month: Int, year: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeap(year)) 29 else 28
        else -> 0
    }

    private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
}
