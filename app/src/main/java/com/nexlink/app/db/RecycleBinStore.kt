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

data class DeletedMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val isMms: Boolean,
    val deletedAt: Long
)

object RecycleBinStore {
    private const val PREFS      = "nx_recycle_bin"
    private const val KEY_CONVS  = "deleted_convs"
    private const val KEY_MSGS   = "deleted_msgs"
    private val EXPIRY_MS        = 30L * 24 * 60 * 60 * 1000  // 30 days

    // ── Conversations ──────────────────────────────────────────────────────────

    fun add(ctx: Context, conv: Conversation) {
        val list = getAll(ctx).toMutableList()
        list.removeAll { it.threadId == conv.threadId }
        list.add(0, DeletedConversation(conv.threadId, conv.address, conv.contactName,
            conv.lastMessage, conv.timestamp, System.currentTimeMillis()))
        if (list.size > 100) list.subList(100, list.size).clear()
        saveConvs(ctx, list)
    }

    fun remove(ctx: Context, threadId: Long) {
        saveConvs(ctx, getAll(ctx).filter { it.threadId != threadId })
    }

    fun getAll(ctx: Context): List<DeletedConversation> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CONVS, null)
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
        if (active.size != all.size) saveConvs(ctx, active)
        return active
    }

    fun clear(ctx: Context) {
        saveConvs(ctx, emptyList())
        saveMsgs(ctx, emptyList())
    }

    private fun saveConvs(ctx: Context, list: List<DeletedConversation>) {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(JSONObject().apply {
                put("threadId", d.threadId); put("address", d.address)
                put("contactName", d.contactName); put("lastMessage", d.lastMessage)
                put("timestamp", d.timestamp); put("deletedAt", d.deletedAt)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CONVS, arr.toString()).apply()
    }

    // ── Messages ───────────────────────────────────────────────────────────────

    fun addMessage(ctx: Context, msg: SmsMessage) {
        val list = getMessages(ctx).toMutableList()
        list.removeAll { it.id == msg.id }
        list.add(0, DeletedMessage(msg.id, msg.threadId, msg.address, msg.body,
            msg.timestamp, msg.isMms, System.currentTimeMillis()))
        if (list.size > 500) list.subList(500, list.size).clear()
        saveMsgs(ctx, list)
    }

    fun getMessages(ctx: Context): List<DeletedMessage> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MSGS, null)
            ?: return emptyList()
        val all = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DeletedMessage(o.getLong("id"), o.getLong("threadId"), o.getString("address"),
                    o.getString("body"), o.getLong("timestamp"), o.getBoolean("isMms"),
                    o.getLong("deletedAt"))
            }
        } catch (_: Exception) { emptyList() }
        val cutoff = System.currentTimeMillis() - EXPIRY_MS
        val active = all.filter { it.deletedAt >= cutoff }
        if (active.size != all.size) saveMsgs(ctx, active)
        return active
    }

    private fun saveMsgs(ctx: Context, list: List<DeletedMessage>) {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id); put("threadId", d.threadId); put("address", d.address)
                put("body", d.body); put("timestamp", d.timestamp)
                put("isMms", d.isMms); put("deletedAt", d.deletedAt)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MSGS, arr.toString()).apply()
    }
}
