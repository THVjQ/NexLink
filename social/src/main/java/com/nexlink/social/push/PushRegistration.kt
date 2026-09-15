package com.nexlink.social.push

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.nexlink.social.SessionProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * §13.3 — registering this device with the homeserver's push routing.
 *
 * Kept apart from [SocialPushService] so the FCM dependency sits in one place
 * that a UnifiedPush implementation could sit beside (§13.3.3: "abstracted
 * enough that UnifiedPush is an addition rather than a rewrite").
 */
object PushRegistration {

    private const val TAG = "SocialPush"

    /**
     * The app id Sygnal is configured with, and it must match **exactly**.
     *
     * Derived from the package so debug and release route to their own Firebase
     * clients rather than fighting over one. A mismatch is silent: Synapse
     * posts the notification, Sygnal answers that it does not know the app id,
     * and the device is simply never notified.
     */
    fun appId(context: Context): String = context.packageName + ".android"

    /**
     * Where Synapse posts notifications.
     *
     * The LAN address, not the public hostname: Synapse and Sygnal are both on
     * Willard, so the push never leaves the box. Sending it out through
     * Cloudflare and back would add a public round trip and put notification
     * metadata on the wire for no reason.
     */
    const val GATEWAY_URL = "http://192.168.0.10:8062/_matrix/push/v1/notify"

    suspend fun currentToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener {
                Log.w(TAG, "could not obtain an FCM token: ${it.javaClass.simpleName}")
                cont.resume(null)
            }
    }

    /**
     * Register, using the current token if one is not supplied.
     *
     * Safe to call repeatedly — `append = false` replaces the pusher for this
     * pushkey rather than accumulating duplicates, so calling it on every
     * sign-in and every token rotation is correct rather than wasteful.
     */
    /**
     * §13.3 — register this device's token with the homeserver.
     *
     * **Restores the session first, for the same reason `onMessageReceived`
     * does — and this one was missed.** `SocialPushService` already carries a
     * long note about a cold push arriving in a process where nothing has run
     * `restore()` yet, so `current()` is null even though the credentials are
     * in `EncryptedSharedPreferences`. `onNewToken` fires in exactly that
     * process, and this function began `current() ?: return false`.
     *
     * The consequence is not a missed notification, it is **permanent**:
     *
     * 1. FCM rotates the token and rejects a push sent to the old one.
     * 2. Synapse sees the gateway report the key is invalid and **retires the
     *    pusher** — correctly; that is what a 404 from FCM means.
     * 3. `onNewToken` fires, this returns false without a word, and nothing
     *    re-registers.
     * 4. The device now has **no pusher at all** and will never be notified
     *    again until someone opens the app.
     *
     * Measured on a handset: `peer195112` ended the evening with zero pushers
     * and an app that had logged *"FCM token rotated, re-registering"* four
     * minutes earlier. `sygnal_gcm_status_codes_total{code="404"}` is what made
     * it visible — the delivery metric from §13.3.5, earning its place on its
     * first day.
     */
    suspend fun register(context: Context, token: String? = null): Boolean {
        val manager = SessionProvider.manager(context)
        var session = manager.current()
        if (session == null && manager.hasStoredSession) {
            runCatching { manager.restore() }
            session = manager.current()
        }
        if (session == null) {
            // Genuinely signed out. Say so: silence here is what turned a token
            // rotation into a permanently unreachable device.
            Log.w(TAG, "cannot register a pusher: no session")
            return false
        }
        val t = token ?: currentToken() ?: return false
        val r = session.registerPush(t, appId(context), GATEWAY_URL)
        r.onFailure { Log.w(TAG, "pusher registration failed: ${it.message}") }
        // Length only — §27.5.1. The token routes to a specific device and is
        // not something to write into a log that may be shared in a bug report.
        if (r.isSuccess) Log.i(TAG, "pusher registered (token len=${t.length})")
        return r.isSuccess
    }

    /**
     * §32.3 — stop push before the session goes away.
     *
     * Must happen while the session still has an access token. Signing out
     * first leaves the pusher registered server-side, and the homeserver keeps
     * notifying a device that can no longer read anything.
     */
    suspend fun unregister(context: Context): Boolean {
        val session = SessionProvider.manager(context).current() ?: return false
        val t = currentToken() ?: return false
        return session.unregisterPush(t, appId(context)).isSuccess
    }
}
