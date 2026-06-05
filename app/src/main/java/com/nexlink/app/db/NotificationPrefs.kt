package com.nexlink.app.db

import android.content.Context

object NotificationPrefs {
    private const val PREFS = "nexlink_notif_prefs"
    private const val KEY_SUPPRESS_SOURCE = "suppress_source_notifications"

    fun isMuted(ctx: Context, platform: String): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("mute_$platform", false)

    fun setMuted(ctx: Context, platform: String, muted: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("mute_$platform", muted).apply()
    }

    fun isSuppressSourceEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SUPPRESS_SOURCE, false)

    fun setSuppressSource(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SUPPRESS_SOURCE, enabled).apply()
    }
}
