package com.nexlink.wear.data

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.nexlink.shared.WearPaths
import org.json.JSONObject

object WearMessageClient {

    private fun phoneNodeId(ctx: Context): String? = try {
        Tasks.await(Wearable.getNodeClient(ctx).connectedNodes).firstOrNull()?.id
    } catch (_: Exception) { null }

    private fun send(ctx: Context, path: String, payload: JSONObject) {
        Thread {
            val nodeId = phoneNodeId(ctx) ?: return@Thread
            try {
                Wearable.getMessageClient(ctx)
                    .sendMessage(nodeId, path, payload.toString().toByteArray())
            } catch (_: Exception) {}
        }.start()
    }

    fun sendSms(ctx: Context, address: String, body: String, simIndex: Int = -1) {
        send(ctx, WearPaths.SEND_SMS, JSONObject().apply {
            put("address", address); put("body", body); put("simIndex", simIndex)
        })
    }

    fun markRead(ctx: Context, threadId: Long) {
        send(ctx, WearPaths.MARK_READ, JSONObject().apply { put("threadId", threadId) })
    }

    fun requestThread(ctx: Context, threadId: Long, address: String) {
        send(ctx, WearPaths.REQUEST_THREAD, JSONObject().apply {
            put("threadId", threadId); put("address", address)
        })
    }

    fun requestConversations(ctx: Context) {
        send(ctx, WearPaths.REQUEST_CONVERSATIONS, JSONObject())
    }
}
