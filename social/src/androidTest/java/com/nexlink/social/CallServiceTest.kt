package com.nexlink.social

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.nexlink.social.rtc.calls.CallService
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §15.9 — the foreground service contract, on a real device.
 *
 * This cannot be a unit test and should not be. §15's entire subject is what
 * the *platform* does to a process that mishandles `startForegroundService`,
 * and the platform is the thing under test. §34.10 already records that the
 * emulator is unusable here.
 *
 * The failure being guarded against is not an exception in our code. It is the
 * platform killing the process seconds later for not having promoted — which a
 * unit test cannot observe and a passing build says nothing about.
 */
@RunWith(AndroidJUnit4::class)
class CallServiceTest {

    /**
     * RECORD_AUDIO is not optional scenery here.
     *
     * Without it the first run of this test failed exactly as §15.2.1
     * describes: FGS start allowed, no promotion, service killed. On Android
     * 14+ a `microphone` foreground start throws unless RECORD_AUDIO is
     * *granted*, and declaring it in the manifest is not granting it.
     */
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.RECORD_AUDIO
    )

    private val ctx: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun ongoingCallNotification(): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.activeNotifications.any { it.id == 7301 }
    }

    /**
     * Start it, and assert the promotion actually happened.
     *
     * The notification is the observable proof: a service that reached
     * `startForeground` has one, and a service that did not has been killed.
     */
    @Test
    fun startsAndPromotesToForeground() {
        CallService.start(ctx, "!room:example")
        Thread.sleep(3_000)
        assertTrue(
            "no ongoing call notification — the service never promoted, and the " +
                "platform will kill the process for it (§15.2.1)",
            ongoingCallNotification()
        )
        CallService.stop(ctx)
        Thread.sleep(2_000)
    }

    /**
     * §15.2.1's stopCleanly case — the one that looks pointless.
     *
     * Started with no room id, the service decides not to run. It still owes
     * the platform a promotion on that path, so it must promote, then stop, and
     * leave nothing behind. A bare `stopSelf()` here is what kills the process.
     */
    @Test
    fun startingWithNothingToDoStopsCleanlyAndLeavesNoNotification() {
        val i = android.content.Intent(ctx, CallService::class.java)
        ctx.startForegroundService(i)
        Thread.sleep(3_000)
        assertTrue(
            "the service should have stopped and removed its notification",
            !ongoingCallNotification()
        )
    }

    /** Stopping removes the notification — §15.2.1's STOP_FOREGROUND_REMOVE. */
    @Test
    fun stoppingRemovesTheNotification() {
        CallService.start(ctx, "!room:example")
        Thread.sleep(2_500)
        CallService.stop(ctx)
        Thread.sleep(2_500)
        assertTrue(
            "an ongoing call notification outlived the call",
            !ongoingCallNotification()
        )
    }
}
