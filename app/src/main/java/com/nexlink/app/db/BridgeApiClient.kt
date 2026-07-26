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
 *  • §14.4 — [forwardIncoming] encrypts to the browser CLIENTS' public keys — one
 *    envelope per PC on the account — and never falls back to plaintext. If no key is
 *    known the reply is queued and retried rather than dropped.
 *
 *    This is now true in both directions. It was not before: the server only ever
 *    returned its OWN key, so inbound was encrypted to the server and decrypted by it
 *    on the way out. A server that still answers only `server_key` puts this back into
 *    that state, which is why the fallback is marked where it happens.
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
     * Redeems a one-time pairing code minted on the PC (`POST /generate-code`) and registers this
     * phone. Uploads the phone public key; expects the browser client's public key back
     * (`client_key`, with `server_key` accepted for back-compat).
     *
     * [code] is the **pairing code**, not the API key — the two are different secrets and the
     * server only ever looks the former up in `pairing_codes`. The HTTP status is surfaced so the
     * caller can distinguish an expired code (403) from a bad key (401).
     */
    fun linkDevice(ctx: Context, code: String, callback: (ok: Boolean, httpCode: Int, msg: String) -> Unit) {
        val devicePubKey = BridgeKeyManager.getOrCreatePublicKey(ctx)
        val body = JSONObject()
            .put("pairing_code", code)
            .put("device_id",    BridgePrefs.getDeviceId(ctx))
            .put("public_key",   devicePubKey)
            .toString().toRequestBody(JSON)

        val r = runCatching { req(ctx, "/link").post(body).build() }
            .getOrElse { callback(false, -1, "Invalid server URL"); return }
        client.newCall(r).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(false, -1, e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val text = response.body?.string() ?: ""
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (response.isSuccessful && json?.optBoolean("ok") == true) {
                    json.optString("api_key").takeIf { it.isNotEmpty() }?.let { BridgePrefs.setApiKey(ctx, it) }
                    storePeerKeys(ctx, json)
                    callback(true, response.code, "Linked")
                } else {
                    callback(false, response.code, json?.optString("error").orEmpty().ifEmpty { "Failed (${response.code})" })
                }
            }
        })
    }

    /**
     * Records the desktop keys a reply must be encrypted to.
     *
     * `client_keys` is the current form — one entry per PC on the account, since an account can
     * hold several. `client_key` and `server_key` are the older single-key fields; `server_key` is
     * the server's OWN key, so falling back to it means the server can read the reply. Accepted
     * only so an updated phone still works against a server that has not been upgraded yet.
     */
    private fun storePeerKeys(ctx: Context, json: JSONObject) {
        val arr = json.optJSONArray("client_keys")
        if (arr != null && arr.length() > 0) {
            val keys = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val pub = o.optString("public_key")
                if (pub.isEmpty()) null else (o.optString("key_id").ifEmpty { BridgeCrypto.keyId(pub) } to pub)
            }
            if (keys.isNotEmpty()) { BridgeKeyManager.storeClientKeys(ctx, keys); return }
        }
        val single = json.optString("client_key").ifEmpty { json.optString("server_key") }
        if (single.isNotEmpty()) BridgeKeyManager.storeClientKey(ctx, single)
    }

    /**
     * Refreshes the desktop keys from the server. A PC that registers after the phone paired is
     * invisible until this runs, and a reply that arrives in the meantime has nowhere to go — the
     * polling service calls this before flushing the queue for exactly that reason.
     */
    fun refreshClientKeys(ctx: Context, callback: ((Boolean) -> Unit)? = null) {
        val r = runCatching { req(ctx, "/client-keys").get().build() }
            .getOrElse { callback?.invoke(false); return }
        client.newCall(r).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback?.invoke(false) }
            override fun onResponse(call: Call, response: Response) {
                val text = response.body?.string().orEmpty()
                response.close()
                if (!response.isSuccessful) { callback?.invoke(false); return }
                val keys = runCatching {
                    val arr = JSONObject(text).getJSONArray("keys")
                    (0 until arr.length()).mapNotNull { i ->
                        val o = arr.getJSONObject(i)
                        val pub = o.optString("public_key")
                        if (pub.isEmpty()) null else (o.optString("key_id").ifEmpty { BridgeCrypto.keyId(pub) } to pub)
                    }
                }.getOrNull()
                if (keys == null) { callback?.invoke(false); return }
                BridgeKeyManager.storeClientKeys(ctx, keys)
                callback?.invoke(keys.isNotEmpty())
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
     * Forwards an incoming SMS as ciphertext addressed to each PC on the account — one envelope per
     * desktop key, so the server relays something it holds no key for.
     *
     * There is still no plaintext fallback, but a reply is no longer thrown away when the keys are
     * missing: it is queued and retried. Previously a reply that arrived before any PC had
     * registered was lost permanently, which is a worse outcome than a delay.
     */
    fun forwardIncoming(ctx: Context, from: String, message: String, callback: ((Boolean) -> Unit)? = null) {
        val keys = BridgeKeyManager.getClientKeys(ctx)
        if (keys.isEmpty()) {
            android.util.Log.w("NexLink_Bridge", "forwardIncoming: no desktop key yet — queuing for retry")
            BridgePrefs.queueIncoming(ctx, from, message)
            // Ask the server who the PCs are; the polling service flushes the queue once we know.
            refreshClientKeys(ctx)
            callback?.invoke(false); return
        }

        val envelopes = JSONObject()
        for ((keyId, pub) in keys) {
            val envelope = runCatching { BridgeCrypto.encrypt(message, pub) }.getOrNull()
            if (envelope != null) envelopes.put(keyId, JSONObject(envelope))
            else android.util.Log.w("NexLink_Bridge", "forwardIncoming: could not encrypt to key $keyId")
        }
        if (envelopes.length() == 0) {
            BridgePrefs.queueIncoming(ctx, from, message)
            callback?.invoke(false); return
        }

        val bodyObj = JSONObject()
            .put("from", from)
            .put("device_id", BridgePrefs.getDeviceId(ctx))
            .put("envelopes", envelopes)
        val r = req(ctx, "/incoming").post(bodyObj.toString().toRequestBody(JSON)).build()
        client.newCall(r).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // The network, not the crypto — hold onto it and try again on the next poll.
                BridgePrefs.queueIncoming(ctx, from, message)
                callback?.invoke(false)
            }
            override fun onResponse(call: Call, response: Response) {
                val ok = response.isSuccessful
                response.close()
                if (!ok) BridgePrefs.queueIncoming(ctx, from, message)
                callback?.invoke(ok)
            }
        })
    }

    /**
     * Retries replies that could not be forwarded earlier. Called from the polling service, so a
     * queued reply goes out as soon as a PC registers its key or the network returns.
     */
    fun flushIncomingQueue(ctx: Context) {
        val queued = BridgePrefs.takeQueuedIncoming(ctx)
        if (queued.isEmpty()) return
        if (BridgeKeyManager.getClientKeys(ctx).isEmpty()) {
            refreshClientKeys(ctx) { gotKeys ->
                // Put them back either way; forwardIncoming re-queues anything that still fails.
                queued.forEach { (from, msg) -> if (gotKeys) forwardIncoming(ctx, from, msg) else BridgePrefs.queueIncoming(ctx, from, msg) }
            }
            return
        }
        queued.forEach { (from, msg) -> forwardIncoming(ctx, from, msg) }
    }

    data class PendingMessage(val id: Int, val phone: String, val message: String)
}
