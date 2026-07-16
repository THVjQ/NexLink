package com.nexlink.app.services

import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.nexlink.app.App
import com.nexlink.app.R
import com.nexlink.app.db.NotificationPrefs
import com.nexlink.app.db.NotificationStore
import com.nexlink.app.db.SocialNotification
import com.nexlink.app.receivers.SmsNotifier
import com.nexlink.app.SocialOpenActivity

class NexLinkNotificationListener : NotificationListenerService() {

    companion object {
        // Original contentIntents from social apps — keyed by StatusBarNotification.key.
        // Stored so tapping the NexLink re-post OR an inbox conversation row opens the exact
        // chat instead of cold-launching the app to its last screen.
        //
        // In-memory only (does not survive a reboot) and read from the UI thread while written
        // from the listener thread, so every access goes through the synchronized helpers below.
        // Bounded LinkedHashMap: self-suppressed notifications keep their intent for the process
        // lifetime (see selfSuppressed), so cap the size to avoid slow unbounded growth.
        private const val MAX_CACHED_INTENTS = 200
        private val cacheLock = Any()
        private val contentIntents =
            object : LinkedHashMap<String, android.app.PendingIntent>(16, 0.75f, false) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, android.app.PendingIntent>
                ): Boolean = size > MAX_CACHED_INTENTS
            }

        // Notification keys we canceled ourselves for source-suppression. The resulting
        // onNotificationRemoved must NOT discard the cached intent or mark the message read —
        // only the source app dismissing its own notification means "read in the app".
        private val selfSuppressed = mutableSetOf<String>()

        fun cacheContentIntent(key: String, pi: android.app.PendingIntent) =
            synchronized(cacheLock) { contentIntents[key] = pi }

        /** Return and remove — for one-shot callers. */
        fun popContentIntent(key: String): android.app.PendingIntent? =
            synchronized(cacheLock) { contentIntents.remove(key) }

        /** Return without removing so both the notification tap and the inbox row can use it. */
        fun peekContentIntent(key: String): android.app.PendingIntent? =
            synchronized(cacheLock) { contentIntents[key] }

        /** Drop an intent the system reported as canceled/stale. */
        fun dropContentIntent(key: String) =
            synchronized(cacheLock) { contentIntents.remove(key) }

        /**
         * Fire the social app's own contentIntent so the tap lands in the exact conversation.
         * Returns false when there is nothing cached or the source app canceled the intent, so
         * callers can fall back to cold-launching the app.
         */
        fun fireContentIntent(ctx: Context, key: String): Boolean {
            val pi = peekContentIntent(key) ?: return false
            return try {
                pi.send(ctx, 0, null, null, null, null, backgroundStartOptions())
                true
            } catch (_: PendingIntent.CanceledException) {
                dropContentIntent(key)
                false
            }
        }

        // Since Android 14 the sender no longer hands its background-activity-launch privilege
        // to the PendingIntent's creator. Without opting in, send() returns normally while the
        // system silently drops the launch — a tap that appears to do nothing at all.
        private fun backgroundStartOptions(): Bundle? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else null

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

    private fun isVoiceMessageNotification(extras: android.os.Bundle): Boolean {
        val text = extras.getCharSequence("android.text")?.toString()?.lowercase() ?: return false
        return text.contains("voice message") || text.contains("voice note") ||
               text.contains("audio message") || text.contains("🎤")
    }

    private fun isMediaMessage(extras: android.os.Bundle): Boolean {
        val text = extras.getCharSequence("android.text")?.toString()?.lowercase() ?: return false
        return text.contains("voice message") || text.contains("audio message") ||
               text.contains("voice note") || text.contains("🎵") || text.contains("🎤")
    }

    // Messenger and Signal send background polling/status notifications — skip them.
    private fun isPollingNotification(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("checking for messages") ||
               lower.contains("notifications disabled") ||
               lower.contains("message notifications disabled") ||
               lower.contains("your messages will appear here") ||
               lower.contains("tap to check messages") ||
               lower.contains("signal is connected") ||
               lower.contains("pinned notification")
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

        // Skip persistent foreground-service / ongoing notifications (Signal's call service, etc.)
        if (sbn.isOngoing) return

        // Skip group-summary notifications (WhatsApp/Instagram send one per chat + one summary)
        if (sbn.notification?.flags != null &&
            sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = sbn.notification?.extras ?: return
        val rawTitle = extras.getCharSequence("android.title")?.toString() ?: return
        val text     = extras.getCharSequence("android.text")?.toString()  ?: return
        if (rawTitle.isBlank() || text.isBlank()) return
        // For MessagingStyle group chats (Signal, WhatsApp), android.conversationTitle is
        // the group/chat name while android.title is the individual sender.  Using the
        // conversation title groups all members' messages under one chat in the inbox.
        val convTitle = extras.getCharSequence("android.conversationTitle")?.toString()
        val title = convTitle?.takeIf { it.isNotBlank() } ?: rawTitle

        // Skip polling/status notifications from Messenger, Signal, etc.
        if (isPollingNotification(text) || isPollingNotification(title)) return

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
        sbn.notification.contentIntent?.let { cacheContentIntent(sbn.key, it) }

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

        NotificationStore.add(n, applicationContext)

        // Suppress source app notification when setting is enabled.
        // Voice messages are NEVER suppressed — the user must open the social app to play audio.
        // Other media (images, video) respect the pass-through-media toggle.
        val isVoice = isVoiceMessageNotification(extras)
        val isMedia = isMediaMessage(extras) && !isVoice
        val shouldSuppressSource = NotificationPrefs.isSuppressSourceEnabled(ctx) &&
            !isVoice &&
            !(NotificationPrefs.isPassThroughMedia(ctx) && isMedia)
        if (shouldSuppressSource) {
            // Mark before canceling: our cancel triggers onNotificationRemoved, which must keep
            // the cached contentIntent alive (so the inbox row can still open the exact chat) and
            // must not mistake this for the user having read the message in the source app.
            selfSuppressed.add(sbn.key)
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
        // We canceled this ourselves to suppress the source app: it was NOT read in the app, and
        // its cached contentIntent must survive so a later tap still opens the exact conversation.
        if (selfSuppressed.remove(sbn.key)) return
        // Social app dismissed its own notification → user read the message in the app
        val extras = sbn.notification?.extras ?: return
        val title  = extras.getCharSequence("android.title")?.toString() ?: return
        NotificationStore.markRead(NotificationStore.platform(pkg), title)
        dropContentIntent(sbn.key)
    }

    private fun postNexLinkNotification(n: SocialNotification) {
        val ctx    = applicationContext
        val notifId = "nexlink_social_${n.id}".hashCode()
        // Route tap through SocialOpenActivity (Activity, NOT receiver).
        // Android 12+ blocks activity starts from receivers triggered by a notification tap;
        // an Activity is allowed to start other activities, so the social chat opens correctly.
        val tapIntent = Intent(ctx, SocialOpenActivity::class.java).apply {
            putExtra("platform",         n.platform)
            putExtra("sender",           n.sender)
            putExtra("notification_key", n.id)
            putExtra("notif_id",         notifId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, notifId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // MessagingStyle with the platform as conversation title lets Samsung Buds and
        // other wearables announce "Signal from John" / "WhatsApp from John" etc.
        val person = Person.Builder().setName(n.sender).build()
        val msgStyle = NotificationCompat.MessagingStyle(person)
            .setConversationTitle(n.platform)
            .addMessage(n.text, n.timestamp, person)

        NotificationCompat.Builder(ctx, App.CH_SOCIAL)
            .setSmallIcon(SmsNotifier.notifIconRes(ctx))
            .setLargeIcon(buildCompositeIcon(n.platform))
            .setContentTitle("${n.platform} · ${n.sender}")
            .setContentText(n.text)
            .setStyle(msgStyle)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
            .also {
                ctx.getSystemService(NotificationManager::class.java)
                    .notify(notifId, it)
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
