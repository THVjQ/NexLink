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
 *   • `startForeground` is only called with POST_NOTIFICATIONS granted (§7b).
 *
 * Idempotency (§16.2): an id is recorded in [BridgePrefs] the moment it is handed to the SMS
 * stack, so a re-poll after a dropped `mark-sent` never double-texts a customer.
 */
class BridgePollingService : Service() {

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var task: ScheduledFuture<*>? = null

    /**
     * A PC that registers its key after this phone paired would otherwise never be discovered — the
     * key set is only fetched when it is empty, so a second desktop silently receives no replies.
     * Re-reading it periodically bounds how long a newly added PC stays invisible.
     */
    @Volatile private var lastKeyRefresh = 0L

    override fun onCreate() {
        super.onCreate()
        // Do not start the FGS without POST_NOTIFICATIONS on 13+ — startForeground would throw an
        // uncatchable CannotPostForegroundServiceNotificationException.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (e: Exception) {
            android.util.Log.e("NexLink_Bridge", "startForeground failed: ${e.message}")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BridgePrefs.isEnabled(this) || !BridgePrefs.isLinked(this)) { stopSelf(); return START_NOT_STICKY }
        // Re-scheduling an already-running service is a safe no-op: we cancel the prior task first.
        task?.cancel(false)
        val interval = BridgePrefs.getPollIntervalSeconds(this).toLong()
        task = executor.scheduleAtFixedRate(::poll, 0, interval, TimeUnit.SECONDS)
        return START_STICKY
    }

    private fun poll() {
        val ctx = applicationContext
        if (BridgePrefs.getServerUrl(ctx).isBlank() || BridgePrefs.getApiKey(ctx).isBlank()) return

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

        BridgeApiClient.getPending(ctx) { messages ->
            for (msg in messages) {
                // §16.2 — skip anything already handed to the SMS stack (survives a dropped mark-sent).
                if (BridgePrefs.wasHandled(ctx, msg.id)) { BridgeApiClient.markSent(ctx, msg.id); continue }
                try {
                    BridgePrefs.markHandled(ctx, msg.id)          // record BEFORE sending
                    val rowId = SmsHelper.sendSms(ctx, msg.phone, msg.message) // reuse NexLink's outbound path (§6)
                    BridgePrefs.markBridgeSent(ctx, rowId)         // tag so the chat badges it as PC-sent
                    BridgeApiClient.markSent(ctx, msg.id)
                    DebugLog.log(ctx, DebugLog.CAT_SENT, msg.phone, "Bridge → SMS · id=${msg.id} · row=$rowId")
                } catch (e: Exception) {
                    BridgeApiClient.markFailed(ctx, msg.id)
                    DebugLog.log(ctx, DebugLog.CAT_ERROR, msg.phone, "Bridge send failed · id=${msg.id} · ${e.message}")
                }
            }
        }
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
    override fun onTimeout(startId: Int) { stopSelf() }

    override fun onDestroy() { task?.cancel(true); executor.shutdown(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 4201

        /** How stale the desktop key set may get. A new PC starts receiving replies within this. */
        private const val KEY_REFRESH_MS = 5 * 60 * 1000L

        fun start(ctx: Context) {
            if (!BridgePrefs.isEnabled(ctx) || !BridgePrefs.isLinked(ctx)) return
            val i = Intent(ctx, BridgePollingService::class.java)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, BridgePollingService::class.java))
        }
    }
}
