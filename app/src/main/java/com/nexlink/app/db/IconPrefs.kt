package com.nexlink.app.db

import android.content.Context

object IconPrefs {
    private const val PREFS          = "nx_icon_prefs"
    private const val KEY_APP_ICON   = "app_icon_index"
    private const val KEY_NOTIF_ICON = "notif_icon_index"

    /** 0 = default, 1-10 = custom icons from zip (set1_1..set1_5, set2_1..set2_5) */
    fun getAppIconIndex(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_APP_ICON, 0)

    fun setAppIconIndex(ctx: Context, index: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_APP_ICON, index).apply()
    }

    fun getNotifIconIndex(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_NOTIF_ICON, 0)

    fun setNotifIconIndex(ctx: Context, index: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_NOTIF_ICON, index).apply()
    }

    /** Returns the alias component name for the given icon index (0 = default). */
    fun aliasForIndex(index: Int) = when (index) {
        0    -> ".MainActivityDefault"
        else -> ".MainActivityIcon$index"
    }
}
