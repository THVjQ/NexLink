package com.nexlink.app.db

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Telephony
import com.google.android.mms.pdu_alt.PduParser
import com.google.android.mms.pdu_alt.PduPersister
import com.google.android.mms.pdu_alt.RetrieveConf
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class MmsPduResult(val sender: String?, val notifText: String)

object MmsDownloader {
    private const val TAG = "NexLink_MMS"

    /**
     * Parses and stores a raw MMS PDU. Returns sender + notification text, or null on error.
     * May be called from any thread.
     */
    fun storeRawPdu(ctx: Context, pduBytes: ByteArray): MmsPduResult? = storePdu(ctx, pduBytes, -1)

    /**
     * Downloads the MMS PDU from contentLocation and stores it in content://mms.
     * Returns the normalised sender address, or null if the download or store failed.
     * Must be called from a background thread.
     */
    fun downloadAndStore(ctx: Context, contentLocation: String, subId: Int): String? {
        val pduBytes = fetchPdu(ctx, contentLocation, subId)
        if (pduBytes == null) {
            android.util.Log.e(TAG, "MmsDownloader: download failed for $contentLocation")
            return null
        }
        android.util.Log.d(TAG, "MmsDownloader: downloaded ${pduBytes.size}B")
        return storePdu(ctx, pduBytes, subId)?.sender
    }

    // ── Network fetch ──────────────────────────────────────────────────────────

