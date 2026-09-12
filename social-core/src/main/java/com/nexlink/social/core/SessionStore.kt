package com.nexlink.social.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SlidingSyncVersion

/**
 * Persists the Matrix session so the user signs in once — §12.
 *
 * §12.2: the SDK owns the crypto and state stores; this holds only the small
 * amount the application needs to hand back to `Client.restoreSession`.
 *
 * §12.4: it is an **access token**, so it is treated as key material and stored
 * in `EncryptedSharedPreferences` behind a Keystore-backed master key. A stolen
 * phone with a locked screen does not yield a usable session (§3.3, A2).
 *
 * §2.8 invariant 2 — *no private key material leaves the device unencrypted* —
 * applies here: nothing in this class is ever logged, backed up, or sent
 * anywhere. `android:allowBackup="false"` in the manifest is the other half.
 */
class SessionStore(context: Context) {

    private val prefs by lazy {
        val key = masterKey(context)
        EncryptedSharedPreferences.create(
            context,
            "social_session",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * §12.4.3 — "StrongBox where the device provides it, falling back to TEE."
     *
     * StrongBox is a separate security chip; a key held there survives attacks
     * that defeat the TEE. Most devices do not have one, and on those
     * `build()` throws — including, on some vendors, with exceptions other than
     * the documented `StrongBoxUnavailableException`. So the fallback catches
     * broadly on purpose: a device without StrongBox must still get a working
     * Keystore-backed key, not a crash on first launch.
     *
     * Deliberately **not** `setUserAuthenticationRequired(true)`. §12.4.4 is
     * explicit about why: a background sync woken by a push cannot authenticate
     * the user, so requiring it stops message delivery whenever the phone is
     * locked — which is most of the time. The honest trade is a working
     * messenger protected by platform encryption, and [deviceLockWarning] is
     * the other half of that bargain.
     */
    private fun masterKey(context: Context): MasterKey {
        val strongBox = runCatching {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(true)
                .build()
        }
        return strongBox.getOrElse {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }
    }

    fun save(session: Session) {
        prefs.edit()
            .putString(K_ACCESS, session.accessToken)
            .putString(K_REFRESH, session.refreshToken)
            .putString(K_USER, session.userId)
            .putString(K_DEVICE, session.deviceId)
            .putString(K_HS, session.homeserverUrl)
            .putString(K_OIDC, session.oauthData)
            .apply()
    }

    fun load(): Session? {
        val access = prefs.getString(K_ACCESS, null) ?: return null
        val user = prefs.getString(K_USER, null) ?: return null
        val device = prefs.getString(K_DEVICE, null) ?: return null
        val hs = prefs.getString(K_HS, null) ?: return null
        return Session(
            accessToken = access,
            refreshToken = prefs.getString(K_REFRESH, null),
            userId = user,
            deviceId = device,
            homeserverUrl = hs,
            oauthData = prefs.getString(K_OIDC, null),
            slidingSyncVersion = SlidingSyncVersion.NATIVE
        )
    }

    val hasSession: Boolean get() = prefs.contains(K_ACCESS)

    /**
     * §12.4 — the key that encrypts the SDK's own SQLite store.
     *
     * The SDK owns the crypto and state stores (§12.2), so the application
     * cannot substitute its own — but it can insist the SDK encrypts them.
     * `SqliteStoreBuilder.key()` takes the key; this generates it once and keeps
     * it in `EncryptedSharedPreferences`, behind the Keystore.
     *
     * §12.4.1's threat is the one this addresses: an attacker with the device
     * and time, but not the screen lock. Without this the crypto store leans
     * entirely on full-disk encryption and the app sandbox.
     *
     * **Losing this key means losing the store**, which means re-login and
     * re-verification. It is deleted only by [clear], alongside the store
     * itself.
     */
    fun storeKey(): ByteArray {
        prefs.getString(K_STORE_KEY, null)?.let {
            return android.util.Base64.decode(it, android.util.Base64.NO_WRAP)
        }
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(K_STORE_KEY, android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP))
            .apply()
        return key
    }

    /**
     * §7.5.2 — the credentials an archive must carry, as JSON bytes.
     *
     * **This is the part the first build of the archive got wrong.** It packed
     * `filesDir` and `cacheDir` and nothing else, which looked complete: the
     * SQLite stores were all there. But the store key and the access token live
     * in `EncryptedSharedPreferences`, which sits in `shared_prefs/` — a
     * *sibling* of `filesDir`, not inside it. So the archive held stores that
     * **nothing could open**, and was useless for the one job §7.5.2 gives it.
     *
     * Copying `shared_prefs/social_session.xml` in would not have fixed it
     * either: that file is sealed by a Keystore master key which by design never
     * leaves the device. On a new phone it is undecryptable ciphertext.
     *
     * So the custody model differs by destination, and has to:
     *
     * | Where | Protected by |
     * |---|---|
     * | On this device | Keystore, hardware-backed where available (§12.4.3) |
     * | In an archive | the user's passphrase (§7.5.2) |
     *
     * The whole archive is AES-256-GCM under a key derived from that
     * passphrase, so these bytes are protected by it and by nothing else.
     * **That is the trade §7.5.2 asks for** — an archive that can be opened on a
     * phone that has never seen this Keystore — and it is why the passphrase
     * rules in [com.nexlink.social.core.transfer.Passphrase] are not
     * decoration: this blob is a full account takeover to anyone who reads it.
     */
    fun exportBundle(): ByteArray {
        val o = org.json.JSONObject()
        o.put("v", 1)
        o.put("storeKey", android.util.Base64.encodeToString(storeKey(), android.util.Base64.NO_WRAP))
        load()?.let { se ->
            o.put("accessToken", se.accessToken)
            o.put("refreshToken", se.refreshToken)
            o.put("userId", se.userId)
            o.put("deviceId", se.deviceId)
            o.put("homeserverUrl", se.homeserverUrl)
            o.put("oauthData", se.oauthData)
        }
        return o.toString().toByteArray()
    }

    /**
     * §7.5.2 — put an archive's credentials back.
     *
     * The store key is written **first and unconditionally**: without it the
     * restored SQLite files are unreadable, and a restore that placed the
     * stores but not the key would produce the silent half-broken account this
     * whole path is written to avoid.
     */
    fun importBundle(bytes: ByteArray) {
        val o = org.json.JSONObject(String(bytes))
        val e = prefs.edit()
        e.putString(K_STORE_KEY, o.getString("storeKey"))
        // org.json returns the literal string "null" from optString for a JSON
        // null, so every optional field is read through isNull. This bit us
        // once already, in the invite service.
        fun opt(k: String): String? = if (o.isNull(k)) null else o.optString(k, null)
        opt("accessToken")?.let { e.putString(K_ACCESS, it) }
        e.putString(K_REFRESH, opt("refreshToken"))
        opt("userId")?.let { e.putString(K_USER, it) }
        opt("deviceId")?.let { e.putString(K_DEVICE, it) }
        opt("homeserverUrl")?.let { e.putString(K_HS, it) }
        e.putString(K_OIDC, opt("oauthData"))
        // commit, not apply: the caller restarts the process immediately after
        // a restore, and apply() is asynchronous.
        e.commit()
    }

    /**
     * §7.6 / §32.3 — sign-out must leave nothing behind. The SDK's own store is
     * deleted separately by whoever owns the session paths; this clears the
     * credentials that would let anything re-attach to the account.
     */
    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val K_ACCESS = "access_token"
        const val K_REFRESH = "refresh_token"
        const val K_USER = "user_id"
        const val K_DEVICE = "device_id"
        const val K_HS = "homeserver_url"
        const val K_OIDC = "oidc_data"
        const val K_STORE_KEY = "store_key"
    }
}
