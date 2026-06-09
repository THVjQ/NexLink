package com.nexlink.app.db

import android.content.Context

object BlockStore {
    private const val PREFS = "nx_blocks"
    private const val KEY   = "blocked"

    fun block(ctx: Context, address: String)   = mutate(ctx) { it.add(normalize(address)) }
    fun unblock(ctx: Context, address: String) = mutate(ctx) { it.remove(normalize(address)) }
    fun isBlocked(ctx: Context, address: String) = load(ctx).contains(normalize(address))
    fun blockedSet(ctx: Context): Set<String> = load(ctx)

    private fun normalize(a: String) = a.replace("\\s".toRegex(), "")
    private fun load(ctx: Context): MutableSet<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())!!.toMutableSet()
    private fun mutate(ctx: Context, fn: (MutableSet<String>) -> Unit) {
        val set = load(ctx); fn(set)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY, set).apply()
    }
}
