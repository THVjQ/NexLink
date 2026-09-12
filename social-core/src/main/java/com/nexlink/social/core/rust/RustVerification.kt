package com.nexlink.social.core.rust

import com.nexlink.social.core.session.VerificationFlow
import com.nexlink.social.core.session.VerificationStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.SessionVerificationController
import org.matrix.rustcomponents.sdk.SessionVerificationControllerDelegate
import org.matrix.rustcomponents.sdk.SessionVerificationData
import org.matrix.rustcomponents.sdk.SessionVerificationRequestDetails

/**
 * §8.4 — verifying one of your own devices against another.
 *
 * ## Why this is not `verifyDevice(deviceId)`
 *
 * The seam originally declared `verifyDevice(deviceId: DeviceId)`, which reads
 * naturally — pick a device from the list in §8.6 and verify *that one*. The
 * SDK does not work that way and neither does the protocol underneath it:
 * `requestDeviceVerification()` takes **no argument**. It broadcasts a request
 * from this session to the user's other sessions, and whichever one a person is
 * sitting in front of answers it.
 *
 * That is the right shape, because verification is a **mutual** act performed by
 * one human holding two devices. "Verify that device from here" would imply
 * this device can vouch unilaterally, which is exactly the property
 * cross-signing exists to deny (§8.3.2).
 *
 * So the seam now exposes [RustSocialSession.startVerification] with no target,
 * and the §8.6 device list explains rather than offers a per-row button.
 *
 * ## Both directions, one controller
 *
 * The SDK has a single controller per client and one delegate slot. The same
 * object therefore serves the device that *asks* and the device that *answers*:
 *
 * | | New device | Existing device |
 * |---|---|---|
 * | starts | [request] | [accept] after [incoming] fires |
 * | then | `didAcceptVerificationRequest` → [startSas] | `didStartSasVerification` |
 * | both | `didReceiveVerificationData` → [VerificationStep.ShowEmoji] | same |
 * | user | [confirmMatch] | [confirmMatch] |
 *
 * **Both sides must approve.** A single-sided confirm leaves the other device
 * waiting and the verification incomplete — which is the point, and is why the
 * UI must not report success until `didFinish`.
 */
class RustVerification(
    private val inner: SessionVerificationController
) : VerificationFlow {

    private val _steps = MutableStateFlow<VerificationStep>(VerificationStep.Requested)
    override val steps: Flow<VerificationStep> = _steps.asStateFlow()

    private val _incoming = MutableStateFlow<IncomingRequest?>(null)

    /** §8.3.2 — an unexpected request is a signal, so it is surfaced, not auto-accepted. */
    val incoming: Flow<IncomingRequest?> = _incoming.asStateFlow()

    data class IncomingRequest(
        val deviceId: String,
        val deviceDisplayName: String?,
        val flowId: String,
        val senderUserId: String,
        val firstSeenAt: Long
    )

    /**
     * The delegate's callbacks are plain (non-suspend) and arrive on an FFI
     * thread, but answering them means making suspend FFI calls. This scope
     * carries those, on IO for §11.7.5's reason.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Which side of the handshake this device is on — see the note in the delegate. */
    @Volatile private var isRequester = false

    init {
        inner.setDelegate(object : SessionVerificationControllerDelegate {
            override fun didReceiveVerificationRequest(details: SessionVerificationRequestDetails) {
                _incoming.value = IncomingRequest(
                    deviceId = details.deviceId,
                    deviceDisplayName = details.deviceDisplayName,
                    flowId = details.flowId,
                    senderUserId = details.senderProfile.userId,
                    firstSeenAt = details.firstSeenTimestamp.toLong()
                )
            }

            /**
             * Fires on the device that **asked**, once the other side agrees.
             *
             * SAS does not begin on its own: someone has to call
             * `startSasVerification`, and the protocol expects that to be the
             * requester. Without this the flow stalls here — both devices sit
             * on "waiting" forever, with no error anywhere, which is exactly
             * how it behaved the first time it was run end to end.
             */
            override fun didAcceptVerificationRequest() {
                _steps.value = VerificationStep.Requested
                // ONLY the requester starts SAS.
                //
                // This callback fires on both devices, so starting here
                // unconditionally makes both send m.key.verification.start.
                // Measured on the wire: `.start` appeared twice per attempt,
                // and the handshake then stalled — both sides sat on
                // "connecting" with no error and no emoji, because each was
                // waiting for the other to answer the start it had sent.
                //
                // The protocol gives the requester that job, so [isRequester]
                // decides. Nothing in the SDK's API says which side you are;
                // it is knowable only from which call you made.
                if (isRequester) scope.launch { runCatching { startSas() } }
            }

            override fun didStartSasVerification() {
                _steps.value = VerificationStep.Requested
            }

            override fun didReceiveVerificationData(data: SessionVerificationData) {
                _steps.value = when (data) {
                    is SessionVerificationData.Emojis ->
                        // §14.4.3's rule applies to these too: the symbol is an
                        // opaque grapheme and is never indexed into or
                        // truncated. The description is the SDK's own English
                        // word for it, which is what the two people read aloud.
                        VerificationStep.ShowEmoji(data.emojis.map { it.symbol() to it.description() })
                    is SessionVerificationData.Decimals ->
                        // The protocol's fallback when either side cannot do
                        // emoji. Rendered as digits rather than pretending it
                        // is something else.
                        VerificationStep.ShowEmoji(data.values.map { it.toString() to "" })
                }
            }

            override fun didFail() { _steps.value = VerificationStep.Cancelled("verification failed") }
            override fun didCancel() { _steps.value = VerificationStep.Cancelled("cancelled") }
            override fun didFinish() { _steps.value = VerificationStep.Verified }
        })
    }

    /**
     * New device: ask this user's other devices to verify me.
     *
     * Any previous flow is cancelled first. The crypto machine treats a second
     * request from the same user as a collision and **cancels both**, so a
     * retry after a failed attempt would otherwise kill the very request it is
     * retrying. Cancelling explicitly makes the retry mean what it says.
     */
    suspend fun request() = io {
        runCatching { inner.cancelVerification() }
        _incoming.value = null
        isRequester = true
        inner.requestDeviceVerification()
    }

    /**
     * Existing device: take the request that [incoming] reported.
     *
     * Acknowledge **then** accept — the SDK requires the acknowledgement to bind
     * the flow id before the accept means anything.
     */
    suspend fun accept() = io {
        isRequester = false
        val r = _incoming.value ?: error("no incoming verification request")
        inner.acknowledgeVerificationRequest(r.senderUserId, r.flowId)
        inner.acceptVerificationRequest()
        // Clear it, or the UI keeps rendering the "do you accept?" prompt over
        // a flow that has already moved on.
        _incoming.value = null
    }

    suspend fun startSas() = io { inner.startSasVerification() }

    override suspend fun confirmMatch() = io { inner.approveVerification() }

    override suspend fun declineMatch() = io { inner.declineVerification() }

    override suspend fun cancel() = io {
        inner.cancelVerification()
        _incoming.value = null
    }

    /** §11.7.5 — every FFI call blocks the caller even when declared `suspend`. */
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
