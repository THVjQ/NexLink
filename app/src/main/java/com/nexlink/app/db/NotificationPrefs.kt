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
            .getBoolean(KEY_SUPPRESS_SOURCE, true)

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

    // ── RCS Chats ──
    fun isRcsEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("rcs_enabled", false)

    fun setRcsEnabled(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("rcs_enabled", v).apply()
    }

    // ── Encryption master toggle ──
    fun isEncryptionEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("encryption_enabled", true)

    fun setEncryptionEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("encryption_enabled", enabled).apply()
    }

    // ── Dark mode: 0=system, 1=light, 2=dark ──
    fun getDarkMode(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("dark_mode", 0)

    fun setDarkMode(ctx: Context, mode: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("dark_mode", mode).apply()
    }
}
