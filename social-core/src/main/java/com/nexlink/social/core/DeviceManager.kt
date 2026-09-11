package com.nexlink.social.core

import com.nexlink.social.core.session.DeviceId
import com.nexlink.social.core.session.DeviceInfo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The device-management surface — §8.6.
 *
 * **The SDK provides none of this** (§11.7.3). What looks like a device API in
 * the bindings — `AccountManagementAction.DevicesList` — is a deep link into the
 * homeserver's own web UI, not data a client can render. So this speaks the raw
 * Client-Server API, the same way [Registration] does.
 *
 * §8.3.2 is why it matters beyond convenience: a malicious or compromised
 * server's cheapest attack is to **insert a device into a user's account** and
 * hope nobody looks. Cross-signing makes that device unverified; this screen is
 * how a human ever notices.
 */
class DeviceManager(
    private val homeserverUrl: String,
    private val accessToken: String
) {
    /**
     * The homeserver URL arrives with a trailing slash — `public_baseurl` has
     * one and `.well-known` echoes it. Appending "/_matrix/..." to that gives
     * "//_matrix/...", which the tunnel's anchored `^/_matrix/` route correctly
     * does NOT match, so the request falls through to the catch-all and reaches
     * **Element Web instead of Synapse**. The symptom is an HTML page where JSON
     * was expected. See infra/runbooks/cloudflare-tunnel-routes.md.
     */
    private val base = homeserverUrl.trimEnd('/')

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    private fun req(path: String) = Request.Builder()
        .url(base + path)
        .header("Authorization", "Bearer $accessToken")

    /** §8.6 — every device on the account, newest activity first. */
    fun list(currentDeviceId: String): Result<List<DeviceInfo>> = runCatching {
        http.newCall(req("/_matrix/client/v3/devices").get().build()).execute().use { r ->
            val body = JSONObject(r.body?.string() ?: "{}")
            val arr: JSONArray = body.optJSONArray("devices") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val d = arr.getJSONObject(i)
                val id = d.getString("device_id")
                DeviceInfo(
                    id = DeviceId(id),
                    // org.json's optString() returns the LITERAL STRING "null"
                    // for a JSON null, so ifEmpty{} never catches it and the UI
                    // renders the word "null" as a device name. isNull() is the
                    // only reliable check.
                    displayName = d.takeUnless { it.isNull("display_name") }
                        ?.optString("display_name")?.ifEmpty { null },
                    isCurrent = id == currentDeviceId,
                    // §8.6 — verification state comes from the SDK's crypto
                    // store, not from here. The server's opinion of whether a
                    // device is trusted is exactly what must not be believed
                    // (§8.3.2), so this deliberately reports false and the
                    // caller merges in the real answer.
                    isVerified = false,
                    lastSeenAt = d.optLong("last_seen_ts").takeIf { it > 0 },
                    lastSeenIp = d.takeUnless { it.isNull("last_seen_ip") }
                        ?.optString("last_seen_ip")?.ifEmpty { null }
                )
            }.sortedByDescending { it.lastSeenAt ?: 0 }
        }
    }

    /** Rename a device so the list is legible — §8.6. */
    fun rename(deviceId: DeviceId, displayName: String): Result<Unit> = runCatching {
        val body = JSONObject().put("display_name", displayName)
        http.newCall(
            req("/_matrix/client/v3/devices/${deviceId.value}")
                .put(body.toString().toRequestBody(json)).build()
        ).execute().use { r -> if (!r.isSuccessful) error("rename failed (${r.code})") }
    }

    /**
     * Remove a device — §8.6, and §30.4.1's response to a lost or stolen phone.
     *
     * **Requires User-Interactive Auth**, the same staged flow §22.10 found for
     * registration. That is the platform's rule: removing a device invalidates
     * its access token, so the password prompt is a control, not a courtesy.
     *
     * §30.4.1 is explicit about the limit of this: messages the device already
     * received stay readable on it. Removal stops future access, not past.
     */
    fun delete(deviceId: DeviceId, username: String, password: String): Result<Unit> = runCatching {
        val path = "/_matrix/client/v3/delete_devices"
        val devices = JSONArray().put(deviceId.value)

        // First call establishes the UIA session and is expected to 401.
        val first = http.newCall(
            req(path).post(JSONObject().put("devices", devices).toString().toRequestBody(json))
                .build()
        ).execute().use { JSONObject(it.body?.string() ?: "{}") }

        if (first.has("session")) {
            val auth = JSONObject()
                .put("type", "m.login.password")
                .put("session", first.getString("session"))
                .put("identifier", JSONObject().put("type", "m.id.user").put("user", username))
                .put("password", password)
            val second = http.newCall(
                req(path).post(
                    JSONObject().put("devices", devices).put("auth", auth)
                        .toString().toRequestBody(json)
                ).build()
            ).execute()
            second.use {
                if (!it.isSuccessful) {
                    val e = JSONObject(it.body?.string() ?: "{}")
                    error(when (e.optString("errcode")) {
                        "M_FORBIDDEN" -> "That password is not right."
                        "M_LIMIT_EXCEEDED" -> "Too many attempts. Wait a few minutes."
                        else -> e.optString("error").ifEmpty { "Could not remove the device." }
                    })
                }
            }
        }
    }
}
