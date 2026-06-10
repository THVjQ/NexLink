package com.nexlink.app.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.nexlink.app.db.SmsHelper
import com.nexlink.shared.WearPaths
import org.json.JSONObject

class WearMessageDelegate : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val ctx = applicationContext
        val body = String(event.data)
        when (event.path) {
            WearPaths.SEND_SMS             -> handleSendSms(body)
            WearPaths.MARK_READ            -> handleMarkRead(body)
            WearPaths.REQUEST_THREAD       -> handleRequestThread(body)
            WearPaths.REQUEST_CONVERSATIONS -> WearSync.pushConversations(ctx)
        }
    }

    private fun handleSendSms(json: String) {
        try {
            val obj     = JSONObject(json)
            val address = obj.getString("address")
            val body    = obj.getString("body")
            val simIdx  = obj.optInt("simIndex", -1)
            val ctx     = applicationContext
            val sims    = SmsHelper.getSims(ctx)
            val subId   = if (simIdx in sims.indices) sims[simIdx].subscriptionId else -1
            SmsHelper.sendSms(ctx, address, body, subId)
            WearSync.pushConversations(ctx)
        } catch (_: Exception) {}
    }

    private fun handleMarkRead(json: String) {
        try {
            val threadId = JSONObject(json).getLong("threadId")
            val ctx = applicationContext
            SmsHelper.markReadByThread(ctx, threadId)
            WearSync.pushConversations(ctx)
        } catch (_: Exception) {}
    }

    private fun handleRequestThread(json: String) {
        try {
            val obj      = JSONObject(json)
            val threadId = obj.getLong("threadId")
            val address  = obj.optString("address", "")
            WearSync.pushThread(applicationContext, threadId, address)
        } catch (_: Exception) {}
    }
}
