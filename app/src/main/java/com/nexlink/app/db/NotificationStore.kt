package com.nexlink.app.db

import androidx.lifecycle.MutableLiveData

data class SocialNotification(
    val id: String,
    val platform: String,
    val packageName: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val isReaction: Boolean = false
)

object NotificationStore {
    val notifications = MutableLiveData<List<SocialNotification>>(emptyList())

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
        "com.valvesoftware.android.steam.communityapp"       to "Steam"
    )

    val watchedPackages: Set<String> get() = PLATFORM_MAP.keys

    fun platform(pkg: String) = PLATFORM_MAP[pkg] ?: pkg

    fun add(n: SocialNotification) {
        val current = notifications.value.orEmpty().toMutableList()
        current.removeAll { it.id == n.id }
        current.add(0, n)
        if (current.size > 200) current.subList(200, current.size).clear()
        notifications.postValue(current)
    }

    fun remove(key: String) {
        val current = notifications.value.orEmpty().filter { it.id != key }
        notifications.postValue(current)
    }

    fun removeThread(platform: String, sender: String) {
        val current = notifications.value.orEmpty()
            .filter { !(it.platform == platform && it.sender == sender) }
        notifications.postValue(current)
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
            it.platform == platform && it.sender == sender && it.timestamp > readAt
        }
    }
}
