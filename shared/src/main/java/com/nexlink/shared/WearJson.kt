package com.nexlink.shared

import org.json.JSONArray
import org.json.JSONObject

fun Conversation.toJson(): JSONObject {
    val participantsArr = JSONArray()
    participants.forEach { participantsArr.put(it) }
    return JSONObject().apply {
        put("threadId",    threadId)
        put("address",     address)
        put("participants", participantsArr)
        put("contactName", contactName)
        put("lastMessage", lastMessage)
        put("timestamp",   timestamp)
        put("unreadCount", unreadCount)
    }
}

fun JSONObject.toConversation(): Conversation {
    val pArr = getJSONArray("participants")
    return Conversation(
        threadId     = getLong("threadId"),
        address      = getString("address"),
        participants = (0 until pArr.length()).map { pArr.getString(it) },
        contactName  = getString("contactName"),
        lastMessage  = getString("lastMessage"),
        timestamp    = getLong("timestamp"),
        unreadCount  = getInt("unreadCount")
    )
}

fun SmsMessage.toJson(): JSONObject = JSONObject().apply {
    put("id",             id)
    put("threadId",       threadId)
    put("address",        address)
    put("body",           body)
    put("timestamp",      timestamp)
    put("isIncoming",     isIncoming)
    put("senderName",     senderName ?: JSONObject.NULL)
    put("isMms",          isMms)
    put("isVoice",        isVoice)
    put("mediaUri",       mediaUri  ?: JSONObject.NULL)
    put("mimeType",       mimeType  ?: JSONObject.NULL)
    put("deliveryStatus", deliveryStatus)
}

fun JSONObject.toSmsMessage(): SmsMessage = SmsMessage(
    id             = getLong("id"),
    threadId       = getLong("threadId"),
    address        = getString("address"),
    body           = getString("body"),
    timestamp      = getLong("timestamp"),
    isIncoming     = getBoolean("isIncoming"),
    senderName     = if (isNull("senderName"))  null else getString("senderName"),
    isMms          = getBoolean("isMms"),
    isVoice        = getBoolean("isVoice"),
    mediaUri       = if (isNull("mediaUri"))  null else getString("mediaUri"),
    mimeType       = if (isNull("mimeType"))  null else getString("mimeType"),
    deliveryStatus = getInt("deliveryStatus")
)
