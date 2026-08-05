package com.nexlink.app.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.nexlink.app.db.BridgePrefs
import java.util.concurrent.TimeUnit

/**
 * Periodic watchdog (§7c) that revives [BridgePollingService] if the OS or an OEM battery
 * manager kills it in the background, and runs one poll cycle itself as a fallback for when the
 * platform refuses that restart. Runs at WorkManager's 15-minute floor; between ticks the FGS
 * itself keeps polling, so this only matters when the service has been killed.
 *
 * Only scheduled while the bridge is enabled (see [schedule]); cancelled on disable (§8).
 */
class BridgeWatchdogWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        if (!BridgePrefs.isEnabled(ctx) || !BridgePrefs.isLinked(ctx)) return Result.success()
        if (BridgePrefs.getServerUrl(ctx).isBlank() || BridgePrefs.getApiKey(ctx).isBlank()) return Result.success()
        // Re-starting an already-running service is a safe no-op (onStartCommand reschedules).
        // On Android 12+ this can simply be refused: a WorkManager job is a background context and
        // the battery-optimisation exemption does not grant background FGS starts, so the restart
        // this watchdog exists for throws ForegroundServiceStartNotAllowedException.
        try { BridgePollingService.start(ctx) } catch (_: Exception) { /* fall through to pollOnce */ }

        // …which is why the tick also does the work itself. doWork already runs off the main
        // thread, and pollOnce is a no-op if the service's own loop is mid-cycle, so queued
        // messages keep moving every ~15 minutes even when the service can't come up at all.
        return try {
            BridgePollingService.pollOnce(ctx)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "bridge_watchdog"

        fun schedule(ctx: Context) {
            val work = PeriodicWorkRequestBuilder<BridgeWatchdogWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work
            )
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
        }
    }
}
