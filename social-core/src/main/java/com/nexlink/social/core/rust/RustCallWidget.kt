package com.nexlink.social.core.rust

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientProperties
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.WidgetCapabilities
import org.matrix.rustcomponents.sdk.WidgetCapabilitiesProvider
import org.matrix.rustcomponents.sdk.WidgetDriverAndHandle
import org.matrix.rustcomponents.sdk.generateWebviewUrl
import org.matrix.rustcomponents.sdk.getElementCallRequiredPermissions
import org.matrix.rustcomponents.sdk.makeWidgetDriver
import org.matrix.rustcomponents.sdk.newVirtualElementCallWidget
import uniffi.matrix_sdk.EncryptionSystem
import uniffi.matrix_sdk.Intent
import uniffi.matrix_sdk.VirtualElementCallWidgetConfig
import uniffi.matrix_sdk.VirtualElementCallWidgetProperties

/**
 * §17.6.3 — the bridge that lets the Element Call widget act as this user.
 *
 * ## The problem this solves
 *
 * Loading the widget in a WebView is easy and produces a call nobody is signed
 * in to: Element Call comes up in standalone mode offering *"Join as guest"*,
 * and the WebView console shows `POST /_matrix/client/v3/register → 401` as it
 * tries to make itself a guest account. That was the state §33.5.2 recorded.
 *
 * An embedded Element Call does **not** get its own access token. It drives the
 * host client's Matrix session over the **widget API**, a `postMessage`
 * protocol: the widget asks, the host performs the operation and answers. So
 * the host has to speak that protocol.
 *
 * ## Why this is thirty lines rather than a month
 *
 * The SDK implements the protocol. `makeWidgetDriver` returns a driver that
 * speaks widget-API to a room, plus a handle with `recv()` and `send()`. All
 * this class does is pump strings between that handle and the WebView, which is
 * the same shape Element X Android uses.
 *
 * **That is §17.6's argument arriving as a concrete saving.** Option B would
 * have had to implement call membership and match Element Call's E2EE by hand;
 * Option A's integration cost turns out to be a message pump, because the
 * work is upstream's and the SDK exposes it.
 *
 * ## The capability negotiation is a security decision
 *
 * The widget asks for permissions and [capabilities] decides. This grants
 * exactly `getElementCallRequiredPermissions` — what Element Call needs for a
 * call in this room and nothing wider. It is not a rubber stamp: a widget is
 * remote code, and the reason this deployment self-hosts it (§17.6.3) is the
 * same reason the grant is narrow.
 */
class RustCallWidget internal constructor(
    private val room: Room,
    private val driverAndHandle: WidgetDriverAndHandle,
    private val ownUserId: String,
    private val deviceId: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pump: Job? = null

    /**
     * Start the driver and begin pumping messages.
     *
     * @param toWidget called with every message the host wants delivered to the
     *   page. The caller evaluates it as `postMessage` inside the WebView.
     */
    fun start(toWidget: (String) -> Unit) {
        // The driver runs for the life of the call and only returns when the
        // widget is done, so it gets its own coroutine rather than blocking.
        scope.launch {
            runCatching {
                driverAndHandle.driver.run(room, object : WidgetCapabilitiesProvider {
                    override fun acquireCapabilities(
                        capabilities: WidgetCapabilities
                    ): WidgetCapabilities = capabilities(capabilities)
                })
            }
        }
        pump = scope.launch {
            while (true) {
                val msg = runCatching { driverAndHandle.handle.recv() }.getOrNull() ?: break
                withContext(Dispatchers.Main) { toWidget(msg) }
            }
        }
    }

    /** A message the page posted to the host. */
    suspend fun fromWidget(message: String): Boolean =
        runCatching { driverAndHandle.handle.send(message) }.getOrDefault(false)

    /**
     * §17.6.3 — grant only what a call in this room needs.
     *
     * The widget's request is ignored in favour of the SDK's own list for
     * Element Call. If a future widget build asks for more, it does not get it
     * by asking; the grant changes only when this line does.
     */
    private fun capabilities(requested: WidgetCapabilities): WidgetCapabilities =
        getElementCallRequiredPermissions(ownUserId, deviceId)

    fun close() {
        pump?.cancel()
        scope.cancel()
        runCatching { driverAndHandle.driver.destroy() }
        runCatching { driverAndHandle.handle.destroy() }
    }
}