    private fun fetchPdu(ctx: Context, url: String, subId: Int): ByteArray? {
        val apn = MmsSender.getApn(ctx, subId)
        if (apn == null) {
            android.util.Log.e(TAG, "MmsDownloader: no MMS APN — cannot download")
            return null
        }
        android.util.Log.d(TAG, "MmsDownloader: GET $url via mmsc=${apn.mmsc} proxy=${apn.proxy}:${apn.port}")

        val latch  = CountDownLatch(1)
        var result: ByteArray? = null

        val cm  = ctx.getSystemService(ConnectivityManager::class.java)
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            private var done = false
            override fun onAvailable(network: Network) {
                if (done) return; done = true
                result = try {
                    httpGet(network, apn, url)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "MmsDownloader: socket bind failed (${e.message}), retrying via bindProcessToNetwork")
                    try {
                        cm.bindProcessToNetwork(network)
                        val r = httpGetDirect(apn, url)
                        cm.bindProcessToNetwork(null)
                        r
                    } catch (e2: Exception) {
                        android.util.Log.e(TAG, "MmsDownloader: both fetch strategies failed: ${e2.message}")
                        cm.bindProcessToNetwork(null)
                        null
                    }
                }
                cm.unregisterNetworkCallback(this)
                latch.countDown()
            }
            override fun onUnavailable() {
                if (done) return; done = true
                android.util.Log.e(TAG, "MmsDownloader: MMS network unavailable")
                latch.countDown()
            }
        }

        cm.requestNetwork(req, cb, 30_000)
        latch.await(35, TimeUnit.SECONDS)
        return result
    }

    private fun httpGet(network: Network, apn: MmsApn, url: String): ByteArray? {
        val conn = (if (!apn.proxy.isNullOrBlank() && apn.port > 0)
            network.openConnection(URL(url), Proxy(Proxy.Type.HTTP, InetSocketAddress(apn.proxy, apn.port)))
        else
            network.openConnection(URL(url))) as HttpURLConnection
        return doGet(conn)
    }

    private fun httpGetDirect(apn: MmsApn, url: String): ByteArray? {
        val conn = (if (!apn.proxy.isNullOrBlank() && apn.port > 0)
            URL(url).openConnection(Proxy(Proxy.Type.HTTP, InetSocketAddress(apn.proxy, apn.port)))
        else
            URL(url).openConnection()) as HttpURLConnection
        return doGet(conn)
    }

    private fun doGet(conn: HttpURLConnection): ByteArray? {
        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept",        "*/*, application/vnd.wap.mms-message")
            conn.setRequestProperty("User-Agent",    "Android-Mms/2.0 dalvik/2.1.0")
            conn.setRequestProperty("x-wap-profile", "http://www.gstatic.com/android/sms/mms_ua_profile.xml")
            conn.setRequestProperty("Connection",    "close")
            conn.connectTimeout = 20_000
            conn.readTimeout    = 30_000
            val code = conn.responseCode
            android.util.Log.d(TAG, "MmsDownloader: HTTP $code")
            if (code == 200) conn.inputStream.use { it.readBytes() } else null
        } finally {
            conn.disconnect()
        }
    }

    // ── PDU store ──────────────────────────────────────────────────────────────

    private fun storePdu(ctx: Context, pduBytes: ByteArray, subId: Int): MmsPduResult? {
        return try {
            val pdu = PduParser(pduBytes, true).parse()
            if (pdu !is RetrieveConf) {
                android.util.Log.w(TAG, "MmsDownloader: not a RetrieveConf (${pdu?.javaClass?.simpleName})")
                return null
            }
            val persister = PduPersister.getPduPersister(ctx)
            val uri = persister.persist(pdu, Telephony.Mms.Inbox.CONTENT_URI, true, true, null, subId)
            android.util.Log.d(TAG, "MmsDownloader: stored at $uri")

            val sender = pdu.from?.textString
                ?.let { String(it, Charsets.UTF_8) }
                ?.substringBefore("/TYPE=")
                ?.trim()
                ?.ifBlank { null }

            // Pin the row to the canonical thread the conversation UI opens for this sender.
            // PduPersister derives a thread from the PDU addresses, which can differ in format
            // from the SMS thread — a text/link MMS would then be stored but invisible in the
            // chat (notification arrives, message "missing"). Force it to match.
            val threadId = if (!sender.isNullOrBlank())
                runCatching { Telephony.Threads.getOrCreateThreadId(ctx, sender) }.getOrDefault(0L) else 0L
            ctx.contentResolver.update(uri, ContentValues().apply {
                put(Telephony.Mms.READ, 0)
                put(Telephony.Mms.SEEN, 0)
                if (threadId > 0) put(Telephony.Mms.THREAD_ID, threadId)
            }, null, null)
            android.util.Log.d(TAG, "MmsDownloader: sender=$sender thread=$threadId")

            MmsPduResult(sender, extractNotifText(pdu))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "MmsDownloader: store failed: ${e.message}")
            null
        }
    }

    private fun extractNotifText(pdu: RetrieveConf): String {
        val body = pdu.body ?: return "📷 MMS"
        var hasImage = false; var hasVideo = false; var hasAudio = false; var hasFile = false
        var textContent: String? = null
        for (i in 0 until body.partsNum) {
            val part = body.getPart(i) ?: continue
            val typeBs = part.contentType ?: continue
            val type = String(typeBs, Charsets.ISO_8859_1).lowercase().substringBefore(";").trim()
            when {
                type == "application/smil" -> {}
                type.startsWith("text/") -> {
                    if (textContent == null && part.data != null)
                        textContent = String(part.data, Charsets.UTF_8).trim().ifBlank { null }
                }
                type.startsWith("image/") -> hasImage = true
                type.startsWith("video/") -> hasVideo = true
                type.startsWith("audio/") -> hasAudio = true
                else -> hasFile = true
            }
        }
        return when {
            textContent != null -> {
                val prefix = when {
                    hasImage -> "📷 "
                    hasVideo -> "🎬 "
                    hasAudio -> "🎵 "
                    hasFile  -> "📎 "
                    else     -> ""
                }
                "$prefix${textContent.take(100)}"
            }
            hasVideo -> "🎬 Video"
            hasAudio -> "🎵 Audio message"
            hasImage -> "📷 Photo"
            hasFile  -> "📎 File"
            else     -> "MMS"
        }
    }
}
