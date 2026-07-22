package com.nexlink.app.db

import android.content.Context
import com.nexlink.app.crypto.BridgeCrypto
import com.nexlink.app.crypto.BridgeKeyManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client for the user's self-hosted bridge server. Ported from the reference
 * `ApiClient` with three deliberate changes:
 *
 *  • §16.1 — a real authenticated check ([verifyKey]) so the wizard can't pass with a
 *    wrong API key. [ping] stays as an unauthenticated reachability probe.
 *  • §14.4 — [forwardIncoming] encrypts to the browser CLIENT's public key and NEVER
 *    falls back to plaintext. If the client key is missing it reports failure so the
 *    caller can retry rather than leaking cleartext to the server.
 *  • Context-threaded config via [BridgePrefs] instead of a static ambient Prefs object.
 *
 * Nothing here has a hardcoded server, key or identity — all config is per-user runtime.
 */
object BridgeApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun server(ctx: Context): String {
        val raw = BridgePrefs.getServerUrl(ctx).trim().trimEnd('/')
        return when {
            raw.isEmpty()               -> raw
            raw.startsWith("http://")   -> raw
            raw.startsWith("https://")  -> raw
            else                        -> "https://$raw"
        }
    }

    private fun req(ctx: Context, path: String) = Request.Builder()
        .url("${server(ctx)}/api/tools/sms-bridge$path")
        .header("x-api-key", BridgePrefs.getApiKey(ctx))
        .header("x-device-id", BridgePrefs.getDeviceId(ctx))

    // ── Reachability (unauthenticated) ──────────────────────────────────────────

    /** GET {server}/health — proves the URL resolves and the server is up. Not an auth check. */
    fun ping(ctx: Context, callback: (Boolean) -> Unit) {
        val r = runCatching { Request.Builder().url("${server(ctx)}/health").build() }
            .getOrElse { callback(false); return }
        client.newCall(r).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(false)
            override fun onResponse(call: Call, response: Response) { response.close(); callback(response.isSuccessful) }
        })
    }

    /** §16.1 — authenticated check: GET /pending with the API key. 200 ⇒ key is valid. */
    fun verifyKey(ctx: Context, callback: (ok: Boolean, code: Int, reason: String) -> Unit) {
        if (BridgePrefs.getServerUrl(ctx).isBlank()) { callback(false, -1, "No server URL set"); return }
        if (BridgePrefs.getApiKey(ctx).isBlank())    { callback(false, -1, "No API key set"); return }
        val r = runCatching { req(ctx, "/pending").get().build() }
            .getOrElse { callback(false, -1, "Invalid server URL"); return }
        client.newCall(r).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) =
                callback(false, -1, e.message?.let { "Unreachable: $it" } ?: "Server unreachable")
            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                response.close()
                when {
                    response.isSuccessful      -> callback(true, code, "OK")
                    code == 401 || code == 403 -> callback(false, code, "API key rejected ($code)")
                    else                       -> callback(false, code, "Server error ($code)")
                }
            }
        })
    }

    // ── Pairing ─────────────────────────────────────────────────────────────────

    /**
     * Pairs the device with the server. Uploads the phone public key; expects the browser
     * client's public key back (`client_key`, with `server_key` accepted for back-compat).
     */
    fun linkDevice(ctx: Context, code: String, callback: (ok: Boolean, msg: String) -> Unit) {
        val devicePubKey = BridgeKeyManager.getOrCreatePublicKey(ctx)
        val body = JSONObject()
            .put("pairing_code", code)
            .put("device_id",    BridgePrefs.getDeviceId(ctx))
            .put("public_key",   devicePubKey)
            .toString().toRequestBody(JSON)

        val r = runCatching { req(ctx, "/link").post(body).build() }
            .getOrElse { callback(false, "Invalid server URL"); return }
        client.newCall(r).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(false, e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val text = response.body?.string() ?: ""
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (response.isSuccessful && json?.optBoolean("ok") == true) {
                    json.optString("api_key").takeIf { it.isNotEmpty() }?.let { BridgePrefs.setApiKey(ctx, it) }
                    // §14.4: peer key is the browser client's key. Accept legacy "server_key" field name.
                    val clientKey = json.optString("client_key").ifEmpty { json.optString("server_key") }
                    if (clientKey.isNotEmpty()) BridgeKeyManager.storeClientKey(ctx, clientKey)
                    callback(true, "Linked")
                } else {
                    callback(false, json?.optString("error").orEmpty().ifEmpty { "Failed (${response.code})" })
                }
            }
        })
    }

    // ── Outbound (server → phone) ───────────────────────────────────────────────

    fun getPending(ctx: Context, callback: (List<PendingMessage>) -> Unit) {
        val r = runCatching { req(ctx, "/pending").get().build() }.getOrElse { callback(emptyList()); return }
        client.newCall(r).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(emptyList())
            override fun onResponse(call: Call, response: Response) {
                val text = response.body?.string() ?: return callback(emptyList())
                val list = runCatching {
                    val msgs = JSONObject(text).getJSONArray("messages")
                    (0 until msgs.length()).mapNotNull { i ->
                        val obj       = msgs.getJSONObject(i)
                        val id        = obj.getInt("id")
                        val phone     = obj.getString("phone")
                        val rawMsg    = obj.getString("message")
                        val encrypted = obj.optInt("encrypted", 1) == 1
                        val plaintext = if (encrypted) {
                            val priv = BridgeKeyManager.getPrivateKey(ctx) ?: return@mapNotNull null
                            runCatching { BridgeCrypto.decrypt(rawMsg, priv) }.getOrNull()
                        } else rawMsg
                        plaintext?.let { PendingMessage(id, phone, it) }
                    }
                }.getOrElse { emptyList() }
                callback(list)
            }
        })
    }

    fun markSent(ctx: Context, id: Int): Boolean {
        val body = JSONObject().put("id", id).toString().toRequestBody(JSON)
        return runCatching {
            client.newCall(req(ctx, "/mark-sent").post(body).build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    fun markFailed(ctx: Context, id: Int) {
        val body = JSONObject().put("id", id).toString().toRequestBody(JSON)
        runCatching { client.newCall(req(ctx, "/mark-failed").post(body).build()).execute().close() }
    }

    // ── Inbound (phone → computer), §14.4 full-chain E2E ────────────────────────

    /**
     * Forwards an incoming SMS to the server as ciphertext addressed to the browser client.
     * Returns false (never sends plaintext) if the client key is missing — the caller may retry.
     */
    fun forwardIncoming(ctx: Context, from: String, message: String, callback: ((Boolean) -> Unit)? = null) {
        val clientKey = BridgeKeyManager.getClientKey(ctx)
        if (clientKey.isNullOrEmpty()) {
            // §14.4 — no plaintext fallback. Without the client key we cannot encrypt; do not leak.
            android.util.Log.w("NexLink_Bridge", "forwardIncoming: no client key — dropping (not sending plaintext)")
            callback?.invoke(false); return
        }
        val envelope = runCatching { BridgeCrypto.encrypt(message, clientKey) }.getOrNull()
        if (envelope == null) { callback?.invoke(false); return }

        val bodyObj = JSONObject()
            .put("from", from)
            .put("device_id", BridgePrefs.getDeviceId(ctx))
            .put("encrypted_message", JSONObject(envelope))
        val r = req(ctx, "/incoming").post(bodyObj.toString().toRequestBody(JSON)).build()
        client.newCall(r).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback?.invoke(false) }
            override fun onResponse(call: Call, response: Response) { val ok = response.isSuccessful; response.close(); callback?.invoke(ok) }
        })
    }

    data class PendingMessage(val id: Int, val phone: String, val message: String)
}
