package com.nexlink.social.core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Account creation — §9.6, §22.10.
 *
 * The Matrix SDK has no registration API, so this speaks the raw Client-Server
 * API directly. That is the same gap §11.7.3 found for device management.
 *
 * **The ordering here is a §2.8 invariant, not a preference.** §22.10 established
 * that Synapse's User-Interactive Auth consumes the invite token in a step that
 * completes *before* the account exists:
 *
 * ```
 * 1. start      -> 401 + session, flows demand m.login.registration_token
 * 2. redeem     -> 401, token now pending=1, NO ACCOUNT YET
 *      <-- the acceptance gate runs HERE (§9.6)
 * 3. complete   -> 200, account created
 * ```
 *
 * A client that ran the gate first and redeemed afterwards would create accounts
 * for people who abandoned the gate, breaking §2.8's third invariant: *no
 * account is created without a redeemed invite token AND a completed acceptance
 * record.*
 */
class Registration(private val homeserverUrl: String) {

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

    /** Handle for a registration that has redeemed its token but has no account. */
    data class Pending(val session: String, val username: String, val password: String)

    sealed interface Failure {
        data object InviteInvalid : Failure
        data object InviteUsed : Failure
        data object UsernameTaken : Failure
        data object RateLimited : Failure
        data class Other(val message: String) : Failure
    }

    /** Cheap pre-check. Does NOT consume the token (§22.10). */
    fun checkInvite(token: String): Result<Boolean> = runCatching {
        val url = "$base/_matrix/client/v1/register/m.login.registration_token/validity" +
            "?token=$token"
        http.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
            JSONObject(r.body?.string() ?: "{}").optBoolean("valid", false)
        }
    }

    /** §6.3 — is this username free? Fails fast before the gate, not after. */
    fun isUsernameAvailable(username: String): Result<Boolean> = runCatching {
        val url = "$base/_matrix/client/v3/register/available?username=$username"
        http.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
            if (r.code == 200) JSONObject(r.body?.string() ?: "{}").optBoolean("available", false)
            else false
        }
    }

    /**
     * Steps 1 and 2 — redeem the invite token. **No account exists on return.**
     * The caller shows the acceptance gate next, then calls [complete].
     */
    fun redeemInvite(username: String, password: String, token: String): Result<Pending> =
        runCatching {
            val start = post(
                "/_matrix/client/v3/register",
                JSONObject().put("username", username).put("password", password)
            )
            val session = start.optString("session").ifEmpty {
                throw IllegalStateException(describe(start))
            }
            val redeemed = post(
                "/_matrix/client/v3/register",
                JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .put("auth", JSONObject()
                        .put("type", "m.login.registration_token")
                        .put("token", token)
                        .put("session", session))
            )
            val completed = redeemed.optJSONArray("completed")
            val ok = (0 until (completed?.length() ?: 0))
                .any { completed!!.getString(it) == "m.login.registration_token" }
            if (!ok) throw IllegalStateException(describe(redeemed))
            Pending(session, username, password)
        }

    /** Step 3 — only after the gate completed. Returns the new MXID. */
    fun complete(pending: Pending): Result<String> = runCatching {
        val done = post(
            "/_matrix/client/v3/register",
            JSONObject()
                .put("username", pending.username)
                .put("password", pending.password)
                .put("auth", JSONObject().put("type", "m.login.dummy").put("session", pending.session))
        )
        done.optString("user_id").ifEmpty { throw IllegalStateException(describe(done)) }
    }

    private fun post(path: String, body: JSONObject): JSONObject =
        http.newCall(
            Request.Builder().url(base + path)
                .post(body.toString().toRequestBody(json)).build()
        ).execute().use { r -> JSONObject(r.body?.string() ?: "{}") }

    /** Turn a Matrix errcode into something a person can act on (§14.2.2). */
    private fun describe(o: JSONObject): String = when (o.optString("errcode")) {
        "M_USER_IN_USE" -> "That username is taken."
        "M_INVALID_USERNAME" -> "That username isn't allowed."
        "M_LIMIT_EXCEEDED" -> "Too many attempts. Wait a few minutes and try again."
        "M_FORBIDDEN" -> "That invite code isn't valid."
        else -> (o.takeUnless { it.isNull("error") }?.optString("error"))?.ifEmpty { null }
            ?: "Registration failed."
    }
}
