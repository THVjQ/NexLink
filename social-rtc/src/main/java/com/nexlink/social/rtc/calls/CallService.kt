package com.nexlink.social.rtc.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * §15 — the foreground service that runs for the duration of a call.
 *
 * §15.3 is deliberate about how few of these there should be: messaging needs
 * **no** foreground service at all (§13.6.2), so this is one of only three in
 * the whole product, and it runs only while a call does.
 *
 * ## The pattern is copied, not invented
 *
 * §15.2.1 says the pattern from NexLink's `BridgePollingService` is "to be
 * reused verbatim", and this is that. It looks over-careful in three places and
 * each one is a crash the bridge already paid for:
 *
 * 1. **Promote in `onCreate` before any decision.** `startForegroundService()`
 *    obliges the process to call `startForeground()` within seconds *on every
 *    path*, including the paths that decide not to run. Deciding first and
 *    promoting second is the common shape and it is fatal.
 * 2. **Promote again in `onStartCommand`.** A sticky redelivery re-enters there
 *    with a fresh process and no promotion.
 * 3. **`stopCleanly` promotes before stopping.** That reads as pointless. It is
 *    the crash case: if something threw between `onCreate` and here, the
 *    process still owes the platform a `startForeground` and exits with the
 *    debt outstanding.
 *
 * ## The type fallback chain
 *
 * §15.2.1: *"an OEM refusing a typed start throws out of `startForeground`, and
 * letting that escape crashes the app"*. So microphone+camera degrades to
 * microphone, then to untyped. A call with no camera is worth more than a dead
 * process.
 */
class CallService : Service() {

    @Volatile private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        promoteToForeground()          // before any decision that could stop us
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()          // a sticky redelivery re-enters here
        if (intent?.getStringExtra(EXTRA_ROOM_ID).isNullOrBlank()) {
            // Nothing to run for. Still owed a promotion — see stopCleanly.
            stopCleanly()
            return START_NOT_STICKY
        }
        // §15.4.1 — the service exists for the duration of the call and no
        // longer. Whatever owns the media calls stop() when the call ends.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isForeground = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * §15.2.1's fallback chain — with a hole in it that had to be measured.
     *
     * The chain is written as microphone+camera → microphone → untyped, and the
     * last rung reads like an unconditional escape. **It is not.**
     * `startForeground(id, n)` on a service whose *manifest* declares a
     * `foregroundServiceType` still enforces that type's requirements, so an
     * "untyped" call is untyped only in the source.
     *
     * More to the point, no rung of a fallback chain can substitute for a
     * missing **runtime** permission. On Android 14+ a `microphone` start
     * throws without `RECORD_AUDIO` *granted*, not merely declared. Measured on
     * an SM-G990E (Android 16): with the permission absent, all three rungs
     * failed, `promoteToForeground` returned false silently, and the service
     * sat there owing the platform a promotion — the exact §15.2.1 death, with
     * the fallback chain in place and doing nothing.
     *
     * So the type is chosen from what is actually granted, and the chain
     * handles OEM refusal — which is what §15.2.1 wrote it for — rather than
     * pretending to handle a permission problem it cannot.
     */
    private fun promoteToForeground() {
        if (isForeground) return
        val n = buildNotification()
        isForeground =
            tryStartForeground(n, grantedTypes()) ||
            tryStartForeground(n, micTypeIfGranted()) ||
            tryStartForeground(n, TYPE_NONE)
    }

    private fun granted(permission: String): Boolean =
        checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Both, one, or neither — whatever the user has actually allowed. */
    private fun grantedTypes(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return TYPE_NONE
        var t = 0
        if (granted(android.Manifest.permission.RECORD_AUDIO)) {
            t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (granted(android.Manifest.permission.CAMERA)) {
            t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        return t
    }

    private fun micTypeIfGranted(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            granted(android.Manifest.permission.RECORD_AUDIO)
        ) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else TYPE_NONE

    /**
     * @return true when the platform accepted the promotion.
     *
     * Catches [Throwable] rather than a named exception on purpose: the failure
     * modes here are OEM-specific and have historically included
     * `SecurityException`, `IllegalArgumentException`,
     * `ForegroundServiceStartNotAllowedException` and at least one vendor
     * `RuntimeException`. Naming them is a guess; the point is that a refusal
     * must fall through to the next type rather than escape.
     */
    private fun tryStartForeground(n: Notification, type: Int): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type != TYPE_NONE) {
            startForeground(NOTIFICATION_ID, n, type)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
        true
    } catch (_: Throwable) {
        false
    }

    /**
     * §15.2.2 — **every `stopSelf()` in this file is inside this method.**
     *
     * That is one of the three invariants that section makes checkable, and
     * `tools/check-invariants.sh` enforces it.
     */
    private fun stopCleanly() {
        promoteToForeground()          // looks pointless; it is the crash case
        if (isForeground) stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Calls", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("Call in progress")
            // §27.5 — no identifier here. This notification is visible on the
            // lock screen of a phone that §12.4.4 has already established may
            // have no lock at all.
            .setContentText("NexLink Social")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "social_call"
        private const val NOTIFICATION_ID = 7301
        const val EXTRA_ROOM_ID = "com.nexlink.social.rtc.CALL_ROOM_ID"

        private const val TYPE_NONE = 0

        /**
         * §15.4.1 — start on **answer**, never on ring.
         *
         * §17.7's lifecycle table puts the service start in the "Answer" row
         * explicitly. Starting it while ringing would hold the microphone for a
         * call the user has not taken, which is both a battery cost and exactly
         * the behaviour that makes a permission reviewer look harder (§4.2.1).
         */
        fun start(context: Context, roomId: String) {
            val i = Intent(context, CallService::class.java)
                .putExtra(EXTRA_ROOM_ID, roomId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallService::class.java))
        }
    }
}
