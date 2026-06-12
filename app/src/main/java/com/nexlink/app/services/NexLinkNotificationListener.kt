package com.nexlink.app.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.nexlink.app.App
import com.nexlink.app.R
import com.nexlink.app.db.NotificationPrefs
import com.nexlink.app.db.NotificationStore
import com.nexlink.app.db.SocialNotification
import com.nexlink.app.receivers.SmsNotifier

class NexLinkNotificationListener : NotificationListenerService() {

    companion object {
        // Original contentIntents from social apps — keyed by StatusBarNotification.key.
        // Stored so tapping the NexLink re-post opens the exact conversation.
        private val contentIntents = mutableMapOf<String, android.app.PendingIntent>()

        fun popContentIntent(key: String): android.app.PendingIntent? =
            contentIntents.remove(key)

        // Common system SMS/MMS apps — suppressed when "Only notify via NexLink" is on.
        // NexLink itself is the SMS handler and re-posts its own notification, so these
        // are duplicates.
        private val SMS_PACKAGES = setOf(
            "com.samsung.android.messaging",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.android.messaging",
            "com.hihonor.message",
            "com.sonyericsson.conversations",
            "com.htc.sense.mms"
        )

        // Call-related packages — NEVER suppressed regardless of any setting.
        private val CALL_PACKAGES = setOf(
            "com.android.server.telecom",
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.android.incallui",
            "com.android.phone"
        )
    }

    private fun isMediaMessage(extras: android.os.Bundle): Boolean {
        val text = extras.getCharSequence("android.text")?.toString()?.lowercase() ?: return false
        return text.contains("voice message") || text.contains("audio message") ||
               text.contains("voice note") || text.contains("🎵") || text.contains("🎤")
    }

    private fun isReactionNotification(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("reacted") || lower.contains("liked") ||
               lower.contains("loved") || lower.contains("laughed") ||
               lower.contains("emphasized") ||
               text.trimStart().let { t ->
                   t.startsWith("❤") || t.startsWith("👍") || t.startsWith("😂") ||
                   t.startsWith("😮") || t.startsWith("😢") || t.startsWith("🔥") ||
                   t.startsWith("🎉") || t.startsWith("👏")
               }
    }

    private fun isCallNotification(notification: android.app.Notification, extras: android.os.Bundle): Boolean {
        val cat = notification.category
        if (cat == android.app.Notification.CATEGORY_CALL ||
            cat == android.app.Notification.CATEGORY_MISSED_CALL) return true
        val text = extras.getCharSequence("android.text")?.toString()?.lowercase() ?: return false
        val title = extras.getCharSequence("android.title")?.toString()?.lowercase() ?: ""
        return text.contains("missed call") || text.contains("incoming call") ||
               title.contains("missed call") || text.contains("calling")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val ctx = applicationContext

        // Never touch call notifications
        if (pkg in CALL_PACKAGES) return

        // Suppress raw SMS app notifications when "Only notify via NexLink" is enabled —
        // NexLink's SmsReceiver already posts its own notification for the same message.
        if (pkg in SMS_PACKAGES) {
            if (NotificationPrefs.isSuppressSourceEnabled(ctx)) cancelNotification(sbn.key)
            return
        }

        if (pkg !in NotificationStore.watchedPackages) return

        val extras = sbn.notification?.extras ?: return
        val title  = extras.getCharSequence("android.title")?.toString() ?: return
        val text   = extras.getCharSequence("android.text")?.toString()  ?: return
        if (title.isBlank() || text.isBlank()) return

        // Check if this is a call notification from a social app
        if (isCallNotification(sbn.notification, extras)) {
            if (NotificationPrefs.isSuppressCallNotifs(ctx)) {
                if (NotificationPrefs.isSuppressSourceEnabled(ctx)) {
                    cancelNotification(sbn.key)
                }
                return // Don't store or re-post call notifications when suppressed
            }
        }

        // Cache the social app's own contentIntent so we can open the exact conversation on tap
        sbn.notification.contentIntent?.let { contentIntents[sbn.key] = it }

        val n = SocialNotification(
            id          = "${sbn.key}",
            platform    = NotificationStore.platform(pkg),
            packageName = pkg,
            sender      = title,
            text        = text,
            timestamp   = sbn.postTime,
            isReaction  = isReactionNotification(text)
        )

        // Skip notifications from disabled platforms
        if (!NotificationPrefs.isPlatformEnabled(ctx, n.platform)) return

        NotificationStore.add(n)

        // Suppress source app notification when setting is enabled
        // But if pass-through-media is on and this is a media message, don't cancel the source
        val isMedia = isMediaMessage(extras)
        val shouldSuppressSource = NotificationPrefs.isSuppressSourceEnabled(ctx) &&
            !(NotificationPrefs.isPassThroughMedia(ctx) && isMedia)
        if (shouldSuppressSource) {
            cancelNotification(sbn.key)
        }

        // Only re-post as NexLink notification if this platform is not muted
        if (!NotificationPrefs.isMuted(ctx, n.platform)) {
            postNexLinkNotification(n)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg !in NotificationStore.watchedPackages) return
        // Social app dismissed its own notification → user read the message in the app
        val extras = sbn.notification?.extras ?: return
        val title  = extras.getCharSequence("android.title")?.toString() ?: return
        NotificationStore.markRead(NotificationStore.platform(pkg), title)
        contentIntents.remove(sbn.key)
    }

