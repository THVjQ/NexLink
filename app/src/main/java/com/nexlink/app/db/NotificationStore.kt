package com.nexlink.app.db

import android.content.Context
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray
import org.json.JSONObject

data class SocialNotification(
    val id: String,
    val platform: String,
    val packageName: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val isReaction: Boolean = false,
    val outgoing: Boolean = false
)

object NotificationStore {
    val notifications = MutableLiveData<List<SocialNotification>>(emptyList())

    private const val PREFS = "nx_social_notifs"
    private const val KEY   = "social_notifs"

    private val PLATFORM_MAP = mapOf(
        "org.thoughtcrime.securesms"                         to "Signal",
        "org.telegram.messenger"                             to "Telegram",
        "org.telegram.messenger.web"                         to "Telegram",
        "com.whatsapp"                                       to "WhatsApp",
        "com.whatsapp.w4b"                                   to "WhatsApp",
        "com.facebook.orca"                                  to "Messenger",
        "com.facebook.mlite"                                 to "Messenger",
        "com.discord"                                        to "Discord",
        "com.instagram.android"                              to "Instagram",
        "com.valvesoftware.android.steam.community"           to "Steam"
    )

    val watchedPackages: Set<String> get() = PLATFORM_MAP.keys

    fun platform(pkg: String) = PLATFORM_MAP[pkg] ?: pkg

    fun load(ctx: Context) {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<SocialNotification>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(SocialNotification(
                    id          = o.getString("id"),
                    platform    = o.getString("platform"),
                    packageName = o.getString("packageName"),
                    sender      = o.getString("sender"),
                    text        = o.getString("text"),
                    timestamp   = o.getLong("timestamp"),
                    isReaction  = o.optBoolean("isReaction", false),
                    outgoing    = o.optBoolean("outgoing", false)
                ))
            }
            notifications.postValue(list)
        } catch (_: Exception) {}
    }

    private fun save(ctx: Context) {
        val list = notifications.value.orEmpty()
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("platform", n.platform)
                put("packageName", n.packageName)
                put("sender", n.sender)
                put("text", n.text)
                put("timestamp", n.timestamp)
                put("isReaction", n.isReaction)
                put("outgoing", n.outgoing)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString()).apply()
    }

    fun add(n: SocialNotification, ctx: Context? = null) {
        val current = notifications.value.orEmpty().toMutableList()
        current.removeAll { it.id == n.id }
        current.add(0, n)
        if (current.size > 200) current.subList(200, current.size).clear()
        notifications.postValue(current)
        ctx?.let { save(it) }
    }

    fun remove(key: String, ctx: Context? = null) {
        val current = notifications.value.orEmpty().filter { it.id != key }
        notifications.postValue(current)
        ctx?.let { save(it) }
    }

    fun removeThread(platform: String, sender: String, ctx: Context? = null) {
        val current = notifications.value.orEmpty()
            .filter { !(it.platform == platform && it.sender == sender) }
        notifications.postValue(current)
        ctx?.let { save(it) }
    }

    // ── Read state tracking ──
    private val readMap = mutableMapOf<String, Long>()

    fun markRead(platform: String, sender: String) {
        readMap["$platform|$sender"] = System.currentTimeMillis()
        notifications.postValue(notifications.value)
    }

    fun unreadCountFor(platform: String, sender: String): Int {
        val readAt = readMap["$platform|$sender"] ?: 0L
        return notifications.value.orEmpty().count {
            it.platform == platform && it.sender == sender &&
                it.timestamp > readAt && !it.outgoing
        }
    }
}
