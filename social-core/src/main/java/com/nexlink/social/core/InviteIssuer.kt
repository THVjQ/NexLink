package com.nexlink.social.core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One issued invite. [formatted] is what a human reads; [code] is what is typed. */
data class Invite(
    val code: String,
    val formatted: String,
    val expiresAt: Long,
    val issuedAt: Long = 0L,
    /** Someone has created an account with it. It can no longer be revoked. */
    val used: Boolean = false,
    val expired: Boolean = false,
) {
    /** Still worth giving to somebody. */
    val live: Boolean get() = !used && !expired
}

/**
 * §9.5 — a user inviting someone, rather than asking the operator to.
 *
 * Minting a registration token needs Synapse **admin** credentials, which no
 * phone may ever hold: an admin token reads every room's metadata and can
 * deactivate any account. So the client cannot mint anything. It asks the
 * server to, over an endpoint that authenticates the *user* and does the
 * privileged part out of reach — `infra/synapse-modules/nexlink_invites.py`.
 *
 * Deliberately the raw C-S API, like [DeviceManager] and [Registration]: the
 * endpoint is a Synapse module's, so the SDK has no notion of it.
 */
class InviteIssuer(
    private val homeserverUrl: String,
    private val accessToken: String,
) {
    /**
     * Trailing-slash trap, the same one [DeviceManager] documents: the
     * homeserver URL ends in "/", and "//_synapse/..." does not match the
     * tunnel's anchored route, so the request reaches **Element Web** and comes
     * back as HTML where JSON was expected.
     */
    private val base = homeserverUrl.trimEnd('/')

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    fun create(): Result<Invite> = runCatching {
        val req = Request.Builder()
            .url("$base$PATH")
            .header("Authorization", "Bearer $accessToken")
            .post("{}".toRequestBody(json))
            .build()
        http.newCall(req).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IllegalStateException(errorFrom(r.code, raw))
            val o = JSONObject(raw)
            Invite(
                code = o.getString("code"),
                formatted = o.optString("formatted").ifEmpty { o.getString("code") },
                expiresAt = o.optLong("expires_at"),
            )
        }
    }

    /**
     * §14.2.2 — say what happened.
     *
     * The 404 case is the one worth naming: it means the server is running
     * without the module, which is an operator problem the user can do nothing
     * about, and "not found" would send them looking for a mistake of their own.
     */
    private fun errorFrom(code: Int, body: String): String {
        val message = runCatching { JSONObject(body).optString("error") }
            .getOrNull()?.takeIf { it.isNotBlank() }
        return when {
            code == 404 -> "This server can't issue invites yet. Ask the operator."
            code == 401 || code == 403 -> "You're not signed in any more. Sign in and try again."
            code == 429 -> message ?: "Too many invites in a short time. Try again shortly."
            else -> message ?: "The server couldn't create an invite (error $code)."
        }
    }

    /** §9.5.1 — the codes this user issued. The server filters by issuer. */
    fun list(): Result<List<Invite>> = runCatching {
        val req = Request.Builder()
            .url("$base$PATH")
            .header("Authorization", "Bearer $accessToken")
            .get().build()
        http.newCall(req).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IllegalStateException(errorFrom(r.code, raw))
            val arr = JSONObject(raw).optJSONArray("invites") ?: return@use emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Invite(
                    code = o.getString("code"),
                    formatted = o.optString("formatted").ifEmpty { o.getString("code") },
                    expiresAt = o.optLong("expires_at"),
                    issuedAt = o.optLong("issued_at"),
                    used = o.optBoolean("used"),
                    expired = o.optBoolean("expired"),
                )
            }
        }
    }

    /**
     * Revoke an unredeemed code.
     *
     * A code already used cannot be revoked and the server says so with a 409 —
     * the account exists by then, and unmaking it is §31's business, not this
     * endpoint's.
     */
    fun revoke(code: String): Result<Unit> = runCatching {
        val req = Request.Builder()
            .url("$base$PATH?code=" + java.net.URLEncoder.encode(code, "UTF-8"))
            .header("Authorization", "Bearer $accessToken")
            .delete().build()
        http.newCall(req).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IllegalStateException(
                if (r.code == 409) "That code has already been used, so it can't be revoked."
                else errorFrom(r.code, raw)
            )
        }
    }

    companion object {
        /**
         * A sibling of Synapse's own `/_matrix/` trees. `/_synapse/...` is not
         * routed publicly (404 from a phone, fine on localhost), and
         * `/_matrix/client/unstable/...` — the tidier namespace — 404s even on
         * localhost because Synapse's own resource owns every child of
         * `/_matrix/client`. See the module for the full note.
         *
         * Must match PATH in infra/synapse-modules/nexlink_invites.py.
         */
        const val PATH = "/_matrix/nexlink/v1/invite"
    }
}
