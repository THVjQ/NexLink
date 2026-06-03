package com.nexlink.app.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nexlink.app.db.NotificationStore
import com.nexlink.app.db.SocialNotification

class NexLinkNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg !in NotificationStore.watchedPackages) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: return
        val text  = extras.getCharSequence("android.text")?.toString()  ?: return
        if (title.isBlank() || text.isBlank()) return

        NotificationStore.add(
            SocialNotification(
                id          = "${sbn.key}",
                platform    = NotificationStore.platform(pkg),
                packageName = pkg,
                sender      = title,
                text        = text,
                timestamp   = sbn.postTime
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationStore.remove("${sbn.key}")
    }
}
