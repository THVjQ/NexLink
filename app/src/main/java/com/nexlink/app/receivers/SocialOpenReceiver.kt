package com.nexlink.app.receivers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexlink.app.db.DeepLinkHelper
import com.nexlink.app.db.NotificationStore
import com.nexlink.app.services.NexLinkNotificationListener

/** Fired when the user taps a NexLink social notification. Marks the thread read
 *  and opens the social app's specific conversation (or the app if unavailable). */
class SocialOpenReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val platform = intent.getStringExtra("platform") ?: return
        val sender   = intent.getStringExtra("sender")   ?: ""
        val key      = intent.getStringExtra("notification_key") ?: ""

        NotificationStore.markRead(platform, sender)

        val pi = NexLinkNotificationListener.popContentIntent(key)
        if (pi != null) {
            try {
                pi.send()
                return
            } catch (_: PendingIntent.CanceledException) { /* intent expired, fall through */ }
        }
        DeepLinkHelper.openPlatform(ctx, platform)
    }
}
