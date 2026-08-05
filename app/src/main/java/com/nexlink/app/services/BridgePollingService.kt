package com.nexlink.app.services

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nexlink.app.App
import com.nexlink.app.R
import com.nexlink.app.db.BridgeApiClient
import com.nexlink.app.db.BridgePrefs
import com.nexlink.app.db.DebugLog
import com.nexlink.app.db.SmsHelper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Outbound poll loop for the Computer Bridge: pulls pending messages from the user's server,
 * decrypts them, and sends them as real SMS through NexLink's own [SmsHelper.sendSms] path.
 *
 * Reliability fixes over the reference fork (§7) — the fork only delivered while the app was
 * open because the FGS was OEM-killed in the background and nothing revived it:
 *   • FGS type is `connectedDevice` (§7b) — NOT `dataSync`, which Android 14+ caps at ~6h/day.
 *   • Battery-optimization exemption is requested in the wizard (§7a) so OEMs stop sleeping it.
 *   • A WorkManager watchdog (§7c) and a boot receiver (§7d) restart it if it dies.
 *   • `onTimeout` defensively stops (§7b) — the watchdog brings it straight back.
 *   • [BridgePollAlarm] ticks through Doze — the FGS keeps the process alive but not the CPU, so
 *     the executor below stops firing entirely once the screen goes off.
 *
 * The foreground-service contract: after `startForegroundService()` the platform demands a matching
 * `startForeground()` within ~5s on **every** path out of this service, or it kills the whole
 * process with `ForegroundServiceDidNotStartInTimeException`. So [promoteToForeground] runs first
 * and unconditionally, and every stop goes through [stopCleanly] — never a bare `stopSelf()`.
 *
 * Idempotency (§16.2): an id is recorded in [BridgePrefs] once the SMS stack has accepted it, so a
 * re-poll after a dropped `mark-sent` never double-texts a customer.
 */
class BridgePollingService : Service() {

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var task: ScheduledFuture<*>? = null

    /** True once [startForeground] has succeeded — the contract above is then satisfied. */
    @Volatile private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A START_STICKY redelivery re-enters here without onCreate, so promote here too.
        promoteToForeground()

