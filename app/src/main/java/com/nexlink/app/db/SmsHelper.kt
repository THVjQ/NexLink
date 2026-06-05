package com.nexlink.app.db

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat

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

data class SimInfo(val subscriptionId: Int, val displayName: String, val slotIndex: Int)

object SmsHelper {

    private val contactCache = HashMap<String, String>()

    fun getConversations(ctx: Context, limit: Int = 250): List<Conversation> {
        val list = mutableListOf<Conversation>()
        val uri = Telephony.Sms.CONTENT_URI
        val proj = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)

        // Single upfront pass: gather unread counts for all addresses at once
        val unreadMap = buildUnreadMap(ctx)

        val seen = mutableSetOf<String>()
        try {
            ctx.contentResolver.query(uri, proj, null, null, "${Telephony.Sms.DATE} DESC")?.use { c ->
                while (c.moveToNext()) {
                    if (limit > 0 && seen.size >= limit) break
                    val addr = c.getString(0) ?: continue
                    if (addr in seen) continue
                    seen += addr
                    list += Conversation(
                        address      = addr,
                        contactName  = getContactName(ctx, addr),
                        lastMessage  = c.getString(1) ?: "",
                        timestamp    = c.getLong(2),
                        unreadCount  = unreadMap[addr] ?: 0
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    /** One query to get ALL unread rows, grouped by address. Replaces N per-address queries. */
    private fun buildUnreadMap(ctx: Context): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.READ} = 0",
                null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val addr = c.getString(0) ?: continue
                    map[addr] = (map[addr] ?: 0) + 1
                }
            }
        } catch (_: Exception) {}
        return map
    }

    fun getMessages(ctx: Context, address: String, limit: Int = 300): List<SmsMessage> {
        val list = mutableListOf<SmsMessage>()
        val proj = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                           Telephony.Sms.DATE, Telephony.Sms.TYPE)
        val orderBy = if (limit > 0) "${Telephony.Sms.DATE} ASC LIMIT $limit" else "${Telephony.Sms.DATE} ASC"
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI, proj,
                "${Telephony.Sms.ADDRESS} = ?", arrayOf(address),
                orderBy
            )?.use { c ->
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

    fun getContactName(ctx: Context, address: String): String {
        if (address.isBlank()) return address
        contactCache[address]?.let { return it }
        try {
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(address).build()
            ctx.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use {
                if (it.moveToFirst()) {
                    val name = it.getString(0)
                    contactCache[address] = name
                    return name
                }
            }
        } catch (_: Exception) {}
        return address
    }

    @SuppressLint("MissingPermission")
    fun getSims(ctx: Context): List<SimInfo> {
        return try {
            val sm = ctx.getSystemService(android.telephony.SubscriptionManager::class.java) ?: return emptyList()
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
            (sm.activeSubscriptionInfoList ?: emptyList()).map { sub ->
                SimInfo(sub.subscriptionId, sub.displayName?.toString() ?: "SIM ${sub.simSlotIndex + 1}", sub.simSlotIndex)
            }
        } catch (e: Exception) { emptyList() }
    }

    fun sendSms(ctx: Context, address: String, body: String, subscriptionId: Int = -1) {
        @Suppress("DEPRECATION")
        val smsManager = if (subscriptionId >= 0 && android.os.Build.VERSION.SDK_INT >= 22)
            android.telephony.SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        else android.telephony.SmsManager.getDefault()

        smsManager.sendTextMessage(address, null, body, null, null)
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
