package com.nexlink.app.ui.bridge

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.nexlink.app.R
import com.nexlink.app.crypto.BridgeKeyManager
import com.nexlink.app.db.BridgeApiClient
import com.nexlink.app.db.BridgePrefs
import com.nexlink.app.services.BridgePollingService
import com.nexlink.app.services.BridgeWatchdogWorker
import com.nexlink.app.ui.applySystemBarInsetsPadding

/**
 * Entry point for the Computer Bridge from Settings (§5a).
 *
 * If the bridge is already on → status screen with Test connection, poll-interval and
 * forward/notify toggles, and a full Disable / Forget-server teardown (§8). Otherwise it forwards
 * straight into the disclaimer + wizard.
 */
class BridgeSettingsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private val dp by lazy { resources.displayMetrics.density }
    private fun Int.px() = (this * dp).toInt()
    private fun col(id: Int) = ContextCompat.getColor(this, id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Computer Bridge"

        if (!(BridgePrefs.isEnabled(this) && BridgePrefs.isLinked(this))) {
            startActivity(Intent(this, BridgeSetupActivity::class.java))
            finish(); return
        }

        val scroll = ScrollView(this).apply { setBackgroundColor(col(R.color.bg)); isFillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.px(), 20.px(), 20.px(), 28.px())
        }
        scroll.addView(root); setContentView(scroll)
        root.applySystemBarInsetsPadding()
        render()
    }

    private fun render() {
        root.removeAllViews()

        addTitle("Computer Bridge")
        root.addView(TextView(this).apply {
            text = "● Connected — ${BridgePrefs.serverHost(this@BridgeSettingsActivity)}"
            textSize = 14f; setTextColor(0xFF2E7D32.toInt()); setPadding(0, 0, 0, 16.px())
        })

        // Test connection
        val testResult = TextView(this).apply { textSize = 13f; setPadding(2.px(), 6.px(), 0, 10.px()) }
        root.addView(primaryButton("Test connection") {
            testResult.setTextColor(col(R.color.muted)); testResult.text = "Testing…"
            BridgeApiClient.verifyKey(this) { ok, code, reason ->
                runOnUiThread {
                    testResult.setTextColor(if (ok) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
                    testResult.text = if (ok) "✅ Connected and key accepted." else "❌ $reason"
                }
            }
        })
        root.addView(testResult)

        // Poll interval
        addSectionLabel("Poll interval")
        val intervalLabel = TextView(this).apply {
            textSize = 13f; setTextColor(col(R.color.text2))
            text = "${BridgePrefs.getPollIntervalSeconds(this@BridgeSettingsActivity)} seconds"
        }
        root.addView(intervalLabel)
        root.addView(SeekBar(this).apply {
            max = 118  // 2..120
            progress = BridgePrefs.getPollIntervalSeconds(this@BridgeSettingsActivity) - 2
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    intervalLabel.text = "${p + 2} seconds"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    BridgePrefs.setPollIntervalSeconds(this@BridgeSettingsActivity, (sb?.progress ?: 3) + 2)
                    // Restart so the new interval takes effect immediately
                    BridgePollingService.start(this@BridgeSettingsActivity)
                }
            })
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = 12.px() }
        })

        // Toggles
        addToggle("Forward incoming SMS to computer", BridgePrefs.isForwardIncoming(this)) {
            BridgePrefs.setForwardIncoming(this, it)
        }
        addToggle("Notify me of incoming while bridged", BridgePrefs.isNotifyIncoming(this)) {
            BridgePrefs.setNotifyIncoming(this, it)
        }

        // MMS limitation note (§16.5)
        addSectionLabel("Note")
        root.addView(TextView(this).apply {
            text = "Picture messages (MMS) are not carried over the bridge in this version — they still arrive on the phone."
            textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, 0, 0, 16.px())
        })

        // Re-pair — the recovery path for a bad or stale pairing (§4.3). Without this the only way
        // out of a broken link is clearing app data.
        addSectionLabel("Pairing")
        root.addView(TextView(this).apply {
            text = "If the PC says this phone has no key, or you moved to a new server, unlink and " +
                   "pair again with a fresh code."
            textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, 0, 0, 6.px())
        })
        root.addView(secondaryButton("Unlink & re-pair") { confirmRepair() })

        // Disable
        root.addView(destructiveButton("Disable Computer Bridge") { confirmDisable() })
    }

    private fun confirmRepair() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Unlink & re-pair?")
            .setMessage("The bridge stops until you finish pairing again. Your server URL and API key are " +
                "kept — you'll only need a new pairing code from the PC. Generate it there first " +
                "(chat button → Pair Device → Generate); codes expire after 15 minutes.")
            .setPositiveButton("Unlink & re-pair") { _, _ -> repair() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Stands the bridge down and returns to the wizard's connect step. The stored client key goes
     * too: it belongs to the pairing being discarded, and keeping it would let inbound messages be
     * encrypted to a peer that is no longer linked.
     */
    private fun repair() {
        BridgePollingService.stop(this)
        BridgeWatchdogWorker.cancel(this)
        BridgePrefs.setEnabled(this, false)
        BridgePrefs.setLinked(this, false)
        BridgeKeyManager.clearClientKey(this)
        startActivity(Intent(this, BridgeSetupActivity::class.java)
            .putExtra(BridgeSetupActivity.EXTRA_START_AT_CONNECT, true))
        finish()
    }

    private fun confirmDisable() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Disable Computer Bridge?")
            .setMessage("This stops relaying your texts and shuts the bridge down. Your NexLink messages are untouched. " +
                "You can also forget the stored server URL and API key so a different server can be set up cleanly.")
            .setPositiveButton("Disable") { _, _ -> teardown(forgetServer = false) }
            .setNeutralButton("Disable & forget server") { _, _ -> teardown(forgetServer = true) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Full stand-down per §8 — leaves the app behaving as stock NexLink. */
    private fun teardown(forgetServer: Boolean) {
        BridgePollingService.stop(this)
        BridgeWatchdogWorker.cancel(this)
        BridgePrefs.clearAll(this, forgetServer)
        if (forgetServer) BridgeKeyManager.clearKeys(this)
        Toast.makeText(this, "Computer Bridge disabled", Toast.LENGTH_SHORT).show()
        finish()
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    private fun addTitle(t: String) = root.addView(TextView(this).apply {
        text = t; textSize = 22f; setTypeface(null, Typeface.BOLD)
        setTextColor(col(R.color.text)); setPadding(0, 0, 0, 6.px())
    })

    private fun addSectionLabel(t: String) = root.addView(TextView(this).apply {
        text = t.uppercase(); textSize = 11f; setTypeface(null, Typeface.BOLD); letterSpacing = 0.09f
        setTextColor(col(R.color.muted)); setPadding(0, 14.px(), 0, 6.px())
    })

    private fun addToggle(label: String, initial: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 8.px(), 0, 8.px())
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 14f; setTextColor(col(R.color.text))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(MaterialSwitch(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, c -> onChange(c) }
        })
        root.addView(row)
    }

    private fun primaryButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        backgroundTintList = ContextCompat.getColorStateList(this@BridgeSettingsActivity, R.color.accent)
        setTextColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setOnClickListener { onClick() }
    }

    private fun secondaryButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f
        backgroundTintList = ContextCompat.getColorStateList(this@BridgeSettingsActivity, R.color.surface2)
        setTextColor(col(R.color.text))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = 8.px() }
        setOnClickListener { onClick() }
    }

    private fun destructiveButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        backgroundTintList = ContextCompat.getColorStateList(this@BridgeSettingsActivity, R.color.surface2)
        setTextColor(0xFFC62828.toInt())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = 8.px() }
        setOnClickListener { onClick() }
    }
}