        // Without POST_NOTIFICATIONS on 13+ the ongoing notification is invisible, so the user has
        // no way to see or stop a service that is reading their messages. Stand down instead —
        // start() refuses for the same reason, this is the backstop for callers that slip past it.
        if (!canPostNotifications(this)) {
            android.util.Log.w(TAG, "Notifications are off — bridge service standing down")
            stopCleanly(); return START_NOT_STICKY
        }
        if (!BridgePrefs.isEnabled(this) || !BridgePrefs.isLinked(this)) { stopCleanly(); return START_NOT_STICKY }
        // Re-scheduling an already-running service is a safe no-op: we cancel the prior task first.
        task?.cancel(false)
        val interval = BridgePrefs.getPollIntervalSeconds(this).toLong()
        task = executor.scheduleAtFixedRate(::poll, 0, interval, TimeUnit.SECONDS)
        // The executor is frozen in Doze, so it is not a wake source. Arm the AllowWhileIdle
        // fallback alongside it — it is what actually fires with the screen off.
        BridgePollAlarm.arm(this)
        return START_STICKY
    }

    private fun poll() = pollOnce(applicationContext)

    /**
     * Satisfies the foreground contract before anything else can fail. Tries the declared
     * `connectedDevice` type first (no 6-hour daily cap), then `dataSync`, then untyped — an OEM
     * that refuses the primary type throws a SecurityException out of [startForeground], and
     * letting that escape would crash the app rather than the service.
     */
    private fun promoteToForeground() {
        if (isForeground) return
        val n = buildNotification()
        isForeground =
            tryStartForeground(n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) ||
            tryStartForeground(n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) ||
            tryStartForeground(n, TYPE_NONE)
        if (!isForeground) android.util.Log.e(TAG, "startForeground failed on every type")
    }

    private fun tryStartForeground(n: Notification, type: Int): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type != TYPE_NONE) {
            startForeground(NOTIF_ID, n, type)
        } else {
            startForeground(NOTIF_ID, n)
        }
        true
    } catch (e: Exception) {
        android.util.Log.w(TAG, "startForeground(type=$type) refused: ${e.message}")
        false
    }

    /**
     * The only way this service is allowed to stop. Promoting first looks pointless when we are
     * about to quit, but it is exactly the case the platform crashes on: a start that reaches a
     * stop without ever having called [startForeground].
     */
    private fun stopCleanly() {
        promoteToForeground()
        if (isForeground) stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, App.CH_BRIDGE)
            .setContentTitle("Computer bridge active")
            .setContentText("Relaying your texts to your server")
            .setSmallIcon(R.drawable.ic_notif_nexlink)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    // Cheap insurance if the OS signals a timeout on a time-capped FGS type — the watchdog restarts us.
    override fun onTimeout(startId: Int) { stopCleanly() }

    override fun onDestroy() {
        task?.cancel(true); executor.shutdown(); BridgePollAlarm.cancel(this); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 4201
        private const val TAG = "NexLink_Bridge"
        private const val TYPE_NONE = 0

        /** How stale the desktop key set may get. A new PC starts receiving replies within this. */
        private const val KEY_REFRESH_MS = 5 * 60 * 1000L
        @Volatile private var lastKeyRefresh = 0L

        /**
         * Three things drive a cycle now — the service executor, the Doze alarm, and the watchdog
         * worker — and they overlap freely. Two cycles running at once could both see the same id
         * as unhandled and text the customer twice, so a second caller skips its turn rather than
         * queueing behind one that is already mid-flight.
         */
        private val pollLock = java.util.concurrent.locks.ReentrantLock()

        /** True while [pollOnce] is granted the notification permission it needs to run the FGS. */
        fun canPostNotifications(ctx: Context): Boolean =
            Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        /**
         * One full poll cycle, run to completion on the calling thread. Blocking so a wakelock can
         * bracket it — [com.nexlink.app.receivers.BridgePollAlarmReceiver] calls this from a Doze
         * alarm and must not release the CPU before the round-trip finishes.
         */
        fun pollOnce(ctx: Context) {
            if (BridgePrefs.getServerUrl(ctx).isBlank() || BridgePrefs.getApiKey(ctx).isBlank()) return

            // Without SEND_SMS every dispatch below would throw. Bail before touching the ledger so
            // the queue stays on the server and is retried once the permission is back, rather than
            // being marked handled and lost.
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                DebugLog.log(ctx, DebugLog.CAT_ERROR, "bridge", "Poll skipped · SEND_SMS not granted")
                return
            }

            if (!pollLock.tryLock()) return          // another cycle is already in flight
            try { pollCycle(ctx) } finally { pollLock.unlock() }
        }

        private fun pollCycle(ctx: Context) {

            // Pick up desktops added since pairing, so replies reach every PC on the account and not
            // just whichever ones existed when this phone linked.
            val now = System.currentTimeMillis()
            if (now - lastKeyRefresh >= KEY_REFRESH_MS) {
                lastKeyRefresh = now
                BridgeApiClient.refreshClientKeys(ctx)
            }

            // Replies held back because no PC had registered a key, or because the network was down.
            // Retried on every poll so a customer's answer is delayed rather than lost.
            BridgeApiClient.flushIncomingQueue(ctx)

            val latch = java.util.concurrent.CountDownLatch(1)
            BridgeApiClient.getPending(ctx) { messages ->
                try {
                    for (msg in messages) {
                        // §16.2 — skip anything already handed to the SMS stack (survives a dropped mark-sent).
                        if (BridgePrefs.wasHandled(ctx, msg.id)) { BridgeApiClient.markSent(ctx, msg.id); continue }
                        try {
                            val rowId = SmsHelper.sendSms(ctx, msg.phone, msg.message) // reuse NexLink's outbound path (§6)
                            // Recorded only AFTER a non-throwing dispatch. Marking it first meant a
                            // send that threw (SEND_SMS revoked, radio off) was already in the
                            // ledger, so the next poll skipped it and acked it as sent — the
                            // customer's message vanished. The reverse order costs only the far
                            // narrower window of a process kill between these two lines.
                            BridgePrefs.markHandled(ctx, msg.id)
                            BridgePrefs.markBridgeSent(ctx, rowId)         // tag so the chat badges it as PC-sent
                            BridgeApiClient.markSent(ctx, msg.id)
                            DebugLog.log(ctx, DebugLog.CAT_SENT, msg.phone, "Bridge → SMS · id=${msg.id} · row=$rowId")
                        } catch (e: Exception) {
                            BridgeApiClient.markFailed(ctx, msg.id)
                            DebugLog.log(ctx, DebugLog.CAT_ERROR, msg.phone, "Bridge send failed · id=${msg.id} · ${e.message}")
                        }
                    }
                } finally { latch.countDown() }
            }
            latch.await(25, TimeUnit.SECONDS)   // hold the thread (and wakelock) until the cycle is done
        }

        /**
         * The single choke point for the wizard, the watchdog, the boot receiver and the settings
         * screen. Refusing here — rather than in each caller — is what keeps a start attempt with
         * notifications denied from turning into a foreground-service crash loop.
         */
        fun start(ctx: Context) {
            if (!BridgePrefs.isEnabled(ctx) || !BridgePrefs.isLinked(ctx)) return
            if (!canPostNotifications(ctx)) {
                DebugLog.log(ctx, DebugLog.CAT_SYSTEM, "bridge",
                    "Service not started · notification permission missing")
                return
            }
            val i = Intent(ctx, BridgePollingService::class.java)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            BridgePollAlarm.cancel(ctx)
            ctx.stopService(Intent(ctx, BridgePollingService::class.java))
        }
    }
}
