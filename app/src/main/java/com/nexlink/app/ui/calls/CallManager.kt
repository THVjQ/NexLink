package com.nexlink.app.ui.calls

import android.telecom.Call
import androidx.lifecycle.MutableLiveData

object CallManager {

    val currentCall  = MutableLiveData<Call?>(null)
    val callState    = MutableLiveData(Call.STATE_NEW)
    val callerName   = MutableLiveData("")
    val callerNumber = MutableLiveData("")

    /** Wall-clock ms when the call first became ACTIVE. 0 while not yet active. */
    var callStartTimeMs = 0L
        private set

    private val stateCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_ACTIVE && callStartTimeMs == 0L) {
                callStartTimeMs = System.currentTimeMillis()
            }
            callState.postValue(state)
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                callStartTimeMs = 0L
                currentCall.postValue(null)
            }
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) {
            callerNumber.postValue(details.handle?.schemeSpecificPart ?: "")
        }
    }

    fun addCall(call: Call) {
        currentCall.postValue(call)
        call.registerCallback(stateCallback)
        callState.postValue(call.state)
        val handle = call.details?.handle?.schemeSpecificPart ?: ""
        callerNumber.postValue(handle)
    }

    fun removeCall(call: Call) {
        call.unregisterCallback(stateCallback)
        if (currentCall.value == call) {
            currentCall.postValue(null)
            callState.postValue(Call.STATE_DISCONNECTED)
        }
    }

    fun answer()  { currentCall.value?.answer(0) }
    fun reject()  { currentCall.value?.reject(false, null) }
    fun hangUp()  { currentCall.value?.disconnect() }
    fun hold()    {
        val c = currentCall.value ?: return
        if (c.state == Call.STATE_ACTIVE) c.hold() else c.unhold()
    }
}
