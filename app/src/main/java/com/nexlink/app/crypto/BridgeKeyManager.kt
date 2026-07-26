package com.nexlink.app.crypto

// ─────────────────────────────────────────────────────────────────────────────
//  BridgeKeyManager.kt  —  Computer Bridge transport keypair storage
//
//  Holds the phone's long-term P-256 transport keypair plus the browser CLIENT's
//  public key (received at link time). Distinct from any NexLink key storage and
//  from db/CryptoStore — this is bridge-only.
//
//  §14.4 change vs. the reference: inbound (phone → computer) is now end-to-end
//  encrypted to the browser CLIENT's public key, so the peer key stored here is
//  the client key, not a server key. The server holds NO message-content keypair.
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

object BridgeKeyManager {

    private const val PREF_FILE  = "e2e_keys"           // kept for continuity with the reference
    private const val KEY_PRIV   = "device_private_key"
    private const val KEY_PUB    = "device_public_key"
    private const val KEY_CLIENT = "client_public_key"  // peer (browser client) key — was "server_public_key"

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
     * Forget the peer key only, keeping this phone's own keypair — used by Unlink & re-pair (§4.3).
     * The client key belongs to the pairing being discarded; the phone's identity does not.
     */
    fun clearClientKey(ctx: Context) {
        getPrefs(ctx).edit().remove(KEY_CLIENT).apply()
    }

    /** Wipe all bridge keys — called on Forget-server (§8). */
    fun clearKeys(ctx: Context) {
        getPrefs(ctx).edit().clear().apply()
        prefs = null
    }
}
