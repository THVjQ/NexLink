package com.nexlink.app.ui.calls

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.nexlink.app.databinding.ActivityDialerBinding

class DialerActivity : AppCompatActivity() {

    private lateinit var b: ActivityDialerBinding
    private var number = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDialerBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Pre-fill number from tel: URI if launched that way
        val uri = intent.data
        if (uri?.scheme == "tel") {
            number = uri.schemeSpecificPart ?: ""
            updateDisplay()
        }

        bindKeys()

        b.btnClose.setOnClickListener { finish() }
        b.btnCall.setOnClickListener  { placeCall() }
        b.btnDel.setOnClickListener   {
            if (number.isNotEmpty()) {
                number = number.dropLast(1)
                updateDisplay()
            }
        }
    }

    private fun bindKeys() {
        val keys = mapOf(
            b.k0 to "0", b.k1 to "1", b.k2 to "2", b.k3 to "3",
            b.k4 to "4", b.k5 to "5", b.k6 to "6", b.k7 to "7",
            b.k8 to "8", b.k9 to "9", b.kStar to "*", b.kHash to "#"
        )
        keys.forEach { (view, digit) ->
            view.setOnClickListener { number += digit; updateDisplay() }
        }
    }

    private fun updateDisplay() {
        b.tvNumber.text = formatNumber(number)
    }

    private fun formatNumber(n: String) = when {
        n.length <= 4  -> n
        n.length <= 7  -> "${n.take(4)} ${n.drop(4)}"
        else           -> "${n.take(4)} ${n.drop(4).take(3)} ${n.drop(7)}"
    }

    private fun placeCall() {
        if (number.isEmpty()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) return
        val tm = getSystemService(TelecomManager::class.java)
        val uri = Uri.fromParts("tel", number, null)
        try {
            tm.placeCall(uri, null)
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_CALL, uri))
        }
    }
}
