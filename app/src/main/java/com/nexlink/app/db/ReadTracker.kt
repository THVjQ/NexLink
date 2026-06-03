package com.nexlink.app.db

import android.content.Context

object ReadTracker {
    private const val PREFS = "nexlink_read"

    fun markRead(ctx: Context, address: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(key(address), System.currentTimeMillis()).apply()
    }

    fun isLocallyRead(ctx: Context, address: String, lastMessageTimestamp: Long): Boolean {
        val readAt = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(key(address), 0L)
        return readAt >= lastMessageTimestamp
    }

    fun invalidate(ctx: Context, address: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(key(address)).apply()
    }

    private fun key(address: String) = "read_$address"
}
