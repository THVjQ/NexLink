package com.nexlink.social.rtc.telecom

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

/**
 * §19 — the self-managed `ConnectionService`.
 *
 * §19.2's decision in code: Social registers its calls with Telecom and draws
 * its own UI. `CAPABILITY_SELF_MANAGED` is the whole point — it says "I have
 * calls, tell me about conflicts, but do not put me in the dialer".
 *
 * §19.3: NexLink's dialer UI never renders a Social call. They are separate
 * call surfaces from separate apps that happen to share a signing key. Nothing
 * here asks for the dialer role, and §16.8 forbids NexLink gaining call-handling
 * code for the social product.
 */
class SocialConnectionService : ConnectionService() {

    override fun onCreateOutgoingConnection(
        from: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): android.telecom.Connection = connection(request).apply { setDialing() }

    override fun onCreateIncomingConnection(
        from: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): android.telecom.Connection = connection(request).apply { setRinging() }

    /**
     * Telecom refused the call.
     *
     * Reached when a self-managed call cannot be placed — most often because a
     * cellular call is already up and the platform will not allow a second
     * outgoing call. §19.3's "Social call arrives during a cellular call" row
     * is the incoming counterpart. Not an error to swallow: the UI has to say
     * the call did not go through, because the user pressed something and
     * otherwise nothing happens.
     */
    override fun onCreateOutgoingConnectionFailed(
        from: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Failures.report(roomIdOf(request), outgoing = true)
    }

    override fun onCreateIncomingConnectionFailed(
        from: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Failures.report(roomIdOf(request), outgoing = false)
    }

    private fun connection(request: ConnectionRequest?) =
        SocialConnection(roomIdOf(request)).also { active[it.roomId] = it }

    private fun roomIdOf(request: ConnectionRequest?): String =
        request?.extras?.getString(EXTRA_ROOM_ID).orEmpty()

    companion object {
        const val EXTRA_ROOM_ID = "com.nexlink.social.rtc.ROOM_ID"

        /**
         * Live connections by room.
         *
         * Telecom constructs the `Connection` itself, so there is no other way
         * for the caller that started the call to reach the object Telecom
         * handed back.
         */
        private val active = mutableMapOf<String, SocialConnection>()

        fun connectionFor(roomId: String): SocialConnection? = active[roomId]

        fun forget(roomId: String) { active.remove(roomId) }

        /**
         * §19.2.2 — the account Social registers for its own calls.
         *
         * `CAPABILITY_SELF_MANAGED` and nothing else. Adding
         * `CAPABILITY_CALL_PROVIDER` would put Social in the "default phone
         * app" chooser alongside NexLink, which §19.2 rules out: the user would
         * be asked to choose between their SMS app and their messaging app for
         * the role of "phone", and there is no version of that which is good.
         */
        fun phoneAccountHandle(context: Context) = PhoneAccountHandle(
            ComponentName(context, SocialConnectionService::class.java),
            "nexlink-social"
        )

        /**
         * Register with Telecom. Idempotent — re-registering replaces.
         *
         * Must happen before a call is placed, and is cheap, so it belongs at
         * application start rather than at call time: an unregistered account
         * makes `placeCall` throw, and diagnosing that from a call that simply
         * never starts is unpleasant.
         */
        fun register(context: Context) {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val handle = phoneAccountHandle(context)
            val account = PhoneAccount.builder(handle, "NexLink Social")
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                // A scheme is required. `sip:` rather than `tel:` because these
                // are not telephone calls and must never be offered as a way to
                // dial a phone number.
                .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
                .build()
            tm.registerPhoneAccount(account)
        }

        /** The URI Telecom wants. Opaque to it; it only has to be stable. */
        fun roomUri(roomId: String): Uri = Uri.fromParts(PhoneAccount.SCHEME_SIP, roomId, null)

        fun extras(roomId: String) = Bundle().apply { putString(EXTRA_ROOM_ID, roomId) }
    }

    /**
     * Where a refused call is reported.
     *
     * A plain callback rather than a flow: it fires at most once per attempt,
     * from a platform callback, and the consumer is whatever is showing the
     * call UI at that moment.
     */
    object Failures {
        @Volatile var listener: ((roomId: String, outgoing: Boolean) -> Unit)? = null
        fun report(roomId: String, outgoing: Boolean) { listener?.invoke(roomId, outgoing) }
    }
}
