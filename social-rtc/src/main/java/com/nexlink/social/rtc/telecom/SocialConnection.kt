package com.nexlink.social.rtc.telecom

import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * §19 — one Social call, as Telecom sees it.
 *
 * §19.2 decided this is **self-managed**: Social tells Telecom its calls exist
 * and draws its own UI. It is never an `InCallService` — that is the dialer's
 * job and NexLink already holds it, and two apps from one developer both
 * claiming the dialer role is D1 (§2.2) in its sharpest form.
 *
 * ## What this class is actually for
 *
 * It looks like bookkeeping and it is not. §19.4 is the case that must work:
 *
 * > *"An app that did not register with Telecom would simply keep its
 * > microphone open while the user took a phone call, which is the failure
 * > everyone has experienced from some app or other."*
 *
 * Telecom can only call [onHold] on a connection it knows about. Everything
 * else here exists so that call arrives and is answered correctly.
 *
 * ## Held is not left
 *
 * §19.4 is explicit, and it is the easiest thing here to get wrong: a held
 * participant **stays in the MatrixRTC room**. Their membership state (§17.7) is
 * untouched; only the media is suspended. Leaving and re-joining would be
 * visible to everyone and would rotate the E2EE key twice (§17.5) for nothing.
 *
 * So [onHold] emits [TelecomEvent.Hold] and nothing in this class touches
 * membership. Whatever owns the media suspends it; whatever owns the room never
 * hears about this at all.
 */
class SocialConnection(
    /** The room this call belongs to, so the owner can route events. */
    val roomId: String
) : Connection() {

    private val _events = MutableStateFlow<TelecomEvent>(TelecomEvent.None)

    /** What Telecom has asked of this call. */
    val events: StateFlow<TelecomEvent> = _events.asStateFlow()

    init {
        // A self-managed call must declare that it can be held, or the platform
        // will not hold it when a cellular call arrives — which silently
        // removes §19.4's entire mechanism while looking like nothing is wrong.
        connectionCapabilities = CAPABILITY_HOLD or CAPABILITY_SUPPORT_HOLD or CAPABILITY_MUTE
        audioModeIsVoip = true
    }

    override fun onAnswer() {
        _events.value = TelecomEvent.Answer
        setActive()
    }

    override fun onReject() {
        _events.value = TelecomEvent.Reject
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    override fun onDisconnect() {
        _events.value = TelecomEvent.Disconnect
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    /**
     * §19.4 — a cellular call was answered.
     *
     * `setOnHold()` is called immediately, before the media is actually
     * suspended. Telecom treats a connection that does not confirm hold as
     * misbehaving, and by this point the user is already talking to someone
     * else: the right order is to tell the platform yes, then suspend.
     */
    override fun onHold() {
        _events.value = TelecomEvent.Hold
        setOnHold()
    }

    /**
     * §19.4.1 — the cellular call ended.
     *
     * **`setActive()` is deliberately NOT called here.** Resuming is not free:
     * the microphone has to be re-acquired and that can fail if something else
     * took it. §19.4.1 requires that failure to be surfaced — *"Microphone
     * unavailable"*, with a retry — and never to leave the user in a call
     * silently transmitting nothing.
     *
     * Calling `setActive()` here would assert the opposite: that audio is
     * flowing again. The owner calls [resumeSucceeded] or [resumeFailed] once
     * it knows which happened.
     */
    override fun onUnhold() {
        _events.value = TelecomEvent.Unhold
    }

    /** §19.4.1 — the microphone came back. Now the call really is active. */
    fun resumeSucceeded() = setActive()

    /**
     * §19.4.1 — the microphone did not come back.
     *
     * The call stays held rather than being torn down: the user can retry, and
     * the other participants have not lost them (§19.4 — held is not left). The
     * UI is responsible for saying so.
     */
    fun resumeFailed() {
        setOnHold()
        _events.value = TelecomEvent.ResumeFailed
    }

    override fun onCallAudioStateChanged(state: CallAudioState) {
        _events.value = TelecomEvent.AudioRouteChanged(state.route, state.isMuted)
    }
}

/** What Telecom asked for. Consumed by whatever owns the media and the UI. */
sealed interface TelecomEvent {
    data object None : TelecomEvent
    data object Answer : TelecomEvent
    data object Reject : TelecomEvent
    data object Disconnect : TelecomEvent
    /** §19.4 — suspend media. Do NOT touch membership state. */
    data object Hold : TelecomEvent
    /** §19.4.1 — try to re-acquire the microphone, then report back. */
    data object Unhold : TelecomEvent
    /** §19.4.1 — re-acquisition failed; the UI must say so and offer a retry. */
    data object ResumeFailed : TelecomEvent
    data class AudioRouteChanged(val route: Int, val muted: Boolean) : TelecomEvent
}
