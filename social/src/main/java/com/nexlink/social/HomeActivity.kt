package com.nexlink.social

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nexlink.social.core.session.RoomSummary
import com.nexlink.social.ui.onboarding.AcceptanceGateActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.nexlink.social.ui.R as UiR

/**
 * Phase 0's demonstrable thing (§33.1, principle 3).
 *
 * It does two jobs, both of which are acceptance criteria:
 *  - launches the acceptance gate (§9.6) and reports what came back, showing
 *    that the gate runs end to end and that **the date of birth does not come
 *    back with it** (§9.6.2);
 *  - renders a conversation list from [SessionProvider], proving `:social-ui`
 *    and `:social` are built entirely against §11.6's seam with no SDK present.
 *
 * It is replaced by the real chat surface (§14) in phase 3.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private var gateResult: CharSequence? = null

    private val gate = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        gateResult = if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            getString(
                R.string.home_gate_done,
                data?.getStringExtra(AcceptanceGateActivity.EXTRA_INVITE_CODE) ?: "?",
                data?.getStringExtra(AcceptanceGateActivity.EXTRA_TERMS_VERSION) ?: "?"
            )
        } else {
            getString(R.string.home_gate_cancelled)
        }
        render(lastRooms)
    }

    private var lastRooms: List<RoomSummary> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        scroll.addView(root)
        setContentView(scroll)

        lifecycleScope.launch {
            SessionProvider.session().rooms().collectLatest { rooms ->
                lastRooms = rooms
                render(rooms)
            }
        }
    }

    private fun render(rooms: List<RoomSummary>) {
        root.removeAllViews()

        root.addView(text(getString(R.string.home_title), 26f, bold = true))
        root.addView(text(getString(R.string.home_phase0), 13f, colour = UiR.color.social_muted))
        root.addView(spacer(12))
        root.addView(text(getString(R.string.home_phase0_body), 15f, colour = UiR.color.social_text2))
        root.addView(spacer(8))

        root.addView(Button(this).apply {
            text = getString(R.string.home_run_gate)
            isAllCaps = false
            setOnClickListener { gate.launch(AcceptanceGateActivity.intent(this@HomeActivity)) }
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        })

        gateResult?.let {
            root.addView(text(it, 14f, colour = UiR.color.social_accent))
        }

        root.addView(spacer(16))
        root.addView(divider())
        root.addView(spacer(8))

        rooms.forEach { room -> root.addView(roomRow(room)) }
    }

    private fun roomRow(room: RoomSummary): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(10), 0, dp(10))

        addView(LinearLayout(this@HomeActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(text(room.title, 16f, bold = true).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            if (room.unreadCount > 0) {
                addView(text(room.unreadCount.toString(), 14f, colour = UiR.color.social_accent))
            }
        })

        // §14.2.3 — a room whose latest event failed to decrypt still lists,
        // with a placeholder that says what happened. Never an empty row.
        val preview = when {
            room.lastMessageUndecryptable -> "Can't decrypt — this message was sent before this device"
            room.lastMessagePreview != null -> room.lastMessagePreview!!
            else -> "No messages yet"
        }
        addView(text(preview, 14f, colour =
            if (room.lastMessageUndecryptable) UiR.color.social_muted else UiR.color.social_text2))
    }

    // ── tiny view helpers; the real UI lives in :social-ui ───────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(
        value: CharSequence,
        sizeSp: Float,
        bold: Boolean = false,
        colour: Int = UiR.color.social_text
    ) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setTextColor(androidx.core.content.ContextCompat.getColor(this@HomeActivity, colour))
        gravity = Gravity.START
    }

    private fun spacer(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(h))
    }

    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 1)
        setBackgroundColor(androidx.core.content.ContextCompat
            .getColor(this@HomeActivity, UiR.color.social_divider))
    }

    private companion object {
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
