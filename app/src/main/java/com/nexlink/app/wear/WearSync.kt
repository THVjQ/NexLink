package com.nexlink.app.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.nexlink.app.db.SmsHelper
import com.nexlink.shared.WearPaths
import com.nexlink.shared.toJson
import org.json.JSONArray

object WearSync {

    fun pushConversations(ctx: Context) {
        Thread {
            try {
                val conversations = SmsHelper.getConversations(ctx, 20)
                val arr = JSONArray()
                conversations.forEach { arr.put(it.toJson()) }

                val req = PutDataMapRequest.create(WearPaths.CONVERSATIONS).apply {
                    dataMap.putString("json", arr.toString())
                    dataMap.putLong("ts", System.currentTimeMillis())
                }
                Wearable.getDataClient(ctx).putDataItem(req.asPutDataRequest().setUrgent())

                val unread = conversations.sumOf { it.unreadCount }
                val unreadReq = PutDataMapRequest.create(WearPaths.UNREAD_COUNT).apply {
                    dataMap.putInt("count", unread)
                    dataMap.putLong("ts", System.currentTimeMillis())
                }
                Wearable.getDataClient(ctx).putDataItem(unreadReq.asPutDataRequest().setUrgent())
            } catch (_: Exception) {}
        }.start()
    }

    fun pushThread(ctx: Context, threadId: Long, primaryAddress: String) {
        Thread {
            try {
                val messages = SmsHelper.getMessagesByThread(ctx, threadId, primaryAddress)
                    .takeLast(50)
                val arr = JSONArray()
                messages.forEach { arr.put(it.toJson()) }

                val req = PutDataMapRequest.create("${WearPaths.THREAD}$threadId").apply {
                    dataMap.putString("json", arr.toString())
                    dataMap.putLong("ts", System.currentTimeMillis())
                }
                Wearable.getDataClient(ctx).putDataItem(req.asPutDataRequest().setUrgent())
            } catch (_: Exception) {}
        }.start()
    }

    fun pushCannedReplies(ctx: Context) {
        Thread {
            try {
                val replies = getCannedReplies(ctx)
                val arr = JSONArray()
                replies.forEach { arr.put(it) }

                val req = PutDataMapRequest.create(WearPaths.CANNED_REPLIES).apply {
                    dataMap.putString("json", arr.toString())
                    dataMap.putLong("ts", System.currentTimeMillis())
                }
                Wearable.getDataClient(ctx).putDataItem(req.asPutDataRequest().setUrgent())
            } catch (_: Exception) {}
        }.start()
    }

    fun getCannedReplies(ctx: Context): List<String> {
        val prefs = ctx.getSharedPreferences("nx_wear", Context.MODE_PRIVATE)
        val json = prefs.getString("canned_replies", null)
        if (json != null) {
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) { defaults() }
        }
        return defaults()
    }

    fun saveCannedReplies(ctx: Context, replies: List<String>) {
        val arr = JSONArray()
        replies.forEach { arr.put(it) }
        ctx.getSharedPreferences("nx_wear", Context.MODE_PRIVATE)
            .edit().putString("canned_replies", arr.toString()).apply()
        pushCannedReplies(ctx)
    }

    private fun defaults() = listOf("OK", "On my way", "Can't talk, will call back", "Thanks!")
}
