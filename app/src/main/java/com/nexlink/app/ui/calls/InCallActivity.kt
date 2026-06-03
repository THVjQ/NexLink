package com.nexlink.app.ui.calls

import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.nexlink.app.databinding.ActivityInCallBinding
import com.nexlink.app.db.SmsHelper

class InCallActivity : AppCompatActivity() {

    private lateinit var b: ActivityInCallBinding
    private var callSeconds = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            callSeconds++
            val m = callSeconds / 60
            val s = callSeconds % 60
            b.tvTimer.text = "%02d:%02d".format(m, s)
            timerHandler.postDelayed(this, 1000)
        }
    }
    private var isMuted   = false
    private var isSpeaker = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        b = ActivityInCallBinding.inflate(layoutInflater)
        setContentView(b.root)

        observeCallState()
        bindButtons()
    }

    private fun observeCallState() {
        CallManager.callState.observe(this) { state ->
            when (state) {
                Call.STATE_RINGING -> {
                    b.tvStatus.text = "Incoming call"
                    b.tvTimer.text  = ""
                    b.layoutRinging.visibility = android.view.View.VISIBLE
                    b.layoutActive.visibility  = android.view.View.GONE
                    timerHandler.removeCallbacks(timerRunnable)
                }
                Call.STATE_ACTIVE, Call.STATE_CONNECTING, Call.STATE_DIALING -> {
                    b.tvStatus.text = if (state == Call.STATE_ACTIVE) "On call" else "Calling…"
                    b.layoutRinging.visibility = android.view.View.GONE
                    b.layoutActive.visibility  = android.view.View.VISIBLE
                    if (state == Call.STATE_ACTIVE && callSeconds == 0) {
                        timerHandler.post(timerRunnable)
                    }
                }
                Call.STATE_HOLDING -> {
                    b.tvStatus.text = "On hold"
                }
                Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                    timerHandler.removeCallbacks(timerRunnable)
                    finish()
                }
            }
        }

        CallManager.callerNumber.observe(this) { number ->
            if (number.isNullOrBlank()) return@observe
            val name = SmsHelper.getContactName(this, number)
            b.tvCallerName.text   = if (name != number) name else number
            b.tvCallerNumber.text = if (name != number) number else ""
            val initials = name.split(" ").take(2)
                .joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
            b.tvAvatar.text = initials
        }
    }

    private fun bindButtons() {
        // Ringing actions
        b.btnAnswer.setOnClickListener { CallManager.answer() }
        b.btnDecline.setOnClickListener { CallManager.reject() }

        // Active call actions
        b.btnEndCall.setOnClickListener { CallManager.hangUp() }

        b.btnMute.setOnClickListener {
            isMuted = !isMuted
            val am = getSystemService(AudioManager::class.java)
            am.isMicrophoneMute = isMuted
            b.btnMute.alpha = if (isMuted) 1f else 0.5f
            b.tvMute.text   = if (isMuted) "Unmute" else "Mute"
        }

        b.btnSpeaker.setOnClickListener {
            isSpeaker = !isSpeaker
            val am = getSystemService(AudioManager::class.java)
            am.mode            = AudioManager.MODE_IN_CALL
            am.isSpeakerphoneOn = isSpeaker
            b.btnSpeaker.alpha = if (isSpeaker) 1f else 0.5f
            b.tvSpeaker.text   = if (isSpeaker) "Earpiece" else "Speaker"
        }

        b.btnHold.setOnClickListener {
            CallManager.hold()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
    }
}
