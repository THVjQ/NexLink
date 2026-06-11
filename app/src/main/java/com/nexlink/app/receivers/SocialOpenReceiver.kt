package com.nexlink.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexlink.app.MainActivity
import com.nexlink.app.R
import com.nexlink.app.db.NotificationStore
import com.nexlink.app.services.NexLinkNotificationListener

/** Fired when the user taps a NexLink social notification. Marks the thread read
 *  and opens NexLink on the Inbox page. */
class SocialOpenReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val platform = intent.getStringExtra("platform") ?: return
        val sender   = intent.getStringExtra("sender")   ?: ""
        val key      = intent.getStringExtra("notification_key") ?: ""

        NotificationStore.markRead(platform, sender)
        NexLinkNotificationListener.popContentIntent(key)  // clear cached intent

        ctx.startActivity(Intent(ctx, MainActivity::class.java).apply {
            putExtra("navigate_to", R.id.nav_inbox)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }
}
