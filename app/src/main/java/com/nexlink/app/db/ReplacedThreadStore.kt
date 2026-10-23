package com.nexlink.app.db

import android.content.Context

/**
 * Group threads that were superseded by a membership change.
 *
 * MMS has no "add/remove member": a changed set of people is a new thread. Without this the old
 * thread sat in the list next to its replacement and looked like a duplicate. It is hidden, never
 * deleted, and only until something newer arrives in it — if someone replies to the old group,
 * that reply would otherwise be invisible.
 */
object ReplacedThreadStore {
    private const val PREFS = "nx_replaced_threads"

    fun markReplaced(ctx: Context, threadId: Long) {
        if (threadId <= 0) return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(threadId.toString(), System.currentTimeMillis()).apply()
    }

    /** True while the thread has had nothing new since it was replaced. */
    fun isHidden(ctx: Context, threadId: Long, lastActivity: Long): Boolean {
        val at = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(threadId.toString(), 0L)
        return at > 0 && lastActivity <= at
    }
}
