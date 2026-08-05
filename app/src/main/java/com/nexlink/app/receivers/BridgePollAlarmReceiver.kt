package com.nexlink.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.nexlink.app.db.BridgePrefs
import com.nexlink.app.services.BridgePollAlarm
import com.nexlink.app.services.BridgePollingService

/**
 * Fires through Doze from [BridgePollAlarm]; holds a short wakelock, runs exactly one poll to
 * completion, then re-arms the next tick.
 *
 * The wakelock is what makes this work at all — the alarm only wakes the CPU long enough to deliver
 * the broadcast, and the network round-trip outlives `onReceive` without it.
 */
class BridgePollAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!BridgePrefs.isEnabled(ctx) || !BridgePrefs.isLinked(ctx)) return

        val pending = goAsync()
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NexLink:BridgePoll")
        wl.acquire(30_000)   // hard ceiling above pollOnce's 25s latch, so a stall can't strand it
        Thread {
            try { BridgePollingService.pollOnce(ctx.applicationContext) }
            catch (_: Exception) {}
            finally {
                BridgePollAlarm.arm(ctx)          // schedule the next fallback tick
                if (wl.isHeld) runCatching { wl.release() }
                pending.finish()
            }
        }.start()
    }
}
