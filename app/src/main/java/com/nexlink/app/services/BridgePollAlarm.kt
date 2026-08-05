package com.nexlink.app.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nexlink.app.receivers.BridgePollAlarmReceiver

/**
 * AllowWhileIdle fallback tick so the outbound poll still fires with the screen off.
 *
 * [BridgePollingService]'s ScheduledExecutorService is not an OS wake source: in Doze the CPU is
 * suspended and its timer thread is frozen, so pending messages sat on the server until the phone
 * was woken by hand. `setAndAllowWhileIdle` fires through Doze and needs no special permission
 * (unlike exact alarms), so it costs nothing at install time.
 *
 * One-shot and re-armed by the receiver after each cycle — a repeating alarm would be coalesced
 * back out of Doze.
 */
object BridgePollAlarm {
    private const val REQ = 4202

    /** Doze throttles AllowWhileIdle alarms to its own maintenance windows regardless of this. */
    private const val INTERVAL_MS = 60_000L

    fun arm(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = System.currentTimeMillis() + INTERVAL_MS
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi(ctx)) }
    }

    fun cancel(ctx: Context) {
        runCatching { (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(ctx)) }
    }

    private fun pi(ctx: Context) = PendingIntent.getBroadcast(
        ctx, REQ,
        Intent(ctx, BridgePollAlarmReceiver::class.java).setPackage(ctx.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
