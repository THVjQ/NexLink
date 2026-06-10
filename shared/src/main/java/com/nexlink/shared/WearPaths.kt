package com.nexlink.shared

object WearPaths {
    // Phone → Watch (DataClient)
    const val CONVERSATIONS  = "/nexlink/conversations"
    const val THREAD         = "/nexlink/thread/"   // + threadId appended by caller
    const val UNREAD_COUNT   = "/nexlink/unread_count"
    const val CANNED_REPLIES = "/nexlink/canned_replies"

    // Watch → Phone (MessageClient)
    const val SEND_SMS             = "/nexlink/send_sms"
    const val MARK_READ            = "/nexlink/mark_read"
    const val REQUEST_THREAD       = "/nexlink/request_thread"
    const val REQUEST_CONVERSATIONS = "/nexlink/request_conversations"
}
