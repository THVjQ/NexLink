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
    }
}
