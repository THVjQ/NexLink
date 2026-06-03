package com.nexlink.app.db

import android.content.Context
import android.provider.ContactsContract
import android.provider.Telephony

data class Conversation(
    val address: String,
    val contactName: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int
)

data class SmsMessage(
    val id: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val isIncoming: Boolean
)

object SmsHelper {

    fun getConversations(ctx: Context): List<Conversation> {
        val list = mutableListOf<Conversation>()
        val uri = Telephony.Sms.CONTENT_URI
        val proj = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ
        )
        val seen = mutableSetOf<String>()
        try {
            ctx.contentResolver.query(uri, proj, null, null, "${Telephony.Sms.DATE} DESC")?.use { c ->
                while (c.moveToNext()) {
                    val addr = c.getString(0) ?: continue
                    if (addr in seen) continue
                    seen += addr
                    val body    = c.getString(1) ?: ""
                    val date    = c.getLong(2)
                    val unread  = unreadCount(ctx, addr)
                    list += Conversation(addr, getContactName(ctx, addr), body, date, unread)
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun getMessages(ctx: Context, address: String): List<SmsMessage> {
        val list = mutableListOf<SmsMessage>()
        val uri = Telephony.Sms.CONTENT_URI
        val proj = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
        val sel = "${Telephony.Sms.ADDRESS} = ?"
        try {
            ctx.contentResolver.query(uri, proj, sel, arrayOf(address), "${Telephony.Sms.DATE} ASC")?.use { c ->
                while (c.moveToNext()) {
                    list += SmsMessage(
                        id         = c.getLong(0),
                        address    = c.getString(1) ?: address,
                        body       = c.getString(2) ?: "",
                        timestamp  = c.getLong(3),
                        isIncoming = c.getInt(4) == Telephony.Sms.MESSAGE_TYPE_INBOX
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun unreadCount(ctx: Context, address: String): Int {
        var count = 0
        val uri = Telephony.Sms.CONTENT_URI
        val sel = "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0"
        try {
            ctx.contentResolver.query(uri, arrayOf(Telephony.Sms._ID), sel, arrayOf(address), null)?.use {
                count = it.count
            }
        } catch (_: Exception) {}
        return count
    }

    fun getContactName(ctx: Context, address: String): String {
        if (address.isBlank()) return address
        try {
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(address).build()
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) return it.getString(0)
            }
        } catch (_: Exception) {}
        return address
    }

    fun sendSms(ctx: Context, address: String, body: String) {
        android.telephony.SmsManager.getDefault().sendTextMessage(address, null, body, null, null)
        val values = android.content.ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            put(Telephony.Sms.READ, 1)
        }
        try { ctx.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values) } catch (_: Exception) {}
    }

    fun markRead(ctx: Context, address: String) {
        val values = android.content.ContentValues().apply { put(Telephony.Sms.READ, 1) }
        val sel = "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0"
        try { ctx.contentResolver.update(Telephony.Sms.CONTENT_URI, values, sel, arrayOf(address)) } catch (_: Exception) {}
    }
}