    private fun postNexLinkNotification(n: SocialNotification) {
        val ctx = applicationContext
        // Use getActivity so Android grants the activity-start on notification tap directly,
        // avoiding background-activity-start restrictions on Android 10+.
        val pi = PendingIntent.getActivity(
            ctx, n.id.hashCode(),
            Intent(ctx, com.nexlink.app.MainActivity::class.java).apply {
                putExtra("navigate_to",    com.nexlink.app.R.id.nav_inbox)
                putExtra("social_platform", n.platform)
                putExtra("social_sender",   n.sender)
                putExtra("social_key",      n.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Builder(ctx, App.CH_SOCIAL)
            .setSmallIcon(SmsNotifier.notifIconRes(ctx))
            .setLargeIcon(buildCompositeIcon(n.platform))
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

    /**
     * Builds a large-icon bitmap: NexLink circle (blue background, black N) with a
     * small platform-coloured badge circle in the bottom-right corner.
     */
    private fun buildCompositeIcon(platform: String): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // NexLink background circle
        paint.color = 0xFF397CAF.toInt()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // "N" in centre
        paint.color = 0xFF010101.toInt()
        paint.textSize = size * 0.46f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        val textY = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText("N", size / 2f, textY, paint)

        // Platform badge — bottom-right
        val badgeR = size * 0.20f
        val cx = size - badgeR - 2f
        val cy = size - badgeR - 2f

        // White ring for visual separation
        paint.isFakeBoldText = false
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, badgeR + 3f, paint)

        // Platform colour fill
        paint.color = platformColor(platform)
        canvas.drawCircle(cx, cy, badgeR, paint)

        // Platform initial
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = badgeR * 0.95f
        val badgeTextY = cy - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(platform.take(1), cx, badgeTextY, paint)

        return bmp
    }

    private fun platformColor(platform: String) = when (platform) {
        "Signal"    -> 0xFF3a9bd5.toInt()
        "Telegram"  -> 0xFF229ed9.toInt()
        "WhatsApp"  -> 0xFF25d366.toInt()
        "Messenger" -> 0xFF0099ff.toInt()
        "Discord"   -> 0xFF5865F2.toInt()
        "Instagram" -> 0xFFE1306C.toInt()
        "Steam"     -> 0xFF1A9FFF.toInt()
        else        -> 0xFF6c5ce7.toInt()
    }
}
