package com.nexlink.app.services

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.nexlink.app.ui.calls.CallManager
import com.nexlink.app.ui.calls.InCallActivity

class NexLinkInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        CallManager.addCall(call)
        startActivity(
            Intent(this, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
            }
        )
    }

    override fun onCallRemoved(call: Call) {
        CallManager.removeCall(call)
    }
}
