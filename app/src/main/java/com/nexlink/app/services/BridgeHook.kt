package com.nexlink.app.services

import android.content.Context
import com.nexlink.app.db.BridgeApiClient
import com.nexlink.app.db.BridgePrefs

/**
 * Single, guarded seam between core NexLink and the optional bridge (§6 / §20.8).
 *
 * `receivers/SmsReceiver` calls [onIncomingSms] after it has persisted/displayed the message
 * exactly as before. Everything bridge-related is gated behind [BridgePrefs.isEnabled] here, so
 * with the feature off this is a couple of boolean reads and returns — and keeping the bridge
 * behind this one hook means it could later be excluded from a build as a config change, not a
 * rewrite.
 */
object BridgeHook {

    /** Forward an inbound SMS to the user's server, only when the bridge is fully set up. */
    fun onIncomingSms(ctx: Context, sender: String, body: String) {
        if (!BridgePrefs.isEnabled(ctx)) return
        if (!BridgePrefs.isLinked(ctx)) return
        if (!BridgePrefs.isForwardIncoming(ctx)) return
        BridgeApiClient.forwardIncoming(ctx.applicationContext, sender, body)
    }
}
