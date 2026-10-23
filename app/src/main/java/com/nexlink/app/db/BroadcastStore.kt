package com.nexlink.app.db

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Broadcast lists: one message, sent as a separate SMS to each member.
 *
 * Unlike a group MMS, nobody sees anyone else — each copy lands in, and each reply comes back to,
 * that person's ordinary 1:1 chat. So a broadcast has no telephony thread of its own; this store
 * is the only record of who is on the list and what was sent to it. Because it is just a list,
 * members can be added and removed in place, which a group cannot do.
 */
object BroadcastStore {
    private const val PREFS = "nx_broadcasts"
    private const val KEY   = "lists"
    /** Keeps the history bounded — the real copies live in each member's 1:1 chat anyway. */
    private const val MAX_SENT = 200

    data class Sent(val body: String, val timestamp: Long, val recipients: Int)
    data class Broadcast(val id: Long, val name: String, val members: List<String>,
                         val sent: List<Sent> = emptyList())

    fun getAll(ctx: Context): List<Broadcast> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val m = o.getJSONArray("members")
                val s = o.optJSONArray("sent") ?: JSONArray()
                Broadcast(
                    id      = o.getLong("id"),
                    name    = o.getString("name"),
                    members = (0 until m.length()).map { m.getString(it) },
                    sent    = (0 until s.length()).map {
                        val x = s.getJSONObject(it)
                        Sent(x.getString("body"), x.getLong("ts"), x.optInt("n"))
                    })
            }
        }.getOrDefault(emptyList())
    }

    fun get(ctx: Context, id: Long) = getAll(ctx).firstOrNull { it.id == id }

    fun create(ctx: Context, name: String, members: List<String>): Broadcast {
        val b = Broadcast(System.currentTimeMillis(), name, members)
        save(ctx, getAll(ctx) + b)
        return b
    }

    fun update(ctx: Context, b: Broadcast) =
        save(ctx, getAll(ctx).map { if (it.id == b.id) b else it })

    fun addSent(ctx: Context, id: Long, body: String, recipients: Int) {
        val b = get(ctx, id) ?: return
        update(ctx, b.copy(sent = (b.sent + Sent(body, System.currentTimeMillis(), recipients)).takeLast(MAX_SENT)))
    }

    fun delete(ctx: Context, id: Long) = save(ctx, getAll(ctx).filter { it.id != id })

    @Synchronized
    private fun save(ctx: Context, list: List<Broadcast>) {
        val arr = JSONArray()
        for (b in list) arr.put(JSONObject().apply {
            put("id", b.id); put("name", b.name)
            put("members", JSONArray(b.members))
            put("sent", JSONArray().apply {
                b.sent.forEach { put(JSONObject().put("body", it.body).put("ts", it.timestamp).put("n", it.recipients)) }
            })
        })
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
