package com.nexlink.app.db

import android.content.Context

/**
 * SharedPreferences for the optional **Computer Bridge** addon.
 *
 * Follows NexLink's small-prefs-object convention (see [NotificationPrefs], [IconPrefs]).
 * Its own pref file (`bridge_prefs`) so nothing here collides with core NexLink prefs.
 *
 * [enabled] is the master switch: every bridge code path checks it first, and it is false
 * on a fresh install, so nothing bridge-related runs, connects, or requests permissions until
 * the user completes the disclaimer + setup wizard (see ui/bridge).
 */
object BridgePrefs {
    private const val PREFS = "bridge_prefs"
    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Master switch — everything checks this first. OFF by default.
    fun isEnabled(ctx: Context) = p(ctx).getBoolean("enabled", false)
    fun setEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("enabled", v).apply()

    fun isDisclaimerAccepted(ctx: Context) = p(ctx).getBoolean("disclaimer_accepted", false)
    fun setDisclaimerAccepted(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("disclaimer_accepted", v).apply()

    fun getServerUrl(ctx: Context): String = p(ctx).getString("server_url", "") ?: ""
    fun setServerUrl(ctx: Context, v: String) = p(ctx).edit().putString("server_url", v).apply()

    fun getApiKey(ctx: Context): String = p(ctx).getString("api_key", "") ?: ""
    fun setApiKey(ctx: Context, v: String) = p(ctx).edit().putString("api_key", v).apply()

    /** Stable per-device id; generated on first link if empty. */
    fun getDeviceId(ctx: Context): String {
        val existing = p(ctx).getString("device_id", "") ?: ""
        if (existing.isNotEmpty()) return existing
        val fresh = java.util.UUID.randomUUID().toString()
        p(ctx).edit().putString("device_id", fresh).apply()
        return fresh
    }

    fun isLinked(ctx: Context) = p(ctx).getBoolean("is_linked", false)
    fun setLinked(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("is_linked", v).apply()

    fun getPollIntervalSeconds(ctx: Context) = p(ctx).getInt("poll_interval", 5).coerceIn(2, 120)
    fun setPollIntervalSeconds(ctx: Context, v: Int) = p(ctx).edit().putInt("poll_interval", v.coerceIn(2, 120)).apply()

    fun isForwardIncoming(ctx: Context) = p(ctx).getBoolean("forward_incoming", true)
    fun setForwardIncoming(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("forward_incoming", v).apply()

    fun isNotifyIncoming(ctx: Context) = p(ctx).getBoolean("notify_incoming", true)
    fun setNotifyIncoming(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("notify_incoming", v).apply()

    /**
     * Idempotency ledger (§16.2): ids of outbound messages already handed to the SMS stack,
     * so a re-poll after a failed mark-sent does not double-text the customer. Bounded ring.
     */
    fun wasHandled(ctx: Context, id: Int): Boolean =
        (p(ctx).getString("handled_ids", "") ?: "").split(",").contains(id.toString())

    fun markHandled(ctx: Context, id: Int) {
        val cur = (p(ctx).getString("handled_ids", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableList()
        if (cur.contains(id.toString())) return
        cur.add(id.toString())
        while (cur.size > 500) cur.removeAt(0)
        p(ctx).edit().putString("handled_ids", cur.joinToString(",")).apply()
    }

    /**
     * Ledger of telephony SMS row ids that were sent *from the computer* through the bridge, so the
     * conversation view can badge them with a "sent via PC" symbol. Bounded ring, survives restarts.
     */
    fun markBridgeSent(ctx: Context, smsRowId: Long) {
        if (smsRowId <= 0) return
        val cur = (p(ctx).getString("pc_sent_ids", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableList()
        if (cur.contains(smsRowId.toString())) return
        cur.add(smsRowId.toString())
        while (cur.size > 1000) cur.removeAt(0)
        p(ctx).edit().putString("pc_sent_ids", cur.joinToString(",")).apply()
    }

    fun isBridgeSent(ctx: Context, smsRowId: Long): Boolean {
        if (smsRowId <= 0) return false
        return (p(ctx).getString("pc_sent_ids", "") ?: "").split(",").contains(smsRowId.toString())
    }

    /**
     * Replies that could not be forwarded yet — no desktop key known, or the network was down.
     * Persisted so a reboot does not lose them; previously such a reply was dropped outright, which
     * meant a customer's answer could vanish with no trace at either end.
     *
     * Bounded: the oldest go first if the bridge stays broken long enough to fill it. The contents
     * are message bodies, so they are cleared by the Forget-server teardown along with everything
     * else. They are stored unencrypted in app-private storage, which is the same protection the
     * SMS database itself has on the device.
     */
    private const val INCOMING_QUEUE_MAX = 200

    fun queueIncoming(ctx: Context, from: String, message: String) {
        val arr = runCatching { org.json.JSONArray(p(ctx).getString("incoming_queue", "[]")) }
            .getOrDefault(org.json.JSONArray())
        arr.put(org.json.JSONObject().put("from", from).put("message", message).put("ts", System.currentTimeMillis()))
        while (arr.length() > INCOMING_QUEUE_MAX) arr.remove(0)
        p(ctx).edit().putString("incoming_queue", arr.toString()).apply()
    }

    /** Removes and returns everything queued. The caller re-queues whatever it fails to send. */
    fun takeQueuedIncoming(ctx: Context): List<Pair<String, String>> {
        val raw = p(ctx).getString("incoming_queue", "[]") ?: "[]"
        if (raw == "[]") return emptyList()
        p(ctx).edit().remove("incoming_queue").apply()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.getString("from") to o.getString("message") } }
        }.getOrDefault(emptyList())
    }

    fun queuedIncomingCount(ctx: Context): Int =
        runCatching { org.json.JSONArray(p(ctx).getString("incoming_queue", "[]")).length() }.getOrDefault(0)

    /** Full teardown for the Disable / Forget-server path (§8). Optionally clears server config. */
    fun clearAll(ctx: Context, forgetServer: Boolean) {
        val e = p(ctx).edit()
        e.putBoolean("enabled", false).putBoolean("is_linked", false)
        if (forgetServer) {
            e.remove("server_url").remove("api_key").remove("handled_ids").remove("pc_sent_ids")
             .remove("incoming_queue")
        }
        e.apply()
    }

    fun serverHost(ctx: Context): String = try {
        val u = getServerUrl(ctx)
        val withScheme = if (u.startsWith("http")) u else "https://$u"
        java.net.URI(withScheme).host ?: u
    } catch (_: Exception) { getServerUrl(ctx) }
}
