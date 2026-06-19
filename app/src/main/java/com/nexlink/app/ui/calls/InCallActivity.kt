package com.nexlink.app.ui.calls

import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.nexlink.app.databinding.ActivityInCallBinding
import com.nexlink.app.db.SmsHelper

class InCallActivity : AppCompatActivity() {

    private lateinit var b: ActivityInCallBinding
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            // Compute elapsed time from the wall-clock start recorded in CallManager.
            // This stays correct even if the activity is recreated (user locks screen, etc.)
            val elapsed = if (CallManager.callStartTimeMs > 0)
                (System.currentTimeMillis() - CallManager.callStartTimeMs) / 1000L
            else 0L
            val m = elapsed / 60
            val s = elapsed % 60
            b.tvTimer.text = "%02d:%02d".format(m, s)
            timerHandler.postDelayed(this, 500)
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
                    b.layoutRinging.visibility = View.VISIBLE
                    b.layoutActive.visibility  = View.GONE
                    timerHandler.removeCallbacks(timerRunnable)
                }
                Call.STATE_ACTIVE, Call.STATE_CONNECTING, Call.STATE_DIALING -> {
                    b.tvStatus.text = if (state == Call.STATE_ACTIVE) "On call" else "Calling…"
                    b.layoutRinging.visibility = View.GONE
                    b.layoutActive.visibility  = View.VISIBLE
                    if (state == Call.STATE_ACTIVE && !timerHandler.hasCallbacks(timerRunnable)) {
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
        fun haptic(v: View) = v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        b.btnAnswer.setOnClickListener  { haptic(it); CallManager.answer() }
        b.btnDecline.setOnClickListener { haptic(it); CallManager.reject() }
        b.btnEndCall.setOnClickListener { haptic(it); CallManager.hangUp() }

        b.btnMute.setOnClickListener {
            haptic(it)
            isMuted = !isMuted
            val am = getSystemService(AudioManager::class.java)
            am.isMicrophoneMute = isMuted
            b.btnMute.alpha = if (isMuted) 1f else 0.5f
            b.tvMute.text   = if (isMuted) "Unmute" else "Mute"
        }

        b.btnSpeaker.setOnClickListener {
            haptic(it)
            isSpeaker = !isSpeaker
            val am = getSystemService(AudioManager::class.java)
            am.mode             = AudioManager.MODE_IN_CALL
            am.isSpeakerphoneOn = isSpeaker
            b.btnSpeaker.alpha  = if (isSpeaker) 1f else 0.5f
            b.tvSpeaker.text    = if (isSpeaker) "Earpiece" else "Speaker"
        }

        b.btnHold.setOnClickListener { haptic(it); CallManager.hold() }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
    }
}
