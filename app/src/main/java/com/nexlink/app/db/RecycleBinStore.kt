package com.nexlink.app.db

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DeletedConversation(
    val threadId: Long,
    val address: String,
    val contactName: String,
    val lastMessage: String,
    val timestamp: Long,
    val deletedAt: Long
)

object RecycleBinStore {
    private const val PREFS = "nx_recycle_bin"
    private const val KEY   = "deleted_convs"

    fun add(ctx: Context, conv: Conversation) {
        val list = getAll(ctx).toMutableList()
        list.removeAll { it.threadId == conv.threadId }
        list.add(0, DeletedConversation(conv.threadId, conv.address, conv.contactName,
            conv.lastMessage, conv.timestamp, System.currentTimeMillis()))
        if (list.size > 100) list.subList(100, list.size).clear()
        save(ctx, list)
    }

    fun remove(ctx: Context, threadId: Long) {
        save(ctx, getAll(ctx).filter { it.threadId != threadId })
    }

    private val EXPIRY_MS = 30L * 24 * 60 * 60 * 1000  // 30 days

    fun getAll(ctx: Context): List<DeletedConversation> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        val all = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DeletedConversation(o.getLong("threadId"), o.getString("address"),
                    o.getString("contactName"), o.getString("lastMessage"),
                    o.getLong("timestamp"), o.getLong("deletedAt"))
            }
        } catch (_: Exception) { emptyList() }
        val cutoff = System.currentTimeMillis() - EXPIRY_MS
        val active = all.filter { it.deletedAt >= cutoff }
        if (active.size != all.size) save(ctx, active)  // prune expired items
        return active
    }

    fun clear(ctx: Context) = save(ctx, emptyList())

    private fun save(ctx: Context, list: List<DeletedConversation>) {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(JSONObject().apply {
                put("threadId", d.threadId); put("address", d.address)
                put("contactName", d.contactName); put("lastMessage", d.lastMessage)
                put("timestamp", d.timestamp); put("deletedAt", d.deletedAt)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
