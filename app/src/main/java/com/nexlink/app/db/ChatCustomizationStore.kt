package com.nexlink.app.db

import android.content.Context

object ChatCustomizationStore {
    private const val PREFS = "nx_chat_custom"

    fun getWallpaper(ctx: Context, address: String): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("wp_${norm(address)}", null)

    fun setWallpaper(ctx: Context, address: String, uriString: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("wp_${norm(address)}", uriString).apply()

    fun clearWallpaper(ctx: Context, address: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("wp_${norm(address)}").apply()

    fun getIcon(ctx: Context, address: String): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("icon_${norm(address)}", null)

    fun setIcon(ctx: Context, address: String, emoji: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("icon_${norm(address)}", emoji).apply()

    private fun norm(a: String) = a.replace("\\s".toRegex(), "").replace("+", "p")
}
