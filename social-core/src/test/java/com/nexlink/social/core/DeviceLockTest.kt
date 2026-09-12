package com.nexlink.social.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §12.4.4 — the warning that makes the store-key trade honest. */
class DeviceLockTest {

    @Test
    fun `no warning when a screen lock is set`() {
        assertNull(DeviceLock.warningFor(lockIsSet = true))
    }

    @Test
    fun `warns when there is no screen lock`() {
        assertNotNull(DeviceLock.warningFor(lockIsSet = false))
    }

    /**
     * §9.6.1's standard for security copy: say what an attacker gains, not
     * which setting is off. "No screen lock detected" tells a user nothing
     * about whether to care.
     */
    @Test
    fun `the warning says what an attacker gains, and admits the app cannot fix it`() {
        val w = DeviceLock.warningFor(lockIsSet = false)!!
        assertTrue("must name the consequence", w.contains("read everything"))
        assertTrue("must cover the USB case", w.contains("connects it to a computer"))
        assertTrue(
            "must not imply the app can solve it",
            w.contains("not something this app can do for you")
        )
    }

    /**
     * The claim has to stay accurate: messages ARE encrypted in transit and on
     * the server (§2.8 #1), and it is only the on-device copy the screen lock
     * protects. Overstating the danger here would be the mirror of the
     * overclaiming §9.6.1 forbids.
     */
    @Test
    fun `the warning does not overstate the danger`() {
        val w = DeviceLock.warningFor(lockIsSet = false)!!
        assertTrue(w.contains("encrypted in transit and on the server"))
    }
}
