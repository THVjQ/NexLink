package com.nexlink.app.ui.calls

import android.telecom.Call
import androidx.lifecycle.MutableLiveData

object CallManager {

    val currentCall  = MutableLiveData<Call?>(null)
    val callState    = MutableLiveData(Call.STATE_NEW)
    val callerName   = MutableLiveData("")
    val callerNumber = MutableLiveData("")

    private val stateCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            callState.postValue(state)
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
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
