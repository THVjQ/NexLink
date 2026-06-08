package com.nexlink.app.db

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Telephony
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class MmsApn(val mmsc: String, val proxy: String?, val port: Int)

/**
 * Sends an MMS PDU via direct HTTP POST to the carrier MMSC.
 *
 * Samsung's sendMultimediaMessage() rebuilds the PDU internally and corrupts
 * the Transaction-ID field before forwarding to the MMSC, causing "3514:
 * Internal server error" responses.  By going directly we control the full
 * PDU and get a clean, parseable MMSC response.
 */
object MmsSender {
    private const val TAG = "NexLink_MMS"

    /**
     * Synchronously sends pduBytes to the MMSC.
     * Returns 0 on success (HTTP 200 + Response-Status OK),
     * negative on local error, or the HTTP status code on HTTP failure.
     * Must be called from a background thread.
     */
    fun send(ctx: Context, pduBytes: ByteArray, subId: Int): Int {
        val apn = getApn(ctx, subId)
        if (apn == null) {
            android.util.Log.e(TAG, "MmsSender: no MMS APN — cannot send directly")
            return -1
        }
        android.util.Log.d(TAG, "MmsSender: mmsc=${apn.mmsc} proxy=${apn.proxy}:${apn.port}")

        val latch  = CountDownLatch(1)
        var result = -99

        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            private var done = false
            override fun onAvailable(network: Network) {
                if (done) return; done = true
                result = try {
                    // Attempt 1: socket-level binding (blocked by Samsung SELinux on most firmwares)
                    httpPost(network, apn, pduBytes)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "MmsSender: socket bind failed (${e.message}), trying bindProcessToNetwork")
                    try {
                        // Attempt 2: process-level binding — routes all sockets through MMS APN
                        cm.bindProcessToNetwork(network)
                        val r = httpPostDirect(apn, pduBytes)
                        cm.bindProcessToNetwork(null)
                        r
                    } catch (e2: Exception) {
                        android.util.Log.e(TAG, "MmsSender: bindProcessToNetwork failed: ${e2.message}")
                        cm.bindProcessToNetwork(null)
                        -2
                    }
                }
                cm.unregisterNetworkCallback(this)
                latch.countDown()
            }
            override fun onUnavailable() {
                if (done) return; done = true
                android.util.Log.e(TAG, "MmsSender: MMS network unavailable")
                result = -3
                latch.countDown()
            }
        }

        cm.requestNetwork(req, cb, 30_000)
        latch.await(35, TimeUnit.SECONDS)
        android.util.Log.d(TAG, "MmsSender: result=$result")
        return result
    }

    private fun httpPostDirect(apn: MmsApn, pduBytes: ByteArray): Int {
        val url  = URL(apn.mmsc)
        val conn = (if (!apn.proxy.isNullOrBlank() && apn.port > 0)
            url.openConnection(Proxy(Proxy.Type.HTTP, InetSocketAddress(apn.proxy, apn.port)))
        else
            url.openConnection()) as HttpURLConnection
        return doHttpPost(conn, apn, pduBytes)
    }

    private fun httpPost(network: Network, apn: MmsApn, pduBytes: ByteArray): Int {
        val url  = URL(apn.mmsc)
        val conn = (if (!apn.proxy.isNullOrBlank() && apn.port > 0)
            network.openConnection(url, Proxy(Proxy.Type.HTTP, InetSocketAddress(apn.proxy, apn.port)))
        else
            network.openConnection(url)) as HttpURLConnection
        return doHttpPost(conn, apn, pduBytes)
    }

    private fun doHttpPost(conn: java.net.URLConnection, apn: MmsApn, pduBytes: ByteArray): Int {
        val hc = conn as HttpURLConnection
        try {
            hc.requestMethod = "POST"
            hc.setRequestProperty("Content-Type",   "application/vnd.wap.mms-message")
            hc.setRequestProperty("Accept",         "*/*, application/vnd.wap.mms-message")
            hc.setRequestProperty("User-Agent",     "Android-Mms/2.0 dalvik/2.1.0")
            hc.setRequestProperty("x-wap-profile",  "http://www.gstatic.com/android/sms/mms_ua_profile.xml")
            hc.setRequestProperty("Connection",     "close")   // WAP proxies don't support keep-alive
            hc.connectTimeout = if (!apn.proxy.isNullOrBlank()) 15_000 else 20_000
            hc.readTimeout    = 20_000
            hc.doOutput = true
            hc.doInput  = true
            hc.setFixedLengthStreamingMode(pduBytes.size)   // prevent chunked transfer encoding

            hc.outputStream.use { it.write(pduBytes) }

            val code = hc.responseCode
            val body = runCatching { hc.inputStream.use { it.readBytes() } }.getOrNull()
            val hex  = body?.take(48)?.joinToString(" ") { "%02X".format(it) } ?: "empty"
            android.util.Log.d(TAG, "MmsSender: HTTP $code  response=$hex")

            if (body != null) {
                val status = parseMSendConfStatus(body)
                val txId   = parseMSendConfField(body, 0x98)
                android.util.Log.d(TAG,
                    "MmsSender: mmscStatus=${status?.let { "0x%02X".format(it) } ?: "none"}  echoTxId=$txId")
            }

            return if (code == 200) 0 else code
        } finally {
            hc.disconnect()
        }
    }

    fun getApn(ctx: Context, subId: Int): MmsApn? {
        // Strategy 1: standard APN table (blocked on Samsung One UI even for default SMS app)
        getApnFromTable(ctx, subId)?.let { return it }
        // Strategy 2: preferapn URI (less restricted on some Samsung firmwares)
        getApnFromPreferapn(ctx)?.let { return it }
        // Strategy 3: look up by SIM operator MCC+MNC / carrier name
        return getApnFromCarrier(ctx, subId)
    }

    private fun getApnFromTable(ctx: Context, subId: Int): MmsApn? {
        val where = buildString {
            append("(type='mms' OR type LIKE 'mms,%' OR type LIKE '%,mms' OR type LIKE '%,mms,%')")
            if (subId >= 0) append(" AND sub_id=$subId")
        }
        return try {
            ctx.contentResolver.query(
                Telephony.Carriers.CONTENT_URI,
                arrayOf("mmsc", "mmsproxy", "mmsport"),
                where, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val mmsc = c.getString(0)?.takeIf { it.isNotBlank() } ?: return null
                    MmsApn(mmsc, c.getString(1)?.takeIf { it.isNotBlank() }, c.getString(2)?.toIntOrNull() ?: 80)
                } else null
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "MmsSender: APN table query failed: ${e.message}")
            null
        }
    }

    private fun getApnFromPreferapn(ctx: Context): MmsApn? {
        for (uri in listOf("content://telephony/carriers/preferapn",
                           "content://telephony/carriers/current")) {
            try {
                ctx.contentResolver.query(
                    android.net.Uri.parse(uri),
                    arrayOf("mmsc", "mmsproxy", "mmsport"),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val mmsc = c.getString(0)?.takeIf { it.isNotBlank() } ?: return@use
                        android.util.Log.d(TAG, "MmsSender: APN from $uri mmsc=$mmsc")
                        return MmsApn(mmsc, c.getString(1)?.takeIf { it.isNotBlank() }, c.getString(2)?.toIntOrNull() ?: 80)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "MmsSender: $uri query failed: ${e.message}")
            }
        }
        return null
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun getApnFromCarrier(ctx: Context, subId: Int): MmsApn? {
        return try {
            val tm  = ctx.getSystemService(android.telephony.TelephonyManager::class.java)
            val sub = if (subId >= 0) tm.createForSubscriptionId(subId) else tm
            val op   = sub.simOperator ?: ""           // MCC+MNC, e.g. "50501"
            val name = sub.simOperatorName?.lowercase() ?: ""
            android.util.Log.d(TAG, "MmsSender: carrier operator=$op name=$name")
            when {
                // ── Australia ─────────────────────────────────────────────────
                op == "50501" || name.contains("telstra") || name.contains("boost") || name.contains("aldi") ->
                    MmsApn("http://mmsc.telstra.com:8002/", "10.1.1.180", 8002)
                op == "50502" || name.contains("optus") || name.contains("amaysim") || name.contains("spintel") || name.contains("moose") ->
                    MmsApn("http://mmsc.optus.com.au:8002/", "61.88.190.10", 8070)
                op == "50503" || name.contains("vodafone") || name.contains("voda") || name.contains("tpg") ->
                    MmsApn("http://pxt.vodafone.net.au/pxtsend", "10.202.2.60", 8080)
                op == "50506" || name.contains("nbn") ->
                    MmsApn("http://mmsc.optus.com.au:8002/mms", "10.128.1.2", 8070)
                // ── New Zealand ───────────────────────────────────────────────
                op == "53001" || name.contains("spark") || name.contains("telecom nz") ->
                    MmsApn("http://mms.vodafone.co.nz/servlets/mms", "172.21.12.50", 8080)
                op == "53005" || name.contains("2degrees") ->
                    MmsApn("http://mms.2degreesmobile.co.nz/", null, 0)
                // ── UK ────────────────────────────────────────────────────────
                op == "23430" || name.contains("ee") ->
                    MmsApn("http://mms/", "149.254.201.135", 8080)
                op == "23410" || name.contains("o2") ->
                    MmsApn("http://mmsc.mms.o2.co.uk:8002", "193.113.200.195", 8080)
                op == "23420" || name.contains("three") || name.contains("3uk") ->
                    MmsApn("http://mms.um.three.co.uk:10021/mmsc", "mms.three.co.uk", 8799)
                op == "23415" || name.contains("vodafone uk") ->
                    MmsApn("http://mms.vodafone.co.uk/servlets/mms", "212.183.137.12", 8080)
                // ── US ────────────────────────────────────────────────────────
                op == "310260" || name.contains("t-mobile") ->
                    MmsApn("http://mms.msg.eng.t-mobile.com/mms/wapenc", null, 0)
                op.startsWith("3107") || name.contains("verizon") ->
                    MmsApn("http://mms.vtext.com/servlets/mms", null, 0)
                op == "310410" || name.contains("at&t") ->
                    MmsApn("http://mmsc.mobile.att.net", "proxy.mobile.att.net", 80)
                else -> {
                    android.util.Log.e(TAG, "MmsSender: unknown carrier op=$op name=$name — cannot send directly")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "MmsSender: carrier lookup failed: ${e.message}")
            null
        }
    }

    private fun parseMSendConfStatus(pdu: ByteArray): Int? {
        var i = 0
        while (i < pdu.size - 1) {
            val f = pdu[i++].toInt() and 0xFF
            if (i >= pdu.size) break
            if (f == 0x91) return pdu[i].toInt() and 0xFF
            i += skipValue(pdu, i)
        }
        return null
    }

    private fun parseMSendConfField(pdu: ByteArray, fieldCode: Int): String? {
        var i = 0
        while (i < pdu.size - 1) {
            val f = pdu[i++].toInt() and 0xFF
            if (i >= pdu.size) break
            if (f == fieldCode) {
                val start = i
                while (i < pdu.size && pdu[i] != 0.toByte()) i++
                return String(pdu, start, i - start, Charsets.ISO_8859_1)
            }
            i += skipValue(pdu, i)
        }
        return null
    }

    private fun skipValue(pdu: ByteArray, i: Int): Int {
        if (i >= pdu.size) return 1
        val v = pdu[i].toInt() and 0xFF
        return when {
            v and 0x80 != 0 -> 1
            v in 1..30      -> 1 + v
            else            -> { var e = i; while (e < pdu.size && pdu[e] != 0.toByte()) e++; e - i + 1 }
        }
    }
}
