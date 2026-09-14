package com.nexlink.social.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.nexlink.social.core.session.IncomingCall

/**
 * §15.6 — the incoming-call surface.
 *
 * ## Why this is a notification and not a service
 *
 * §15.6 gives the order and it is the whole design:
 *
 * ```
 * high-priority FCM → app wakes
 *                   → post full-screen-intent call notification
 *                   → user answers
 *                   → NOW start the foreground service with microphone|camera
 * ```
 *
 * *"The notification, not the service, is what reaches the user."* A
 * `microphone` foreground service started from a push would be holding the
 * microphone for a call nobody has answered — a battery cost, and §4.2.1's
 * "the behaviour that makes a permission reviewer look harder". Nothing here
 * touches the microphone or the camera.
 *
 * ## The ringing timeout is the caller's, not ours
 *
 * §15.6 asks for *"a ringing timeout, after which the notification becomes a
 * missed call"*. The caller already put one on the wire — `lifetime`, 90
 * seconds as Element Call sends it — so [IncomingCall.expiresAtMs] is used
 * rather than a second timeout invented here that could disagree with the
 * caller's idea of when it gave up. `setTimeoutAfter` hands the deadline to
 * the platform, which honours it even if this process is dead by then.
 */
object IncomingCallNotification {

    private const val CHANNEL = "social_incoming_call"
    private const val ID = 7302

    const val ACTION_DECLINE = "com.nexlink.social.action.DECLINE_CALL"
    const val EXTRA_ROOM_ID = "room_id"

    fun show(context: Context, call: IncomingCall) {
        ensureChannel(context)

        val answer = PendingIntent.getActivity(
            context, 1,
            CallActivity.intent(context, call.roomId.value, call.roomTitle)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val decline = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, DeclineReceiver::class.java)
                .setAction(ACTION_DECLINE)
                .putExtra(EXTRA_ROOM_ID, call.roomId.value),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // §27.5 — the caller's MXID is an identifier and this notification is
        // visible on a lock screen. Show the conversation's name, which the
        // user chose, and fall back to something that names nobody.
        val who = call.roomTitle.ifBlank { "Someone" }

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(true)
            // The route §15.6 names: on a locked or idle screen this becomes
            // the full-screen ringing UI; on an unlocked, in-use screen the
            // platform shows a heads-up instead, which is the right call and
            // is why this is not an activity launch.
            .setFullScreenIntent(answer, true)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    Person.Builder().setName(who).setImportant(true).build(),
                    decline, answer
                )
            )
            .setTimeoutAfter((call.expiresAtMs - System.currentTimeMillis()).coerceAtLeast(1))
            .build()

        NotificationManagerCompat.from(context).notify(ID, n)
    }

    fun dismiss(context: Context) =
        NotificationManagerCompat.from(context).cancel(ID)

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Incoming calls", NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = "Rings when someone calls you."
                    setShowBadge(false)
                    // A call ring that does not ring is a missed call. The user
                    // can still silence the channel; the default must not.
                    setSound(
                        android.media.RingtoneManager
                            .getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE),
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    enableVibration(true)
                }
        )
    }

    /**
     * Declining only takes the ring away.
     *
     * §17.7's lifecycle puts "decline" as a client-local act: MatrixRTC has no
     * "I rejected this" event, because the caller learns the same thing from
     * nobody joining. Sending one would be inventing protocol.
     */
    class DeclineReceiver : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = dismiss(context)
    }
}
