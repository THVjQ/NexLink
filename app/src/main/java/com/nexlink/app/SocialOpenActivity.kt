package com.nexlink.app

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import com.nexlink.app.db.DeepLinkHelper
import com.nexlink.app.db.NotificationStore
import com.nexlink.app.services.NexLinkNotificationListener

/**
 * Invisible trampoline for NexLink social-notification taps.
 *
 * Android 12+ blocks activity starts from a BroadcastReceiver triggered by a notification tap.
 * An Activity IS allowed to start other activities, so routing here fixes the silent no-op.
 * No UI — does work in onCreate and finishes immediately.
 */
class SocialOpenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val platform = intent.getStringExtra("platform")
        val sender   = intent.getStringExtra("sender") ?: ""
        val key      = intent.getStringExtra("notification_key") ?: ""
        val notifId  = intent.getIntExtra("notif_id", 0)

        if (platform != null) NotificationStore.markRead(platform, sender)
        if (notifId != 0) {
            getSystemService(NotificationManager::class.java).cancel(notifId)
        }

        // 1) Fire the social app's own contentIntent → opens the exact conversation.
        //    Peek (not pop) so the inbox conversation row can reuse the same intent later.
        var opened = NexLinkNotificationListener.fireContentIntent(this, key)

        // 2) Fallback: open the social app (not exact chat — happens if listener was restarted).
        if (!opened && platform != null) {
            opened = try { DeepLinkHelper.openPlatform(this, platform) }
                     catch (_: Exception) { false }
        }

        // 3) Last resort: open NexLink inbox.
        if (!opened) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("navigate_to", R.id.nav_inbox)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }

        finish()
    }
}
