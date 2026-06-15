package com.nexlink.app.receivers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexlink.app.MainActivity
import com.nexlink.app.R
import com.nexlink.app.db.NotificationStore
import com.nexlink.app.services.NexLinkNotificationListener

/** Fired when the user taps a NexLink social notification.
 *  Opens the exact conversation in the social app (using the original contentIntent),
 *  marks the thread read in NexLink, and cancels the notification. */
class SocialOpenReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val platform = intent.getStringExtra("platform") ?: return
        val sender   = intent.getStringExtra("sender")   ?: ""
        val key      = intent.getStringExtra("notification_key") ?: ""
        val notifId  = intent.getIntExtra("notif_id", 0)

        NotificationStore.markRead(platform, sender)

        // Dismiss the NexLink notification explicitly (belt-and-suspenders alongside setAutoCancel)
        if (notifId != 0) {
            ctx.getSystemService(NotificationManager::class.java).cancel(notifId)
        }

        // Try to open the original social app's conversation directly
        val socialPi = NexLinkNotificationListener.popContentIntent(key)
        if (socialPi != null) {
            try {
                socialPi.send()
                return
            } catch (_: PendingIntent.CanceledException) { /* social app's intent expired, fall through */ }
        }

        // Fallback: navigate to NexLink inbox
        ctx.startActivity(Intent(ctx, MainActivity::class.java).apply {
            putExtra("navigate_to", R.id.nav_inbox)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }
}
