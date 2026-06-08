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

object MmsDownloader {
    private const val TAG = "NexLink_MMS"

    /**
     * Parses and stores a raw MMS PDU (RetrieveConf) that was already downloaded by
     * SmsManager.downloadMultimediaMessage().  Returns the sender address or null on error.
     * May be called from any thread.
     */
    fun storeRawPdu(ctx: Context, pduBytes: ByteArray): String? = storePdu(ctx, pduBytes, -1)

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
        return storePdu(ctx, pduBytes, subId)
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

    private fun storePdu(ctx: Context, pduBytes: ByteArray, subId: Int): String? {
        return try {
            val pdu = PduParser(pduBytes, true).parse()
            if (pdu !is RetrieveConf) {
                android.util.Log.w(TAG, "MmsDownloader: not a RetrieveConf (${pdu?.javaClass?.simpleName})")
                return null
            }
            val persister = PduPersister.getPduPersister(ctx)
            val uri = persister.persist(pdu, Telephony.Mms.Inbox.CONTENT_URI, true, true, null, subId)
            android.util.Log.d(TAG, "MmsDownloader: stored at $uri")

            ctx.contentResolver.update(uri, ContentValues().apply {
                put(Telephony.Mms.READ, 0)
                put(Telephony.Mms.SEEN, 0)
            }, null, null)

            // Extract sender — strip /TYPE=PLMN suffix that carriers append
            pdu.from?.textString
                ?.let { String(it, Charsets.UTF_8) }
                ?.substringBefore("/TYPE=")
                ?.trim()
                ?.ifBlank { null }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "MmsDownloader: store failed: ${e.message}")
            null
        }
    }
}
