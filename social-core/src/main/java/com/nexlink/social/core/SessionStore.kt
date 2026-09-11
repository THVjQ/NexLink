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
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "social_session",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
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
