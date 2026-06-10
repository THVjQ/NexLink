package com.nexlink.shared

data class Conversation(
    val threadId: Long = 0,
    val address: String,
    val participants: List<String> = emptyList(),
    val contactName: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int
)

data class SmsMessage(
    val id: Long,
    val threadId: Long = 0,
    val address: String,
    val body: String,
    val timestamp: Long,
    val isIncoming: Boolean,
    val senderName: String? = null,
    val isMms: Boolean = false,
    val isVoice: Boolean = false,
    val mediaUri: String? = null,
    val mimeType: String? = null,
    /** Telephony.Sms.STATUS: -1=none, 0=complete/delivered, 32=pending, 64=failed */
    val deliveryStatus: Int = -1
)

data class SimInfo(val subscriptionId: Int, val displayName: String, val slotIndex: Int, val number: String? = null)
