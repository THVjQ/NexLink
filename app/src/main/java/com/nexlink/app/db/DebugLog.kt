package com.nexlink.app.db

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight, persistent, thread-safe event log for the in-app Debug menu.
 *
 * Every meaningful message event flows through here — SMS/MMS sent, SMS/MMS received,
 * social notifications shown, key exchange / encryption, delivery reports and errors —
 * so the whole pipeline can be inspected from Settings ▸ Debug without adb/logcat.
 *
 * The log is a bounded ring buffer (newest first) persisted to a single JSON file in
 * filesDir. Writes are serialised onto one background thread so any code path — receiver,
 * service, UI — can call [log] cheaply without blocking or racing.
 */
object DebugLog {

    // ── Categories ─────────────────────────────────────────────────────────────
    const val CAT_SENT     = "SENT"       // outgoing SMS/MMS the user sent
    const val CAT_RECEIVED = "RECEIVED"   // incoming SMS/MMS delivered to this phone
    const val CAT_SHOWN    = "SHOWN"      // social-app notifications surfaced in the inbox
    const val CAT_MMS      = "MMS"        // MMS pipeline detail (download/send transport)
    const val CAT_CRYPTO   = "CRYPTO"     // key exchange / encryption session state
    const val CAT_SYSTEM   = "SYSTEM"     // app lifecycle, permissions, misc
    const val CAT_ERROR    = "ERROR"      // failures anywhere in the pipeline

    val ALL_CATEGORIES = listOf(CAT_SENT, CAT_RECEIVED, CAT_SHOWN, CAT_MMS, CAT_CRYPTO, CAT_SYSTEM, CAT_ERROR)

    private const val MAX_EVENTS = 1000
    private const val FILE_NAME  = "nexlink_debug_log.json"

    data class Event(
        val timestamp: Long,
        val category: String,
        val tag: String,        // short context — usually an address, sender or platform
        val message: String
    )

    private val lock = Any()
    private val events = ArrayDeque<Event>()   // index 0 = newest
    private var loaded = false

    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "DebugLog").apply { isDaemon = true }
    }

    private val tsFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Record an event. Safe to call from any thread. */
    fun log(ctx: Context, category: String, tag: String, message: String) {
        val app = ctx.applicationContext
        val ev = Event(System.currentTimeMillis(), category, tag, message)
        synchronized(lock) {
            ensureLoaded(app)
            events.addFirst(ev)
            while (events.size > MAX_EVENTS) events.removeLast()
        }
        writer.execute { persist(app) }
    }

    /** Snapshot of all events, newest first. */
    fun all(ctx: Context): List<Event> = synchronized(lock) {
        ensureLoaded(ctx.applicationContext)
        events.toList()
    }

    fun count(ctx: Context, category: String): Int = synchronized(lock) {
        ensureLoaded(ctx.applicationContext)
        events.count { it.category == category }
    }

    fun clear(ctx: Context) {
        synchronized(lock) { events.clear() }
        writer.execute { persist(ctx.applicationContext) }
    }

    fun formatTime(ts: Long): String = tsFormat.format(Date(ts))

    /** Plain-text dump for copy / share. */
    fun dump(ctx: Context): String {
        val list = all(ctx)
        return buildString {
            appendLine("NexLink debug log — ${list.size} events")
            appendLine("Exported ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("─".repeat(40))
            list.forEach { e ->
                appendLine("${tsFormat.format(Date(e.timestamp))}  [${e.category}] ${e.tag}")
                appendLine("    ${e.message}")
            }
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    private fun ensureLoaded(app: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = File(app.filesDir, FILE_NAME)
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                events.addLast(Event(
                    timestamp = o.getLong("t"),
                    category  = o.getString("c"),
                    tag       = o.optString("g"),
                    message   = o.optString("m")
                ))
            }
        } catch (_: Exception) {}
    }

    private fun persist(app: Context) {
        val snapshot = synchronized(lock) { events.toList() }
        try {
            val arr = JSONArray()
            snapshot.forEach { e ->
                arr.put(JSONObject().apply {
                    put("t", e.timestamp); put("c", e.category)
                    put("g", e.tag); put("m", e.message)
                })
            }
            File(app.filesDir, FILE_NAME).writeText(arr.toString())
        } catch (_: Exception) {}
    }
}
