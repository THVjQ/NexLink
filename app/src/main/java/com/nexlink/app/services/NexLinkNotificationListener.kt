package com.nexlink.app.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.nexlink.app.App
import com.nexlink.app.MainActivity
import com.nexlink.app.R
import com.nexlink.app.db.NotificationStore
import com.nexlink.app.db.SocialNotification

class NexLinkNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg !in NotificationStore.watchedPackages) return

        val extras = sbn.notification?.extras ?: return
        val title  = extras.getCharSequence("android.title")?.toString() ?: return
        val text   = extras.getCharSequence("android.text")?.toString()  ?: return
        if (title.isBlank() || text.isBlank()) return

        val n = SocialNotification(
            id          = "${sbn.key}",
            platform    = NotificationStore.platform(pkg),
            packageName = pkg,
            sender      = title,
            text        = text,
            timestamp   = sbn.postTime
        )
        NotificationStore.add(n)
        postNexLinkNotification(n)
    }

    // Don't remove messages when the user swipes the source app's notification away —
    // they should stay in the NexLink inbox until dismissed from within the app.
    override fun onNotificationRemoved(sbn: StatusBarNotification) { /* intentionally empty */ }

    private fun postNexLinkNotification(n: SocialNotification) {
        val ctx = applicationContext
        val pi = PendingIntent.getActivity(
            ctx, n.id.hashCode(),
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = when (n.platform) {
            "WhatsApp"  -> R.drawable.ic_inbox
            "Signal"    -> R.drawable.ic_inbox
            "Telegram"  -> R.drawable.ic_inbox
            "Messenger" -> R.drawable.ic_inbox
            else        -> R.drawable.ic_inbox
        }
        NotificationCompat.Builder(ctx, App.CH_SOCIAL)
            .setSmallIcon(icon)
            .setContentTitle("${n.platform} · ${n.sender}")
            .setContentText(n.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
            .also {
                ctx.getSystemService(NotificationManager::class.java)
                    .notify("nexlink_social_${n.id}".hashCode(), it)
            }
    }
}
