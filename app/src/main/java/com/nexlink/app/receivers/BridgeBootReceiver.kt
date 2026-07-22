package com.nexlink.app.receivers

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.nexlink.app.db.BridgePrefs
import com.nexlink.app.services.BridgePollingService
import com.nexlink.app.services.BridgeWatchdogWorker

/**
 * Restarts the bridge after a reboot (§7d) — only when the feature is enabled, linked, and the
 * POST_NOTIFICATIONS permission is granted (starting the FGS without it would crash on 13+).
 * Also re-arms the watchdog. Does nothing on a fresh install where the bridge is off.
 */
class BridgeBootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON") return

        if (!BridgePrefs.isEnabled(ctx) || !BridgePrefs.isLinked(ctx)) return

        val notifOk = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!notifOk) return

        BridgeWatchdogWorker.schedule(ctx)
        BridgePollingService.start(ctx)
    }
}
