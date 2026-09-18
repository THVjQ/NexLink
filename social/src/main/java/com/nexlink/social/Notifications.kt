package com.nexlink.social

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nexlink.social.contract.SocialBridgeContract
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.nexlink.social.core.session.RoomId

/**
 * Message notifications — §13.4.
 *
 * §13.4.1 asks for per-conversation channels, `MessagingStyle`, grouping,
 * inline reply and inline mark-read. All of that is here.
 *
 * §13.4.2's prohibitions are the part worth reading:
 *  - **no notification for something already read elsewhere** — [dismiss] is
 *    called when a read receipt arrives from any device, because the most
 *    common complaint about multi-device messengers is drowning in
 *    notifications you have already handled;
 *  - **no duplicates** — the room id is the notification id, so a redelivery
 *    replaces rather than stacks;
 *  - **no content in logs** — nothing here logs a body, a room id or an MXID
 *    (§2.8's last invariant, §27.5).
 *
 * Push (§13.3) is not wired yet, so these fire only while the app is running.
 * The surface is built first deliberately: it is also what NexLink's unified
 * inbox reads at Level 0 (§16.2), and that claim is the whole of §1.4.4.
 */
object Notifications {

    private const val GROUP_KEY = "com.nexlink.social.MESSAGES"
    private const val CHANNEL_GROUP = "conversations"
    const val ACTION_REPLY = "com.nexlink.social.action.REPLY"
    const val ACTION_MARK_READ = "com.nexlink.social.action.MARK_READ"
    const val EXTRA_ROOM_ID = "room_id"
    const val KEY_REPLY_TEXT = "reply_text"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannelGroup(
            NotificationChannelGroup(CHANNEL_GROUP, "Conversations")
        )
    }

    /** §13.4.1 — one channel per conversation, so muting is granular. */
    private fun channelFor(context: Context, roomId: String, title: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return roomId
        val nm = context.getSystemService(NotificationManager::class.java)
        val id = "room_$roomId"
        if (nm.getNotificationChannel(id) == null) {
            nm.createNotificationChannel(
                NotificationChannel(id, title, NotificationManager.IMPORTANCE_HIGH).apply {
                    group = CHANNEL_GROUP
                    setShowBadge(true)
                }
            )
        }
        return id
    }

    fun show(
        context: Context,
        roomId: RoomId,
        roomTitle: String,
        senderName: String,
        body: String,
        timestamp: Long,
        showContent: Boolean
    ) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val channel = channelFor(context, roomId.value, roomTitle)
        val me = Person.Builder().setName("You").build()
        val them = Person.Builder().setName(senderName).build()

        // §13.4.1 — MessagingStyle, required for correct rendering and for the
        // system Conversations surface.
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(roomTitle)
            .setGroupConversation(true)
            .addMessage(
                // §18.5 — while the user is sharing their screen, previews
                // collapse to a count. This is the one app whose notifications
                // we control, so it is the one case we can actually fix.
                if (showContent) body else "New message",
                timestamp,
                them
            )

        val open = PendingIntent.getActivity(
            context, roomId.value.hashCode(),
            ConversationActivity.intent(context, roomId.value, roomTitle),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // §13.4.1 — reply without opening the app.
        val replyIntent = android.content.Intent(context, NotificationActionReceiver::class.java)
            .setAction(ACTION_REPLY)
            .putExtra(EXTRA_ROOM_ID, roomId.value)
        val replyPending = PendingIntent.getBroadcast(
            context, ("r" + roomId.value).hashCode(), replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Reply", replyPending
        ).addRemoteInput(
            RemoteInput.Builder(KEY_REPLY_TEXT).setLabel("Message").build()
        ).setAllowGeneratedReplies(false).build()

        // §13.4.1 — mark read, so dismissing means something.
        val readIntent = android.content.Intent(context, NotificationActionReceiver::class.java)
            .setAction(ACTION_MARK_READ)
            .putExtra(EXTRA_ROOM_ID, roomId.value)
        val readPending = PendingIntent.getBroadcast(
            context, ("m" + roomId.value).hashCode(), readIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            // §16.2 — set title and text explicitly ALONGSIDE MessagingStyle.
            //
            // MessagingStyle does not reliably populate the `android.title` and
            // `android.text` extras, and a NotificationListenerService reading
            // those — which is exactly what NexLink's unified inbox does — sees
            // nothing and drops the notification. Any other listener (Android
            // Auto, Wear, a smartwatch) has the same problem.
            //
            // MessagingStyle still wins for rendering on the phone; these are
            // the fallback a listener can actually read.
            .setContentTitle(roomTitle)
            .setContentText(if (showContent) "$senderName: $body" else "New message")
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setWhen(timestamp)
            .addAction(replyAction)
            .addAction(android.R.drawable.ic_menu_view, "Mark read", readPending)
            .build()

        // §13.4.2 — the room id IS the notification id, so a redelivered push
        // replaces rather than stacks.
        runCatching { nm.notify(roomId.value.hashCode(), n) }
    }

    /**
     * §13.4.2 — called when the conversation is read anywhere, including on
     * another device. This is the prohibition that matters most to a
     * multi-device user.
     */
    fun dismiss(context: Context, roomId: RoomId, roomTitle: String? = null) {
        runCatching { NotificationManagerCompat.from(context).cancel(roomId.value.hashCode()) }

        // §16.2.2 — and tell NexLink, because the cancel above is very often a
        // no-op. NexLink's unified inbox cancels Social's notification the
        // moment it appears and posts its own copy; that copy is the one the
        // user can see, and only NexLink can remove it.
        roomTitle?.takeIf { it.isNotBlank() }?.let { title ->
            runCatching {
                // **No receiver permission.** `sendBroadcast(intent, permission)`
                // requires the RECEIVER to hold it, and NexLink deliberately
                // does not: §2.8 #6 asserts its declared permission set never
                // changes. Passing it here meant the broadcast was simply never
                // delivered — silently, because an undelivered broadcast looks
                // exactly like a delivered one from the sender's side.
                //
                // `setPackage` still means only NexLink can receive it.
                context.sendBroadcast(
                    android.content.Intent(SocialBridgeContract.ACTION_CONVERSATION_READ)
                        .setPackage(SocialBridgeContract.NEXLINK_PACKAGE)
                        .putExtra(SocialBridgeContract.EXTRA_CONVERSATION_TITLE, title)
                )
            }
        }
    }
}
