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
    val deliveryStatus: Int = -1,
    /** True when the row itself is a failed send (SMS TYPE=5 / MMS MESSAGE_BOX=5) — never left the device. */
    val isFailed: Boolean = false,
    /**
     * True while the row is still in the outbox or queued box (SMS TYPE=4/6, MMS MESSAGE_BOX=4):
     * handed to the radio but not yet sent. A successful send moves the row out of these boxes, so
     * one that stays put is stuck rather than slow.
     */
    val isPendingSend: Boolean = false
)

data class SimInfo(val subscriptionId: Int, val displayName: String, val slotIndex: Int, val number: String? = null)
