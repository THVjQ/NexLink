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

    // ── Per-platform enabled state ──
    fun isPlatformEnabled(ctx: Context, platform: String): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("platform_$platform",
                platform in setOf("Signal", "Telegram", "WhatsApp", "Messenger"))

    fun setPlatformEnabled(ctx: Context, platform: String, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("platform_$platform", enabled).apply()
    }

    // ── Suppress call notifications from social apps ──
    fun isSuppressCallNotifs(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("suppress_call_notifs", false)

    fun setSuppressCallNotifs(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("suppress_call_notifs", v).apply()
    }

    // ── Pass-through media (voice messages) ──
    fun isPassThroughMedia(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("pass_through_media", true)

    fun setPassThroughMedia(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("pass_through_media", v).apply()
    }
}
