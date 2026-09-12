package com.nexlink.social

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexlink.social.core.session.RoomId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §16.2 — a probe for the Level 0 claim.
 *
 * Posts one message notification and holds it long enough for NexLink's
 * notification listener to see it and for a human (or a script) to check
 * NexLink's unified inbox.
 *
 * This exists because §1.4.4's claim — that the companion app "appears in the
 * unified inbox on day one, with no integration code written at all" — turned
 * out to be false, and a claim that load-bearing deserves a repeatable check.
 */
@RunWith(AndroidJUnit4::class)
class Level0ProbeTest {

    @get:Rule
    val notificationPermission: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Test fun postAndHold() {
        val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
        Notifications.ensureChannels(ctx)
        Notifications.show(
            context = ctx,
            roomId = RoomId("!level0probe:nexlink.thvjq.com.au"),
            roomTitle = "Level 0 probe",
            senderName = "Alice",
            body = "does NexLink see this",
            timestamp = System.currentTimeMillis(),
            showContent = true
        )
        // Hold the process so the notification is not reaped with instrumentation.
        Thread.sleep(90_000)
    }
}
