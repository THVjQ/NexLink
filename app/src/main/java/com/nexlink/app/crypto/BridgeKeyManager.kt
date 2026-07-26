package com.nexlink.app.crypto

// ─────────────────────────────────────────────────────────────────────────────
//  BridgeKeyManager.kt  —  Computer Bridge transport keypair storage
//
//  Holds the phone's long-term P-256 transport keypair plus the browser CLIENT's
//  public key (received at link time). Distinct from any NexLink key storage and
//  from db/CryptoStore — this is bridge-only.
//
//  §14.4 change vs. the reference: inbound (phone → computer) is end-to-end
//  encrypted to the browser CLIENTS' public keys, so the peer keys stored here are
//  desktop keys, not server keys — the server relays ciphertext it cannot open.
//
//  An account can hold several PCs, each with its own keypair in its own browser
//  profile, so [getClientKeys] returns a set and a reply is encrypted once per key.
//  The single-key slot is kept in step for the fingerprint display and for pairing
//  against a server old enough to return only one key.
//
//  §16.4 note — why EncryptedSharedPreferences and not the Android Keystore:
//  a hardware-backed Keystore EC key would be non-exportable, but Keystore ECDH
//  key agreement (KeyProperties.PURPOSE_AGREE_KEY) is only available on API 31+,
//  and NexLink's minSdk is 26. A dual Keystore/SharedPrefs path would double the
//  crypto surface and risks silent cross-version decryption drift, so the private
//  key is kept in EncryptedSharedPreferences (Keystore-wrapped AES) for now.
//  Revisit once minSdk ≥ 31. Only the phone's own private key is sensitive here;
//  the client public key is not secret.
// ─────────────────────────────────────────────────────────────────────────────

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

object BridgeKeyManager {

    private const val PREF_FILE   = "e2e_keys"           // kept for continuity with the reference
    private const val KEY_PRIV    = "device_private_key"
    private const val KEY_PUB     = "device_public_key"
    private const val KEY_CLIENT  = "client_public_key"  // peer (browser client) key — was "server_public_key"
    private const val KEY_CLIENTS = "client_public_keys" // §Stage 3: JSON [{key_id, public_key}] — one per PC

    @Volatile private var prefs: SharedPreferences? = null

    private fun getPrefs(ctx: Context): SharedPreferences =
        prefs ?: synchronized(this) { prefs ?: build(ctx.applicationContext).also { prefs = it } }

    private fun build(ctx: Context): SharedPreferences {
        val master = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx, PREF_FILE, master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Phone public key (base64 DER SPKI); generates the keypair on first call. */
    fun getOrCreatePublicKey(ctx: Context): String {
        val p = getPrefs(ctx)
        if (!p.contains(KEY_PUB)) {
            val kp = BridgeCrypto.generateKeyPair()
            p.edit()
                .putString(KEY_PUB,  BridgeCrypto.exportPublicKey(kp.public))
                .putString(KEY_PRIV, BridgeCrypto.exportPrivateKey(kp.private))
                .apply()
        }
        return p.getString(KEY_PUB, "")!!
    }

    fun getPrivateKey(ctx: Context): String? = getPrefs(ctx).getString(KEY_PRIV, null)

    fun hasKeys(ctx: Context): Boolean = getPrefs(ctx).contains(KEY_PUB)

    /** Store the browser client's public key (received during /link). */
    fun storeClientKey(ctx: Context, clientPublicKeyB64: String) {
        getPrefs(ctx).edit().putString(KEY_CLIENT, clientPublicKeyB64).apply()
    }

    /** The browser client's public key, or null if not yet linked. */
    fun getClientKey(ctx: Context): String? = getPrefs(ctx).getString(KEY_CLIENT, null)

    /**
     * All desktop keys on the account, as (key_id, public_key) pairs. An account can hold several
     * PCs — each generates its own keypair in its own browser profile — so an incoming reply is
     * encrypted once per key and each PC opens the envelope filed under its own id.
     */
    fun storeClientKeys(ctx: Context, keys: List<Pair<String, String>>) {
        val arr = org.json.JSONArray()
        keys.forEach { (id, pub) -> arr.put(JSONObject().put("key_id", id).put("public_key", pub)) }
        getPrefs(ctx).edit().putString(KEY_CLIENTS, arr.toString()).apply()
        // Keep the single-key slot in step so the fingerprint display keeps working when there is
        // exactly one PC, which is the common case.
        if (keys.size == 1) storeClientKey(ctx, keys[0].second) else if (keys.isEmpty()) clearClientKey(ctx)
    }

    fun getClientKeys(ctx: Context): List<Pair<String, String>> {
        val raw = getPrefs(ctx).getString(KEY_CLIENTS, null)
        if (!raw.isNullOrEmpty()) {
            runCatching {
                val arr = org.json.JSONArray(raw)
                return (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    o.getString("key_id") to o.getString("public_key")
                }
            }
        }
        // Fall back to the single key from an older pairing, so an app updated before its server
        // still has somewhere to send replies.
        val single = getClientKey(ctx) ?: return emptyList()
        return listOf(BridgeCrypto.keyId(single) to single)
    }

    /**
     * Forget the peer key only, keeping this phone's own keypair — used by Unlink & re-pair (§4.3).
     * The client key belongs to the pairing being discarded; the phone's identity does not.
     */
    fun clearClientKey(ctx: Context) {
        getPrefs(ctx).edit().remove(KEY_CLIENT).remove(KEY_CLIENTS).apply()
    }

    /** Wipe all bridge keys — called on Forget-server (§8). */
    fun clearKeys(ctx: Context) {
        getPrefs(ctx).edit().clear().apply()
        prefs = null
    }
}
