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
    suspend fun register(context: Context, token: String? = null): Boolean {
        val session = SessionProvider.manager(context).current() ?: return false
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
