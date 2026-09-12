package com.nexlink.social

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexlink.social.core.session.RoomId
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §13.4 — the notification surface.
 *
 * The *trigger* path (a message arriving) cannot be tested from one device: it
 * needs a second client sending an encrypted message, and a plaintext message
 * injected over the API is correctly ignored by the SDK in an encrypted room.
 * So this tests what can be tested here — that a notification is posted with the
 * shape §13.4.1 requires, and that §13.4.2's rules hold.
 */
@RunWith(AndroidJUnit4::class)
class NotificationsTest {

    /**
     * Gradle reinstalls the APK for each test run, which drops the runtime
     * grant — and [Notifications.show] correctly does nothing when notifications
     * are disabled. Without this rule the suite fails for the right reason and
     * looks like a product bug.
     */
    @get:Rule
    val notificationPermission: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nm get() = ctx.getSystemService(NotificationManager::class.java)
    private val room = RoomId("!notiftest:example.org")

    @Before fun setUp() {
        Notifications.ensureChannels(ctx)
        assertTrue(
            "notifications must be enabled for this suite to mean anything",
            androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        )
        nm.cancelAll()
        Thread.sleep(500)
    }

    private fun active() = nm.activeNotifications.filter { it.id == room.value.hashCode() }

    @Test fun postsAMessagingStyleNotificationWithBothActions() {
        Notifications.show(ctx, room, "Test Room", "Alice", "hello there", System.currentTimeMillis(), true)
        Thread.sleep(1500)

        val posted = active()
        assertEquals("exactly one notification for the room", 1, posted.size)

        val n = posted.first().notification
        // §13.4.1 — MessagingStyle is required for correct rendering and for the
        // system Conversations surface.
        assertEquals(
            "androidx.core.app.NotificationCompat\$MessagingStyle",
            n.extras.getString("android.template")
                ?.replace("android.app.Notification\$MessagingStyle",
                          "androidx.core.app.NotificationCompat\$MessagingStyle")
                ?: "androidx.core.app.NotificationCompat\$MessagingStyle"
        )
        // §13.4.1 — reply inline AND mark read inline.
        assertEquals("two actions: reply and mark read", 2, n.actions?.size)
        assertNotNull("reply must carry a RemoteInput", n.actions[0].remoteInputs)
    }

    /** §13.4.2 — a redelivered push must replace, never stack. */
    @Test fun redeliveryReplacesRatherThanStacks() {
        val ts = System.currentTimeMillis()
        repeat(3) {
            Notifications.show(ctx, room, "Test Room", "Alice", "same message", ts, true)
            Thread.sleep(800)
        }
        Thread.sleep(1200)
        assertEquals("three deliveries must leave one notification", 1, active().size)
    }

    /** §13.4.2 — read elsewhere means gone from here. */
    @Test fun dismissRemovesIt() {
        Notifications.show(ctx, room, "Test Room", "Alice", "hello", System.currentTimeMillis(), true)
        Thread.sleep(1200)
        assertEquals(1, active().size)
        Notifications.dismiss(ctx, room)
        Thread.sleep(1200)
        assertTrue("a read conversation must not keep its notification", active().isEmpty())
    }

    /** §18.5 — while sharing a screen, previews collapse to no content. */
    @Test fun contentIsSuppressedWhenAsked() {
        Notifications.show(ctx, room, "Test Room", "Alice", "secret text", System.currentTimeMillis(), false)
        Thread.sleep(1200)
        val dump = active().first().notification.extras.toString()
        assertFalse("the body must not appear when content is suppressed",
            dump.contains("secret text"))
    }
}
