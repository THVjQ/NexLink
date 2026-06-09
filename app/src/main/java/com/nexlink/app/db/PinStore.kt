package com.nexlink.app.db

import android.content.Context

object PinStore {
    private const val PREFS = "nx_pins"
    private const val KEY   = "pinned_threads"

    fun pin(ctx: Context, threadId: Long)   = mutate(ctx) { it.add(threadId.toString()) }
    fun unpin(ctx: Context, threadId: Long) = mutate(ctx) { it.remove(threadId.toString()) }
    fun isPinned(ctx: Context, threadId: Long) = load(ctx).contains(threadId.toString())
    fun pinnedSet(ctx: Context): Set<String> = load(ctx)

    private fun load(ctx: Context): MutableSet<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())!!.toMutableSet()

    private fun mutate(ctx: Context, fn: (MutableSet<String>) -> Unit) {
        val set = load(ctx); fn(set)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY, set).apply()
    }
}
