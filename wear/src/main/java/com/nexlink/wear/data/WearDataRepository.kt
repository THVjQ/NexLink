package com.nexlink.wear.data

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.nexlink.shared.Conversation
import com.nexlink.shared.SmsMessage
import com.nexlink.shared.WearPaths
import com.nexlink.shared.toConversation
import com.nexlink.shared.toSmsMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

object WearDataRepository {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _threads = MutableStateFlow<Map<Long, List<SmsMessage>>>(emptyMap())
    val threads: StateFlow<Map<Long, List<SmsMessage>>> = _threads

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    private val _cannedReplies = MutableStateFlow(
        listOf("OK", "On my way", "Can't talk, will call back", "Thanks!")
    )
    val cannedReplies: StateFlow<List<String>> = _cannedReplies

    fun handleDataEvent(event: DataEvent) {
        if (event.type != DataEvent.TYPE_CHANGED) return
        val path = event.dataItem.uri.path ?: return
        try {
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            when {
                path == WearPaths.UNREAD_COUNT -> {
                    _unreadCount.value = map.getInt("count", 0)
                }
                path == WearPaths.CONVERSATIONS -> {
                    val json = map.getString("json") ?: return
                    val arr  = JSONArray(json)
                    _conversations.value = (0 until arr.length()).map { arr.getJSONObject(it).toConversation() }
                }
                path.startsWith(WearPaths.THREAD) -> {
                    val json     = map.getString("json") ?: return
                    val threadId = path.removePrefix(WearPaths.THREAD).toLongOrNull() ?: return
                    val arr      = JSONArray(json)
                    val msgs     = (0 until arr.length()).map { arr.getJSONObject(it).toSmsMessage() }
                    _threads.value = _threads.value + (threadId to msgs)
                }
                path == WearPaths.CANNED_REPLIES -> {
                    val json = map.getString("json") ?: return
                    val arr  = JSONArray(json)
                    _cannedReplies.value = (0 until arr.length()).map { arr.getString(it) }
                }
            }
        } catch (_: Exception) {}
    }

    fun loadExisting(ctx: Context) {
        Wearable.getDataClient(ctx).dataItems.addOnSuccessListener { items ->
            items.forEach { item ->
                val path = item.uri.path ?: return@forEach
                try {
                    val map = DataMapItem.fromDataItem(item).dataMap
                    when (path) {
                        WearPaths.CONVERSATIONS -> {
                            val json = map.getString("json") ?: return@forEach
                            val arr  = JSONArray(json)
                            _conversations.value = (0 until arr.length()).map { arr.getJSONObject(it).toConversation() }
                        }
                        WearPaths.CANNED_REPLIES -> {
                            val json = map.getString("json") ?: return@forEach
                            val arr  = JSONArray(json)
                            _cannedReplies.value = (0 until arr.length()).map { arr.getString(it) }
                        }
                        WearPaths.UNREAD_COUNT -> {
                            _unreadCount.value = map.getInt("count", 0)
                        }
                    }
                } catch (_: Exception) {}
            }
            items.release()
        }
    }
}
