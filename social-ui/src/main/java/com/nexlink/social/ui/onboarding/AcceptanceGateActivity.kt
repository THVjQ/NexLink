package com.nexlink.social.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.nexlink.social.core.acceptance.PolicyVersions
import com.nexlink.social.core.invite.InviteCode
import com.nexlink.social.core.invite.InviteCodeError
import com.nexlink.social.ui.R
import com.nexlink.social.ui.common.Ui

/**
 * The acceptance gate — docs/social/09-invites.md §9.6.
 *
 * The screen between redeeming a valid invite and having an account. §9.6 gives
 * it three jobs and the design serves all three:
 *
 *  1. **Informed consent.** The §3.7 disclosures must be *seen*, not merely
 *     available.
 *  2. **Legal record.** Acceptance, timestamped, against a document version.
 *  3. **Friction as a filter.** A deliberate, unskippable step deters casual and
 *     automated signups.
 *
 * Structure follows `BridgeSetupActivity`: a step constant, a hierarchy rebuilt
 * on every step change, and a primary action disabled until its conditions are
 * met. §9.6.1 — *"That pattern works and users of NexLink have already
 * encountered it."*
 *
 * All the rules live in [AcceptanceGateState], which is pure and tested. This
 * class renders it.
 *
 * **No account exists until [finishGate] runs.** §2.8, invariant 3: *no account
 * is created without a redeemed invite token and a completed acceptance record.*
 */
class AcceptanceGateActivity : AppCompatActivity() {

    private val state = AcceptanceGateState()
    private lateinit var ui: Ui
    private lateinit var root: LinearLayout

    /** Survives the rebuild between steps; see [AcceptanceGateState]'s header. */
    private var inviteText: String = ""
    private var dobDay = ""; private var dobMonth = ""; private var dobYear = ""
    private var pendingError: CharSequence? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)

        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            with(ui) { setPadding(20.px(), 24.px(), 20.px(), 24.px()) }
        }
        scroll.addView(root)
        setContentView(scroll)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!state.back()) finish() else render()
            }
        })

        render()
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun render() {
        root.removeAllViews()
        val e = pendingError; pendingError = null

        if (state.step != AcceptanceGateState.Step.INVITE) {
            root.addView(ui.caption(getString(
                R.string.gate_step, state.displayStep(), AcceptanceGateState.Step.gateSteps.size)))
        }

        when (state.step) {
            AcceptanceGateState.Step.INVITE -> renderInvite()
            AcceptanceGateState.Step.WHAT_THIS_IS -> renderWhatThisIs()
            AcceptanceGateState.Step.WHAT_WE_SEE -> renderWhatWeSee()
            AcceptanceGateState.Step.YOUR_DATA -> renderYourData()
            AcceptanceGateState.Step.TERMS -> renderTerms()
            AcceptanceGateState.Step.AGE -> renderAge()
        }

        if (e != null) root.addView(ui.error(e))
    }

    // ── door: the invite code (§9.3) ─────────────────────────────────────────

    private fun renderInvite() {
        root.addView(ui.title(getString(R.string.invite_title)))
        root.addView(ui.body(getString(R.string.invite_body)))

        val field = EditText(this).apply {
            hint = getString(R.string.invite_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
            setText(inviteText)
            setSelection(text.length)
        }
        InviteCodeField.attach(field)
        root.addView(field)
        root.addView(ui.caption(getString(R.string.invite_help)))

        root.addView(ui.primaryButton(getString(R.string.invite_continue)) {
            inviteText = field.text.toString()
            val error = InviteCode.validate(inviteText)
            if (error == null) {
                state.submitInvite(inviteText)
                render()
            } else {
                pendingError = describe(error)
                render()
            }
        })
    }

    private fun describe(error: InviteCodeError): CharSequence = when (error) {
        is InviteCodeError.Empty -> getString(R.string.invite_err_short, 0)
        is InviteCodeError.TooShort -> getString(R.string.invite_err_short, error.length)
        is InviteCodeError.TooLong -> getString(R.string.invite_err_long, error.length)
        is InviteCodeError.ExcludedCharacter -> getString(R.string.invite_err_excluded)
        is InviteCodeError.IllegalCharacter -> getString(R.string.invite_err_char, error.char.toString())
    }

    // ── screen 1 ─────────────────────────────────────────────────────────────

    private fun renderWhatThisIs() {
        root.addView(ui.title(getString(R.string.gate1_title)))
        root.addView(ui.body(getString(R.string.gate1_body)))
        addNav()
    }

    // ── screen 2 — the most important one (§9.6.1) ───────────────────────────

    private fun renderWhatWeSee() {
        root.addView(ui.title(getString(R.string.gate2_title)))

        // §9.6.1: "Two columns, equal visual weight." Equal weight is the point —
        // a design that shrinks the right-hand column is overclaiming by layout.
        val columns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP)
        }
        columns.addView(
            disclosureColumn(
                getString(R.string.gate2_cannot_header),
                getString(R.string.gate2_cannot_items),
                R.color.social_cannot_see
            )
        )
        columns.addView(with(ui) { View(this@AcceptanceGateActivity).apply {
            layoutParams = LinearLayout.LayoutParams(12.px(), 1)
        } })
        columns.addView(
            disclosureColumn(
                getString(R.string.gate2_can_header),
                getString(R.string.gate2_can_items),
                R.color.social_can_see
            )
        )
        root.addView(columns)

        root.addView(with(ui) { spacer(12) })
        root.addView(ui.caption(getString(R.string.gate2_footer)))
        addNav()
    }

    private fun disclosureColumn(header: String, items: String, colour: Int): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, Ui.WRAP, 1f)
            addView(TextView(this@AcceptanceGateActivity).apply {
                text = header
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(with(ui) { col(colour) })
                with(ui) { setPadding(0, 0, 0, 8.px()) }
            })
            items.split("\n").forEach { line ->
                addView(TextView(this@AcceptanceGateActivity).apply {
                    text = "• $line"
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(with(ui) { col(R.color.social_text) })
                    with(ui) { setPadding(0, 3.px(), 0, 3.px()) }
                })
            }
        }

    // ── screen 3 ─────────────────────────────────────────────────────────────

    private fun renderYourData() {
        root.addView(ui.title(getString(R.string.gate3_title)))
        root.addView(ui.body(getString(R.string.gate3_body)))
        addNav()
    }

    // ── screen 4 (§9.6.1: three boxes, none pre-ticked, docs opened first) ───

    private fun renderTerms() {
        root.addView(ui.title(getString(R.string.gate4_title)))
        root.addView(ui.body(getString(R.string.gate4_body)))

        root.addView(ui.textButton(getString(R.string.gate4_read_terms)) {
            state.openTerms(); openPolicy(POLICY_TERMS); render()
        })
        root.addView(ui.textButton(getString(R.string.gate4_read_privacy)) {
            state.openPrivacy(); openPolicy(POLICY_PRIVACY); render()
        })

        root.addView(with(ui) { spacer(8) })
        root.addView(ui.divider())
        root.addView(with(ui) { spacer(8) })

        lateinit var next: Button

        val cbTerms = ui.checkbox(getString(R.string.gate4_cb_terms)) { checked ->
            if (!state.setTermsChecked(checked)) {
                toast(getString(R.string.gate4_must_open))
                render()
                return@checkbox
            }
            next.isEnabled = state.termsComplete
        }.apply { isEnabled = state.termsOpened; isChecked = state.termsChecked }

        val cbPrivacy = ui.checkbox(getString(R.string.gate4_cb_privacy)) { checked ->
            if (!state.setPrivacyChecked(checked)) {
                toast(getString(R.string.gate4_must_open))
                render()
                return@checkbox
            }
            next.isEnabled = state.termsComplete
        }.apply { isEnabled = state.privacyOpened; isChecked = state.privacyChecked }

        val cbWarranty = ui.checkbox(getString(R.string.gate4_cb_warranty)) { checked ->
            state.setWarrantyChecked(checked)
            next.isEnabled = state.termsComplete
        }.apply { isChecked = state.warrantyChecked }

        root.addView(cbTerms); root.addView(cbPrivacy); root.addView(cbWarranty)

        next = ui.primaryButton(getString(R.string.gate_continue)) {
            if (state.advance()) render()
        }
        next.isEnabled = state.termsComplete
        root.addView(next)
        root.addView(backButton())
    }

    // ── screen 5 (§9.6.1: a date, not a checkbox; §9.6.2: not stored) ────────

    private fun renderAge() {
        root.addView(ui.title(getString(R.string.gate5_title)))
        root.addView(ui.body(getString(R.string.gate5_body, AgeCheck.MINIMUM_AGE)))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val day = dateField(getString(R.string.gate5_day), 2, dobDay)
        val month = dateField(getString(R.string.gate5_month), 2, dobMonth)
        val year = dateField(getString(R.string.gate5_year), 4, dobYear)
        row.addView(day); row.addView(month); row.addView(year)
        root.addView(row)

        // §9.6.2 — say it on the screen, not only in the privacy policy.
        root.addView(ui.caption(getString(R.string.gate5_privacy_note)))

        root.addView(ui.primaryButton(getString(R.string.gate_create_account)) {
            dobDay = day.text.toString(); dobMonth = month.text.toString(); dobYear = year.text.toString()
            state.setDob(dobDay.toIntOrNull(), dobMonth.toIntOrNull(), dobYear.toIntOrNull())
            when (val r = state.checkAge()) {
                is AgeCheck.Result.Ok -> finishGate()
                is AgeCheck.Result.Incomplete -> fail(getString(R.string.gate5_err_incomplete))
                is AgeCheck.Result.NotADate -> fail(getString(R.string.gate5_err_invalid))
                is AgeCheck.Result.InFuture -> fail(getString(R.string.gate5_err_future))
                is AgeCheck.Result.TooYoung -> fail(getString(R.string.gate5_err_too_young, r.minimumAge))
            }
        })
        root.addView(backButton())
    }

    private fun dateField(hint: String, maxLen: Int, value: String): EditText =
        EditText(this).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(maxLen))
            gravity = Gravity.CENTER
            setText(value)
            layoutParams = LinearLayout.LayoutParams(0, Ui.WRAP, if (maxLen == 4) 1.6f else 1f)
        }

    // ─────────────────────────────────────────────────────────────────────────

    private fun addNav() {
        root.addView(ui.primaryButton(getString(R.string.gate_continue)) {
            if (state.advance()) render()
        })
        root.addView(backButton())
    }

    private fun backButton(): Button =
        ui.textButton(getString(R.string.gate_back)) {
            if (!state.back()) finish() else render()
        }.apply { gravity = Gravity.CENTER }

    private fun fail(message: CharSequence) { pendingError = message; render() }

    private fun toast(m: CharSequence) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    /**
     * Phase 0 stub. §33.1 builds the gate against a fake session; phase 3
     * (§33.4) replaces this with the real registration call, which redeems the
     * token and writes the `acceptance_record` (§9.6.2) server-side.
     */
    private fun openPolicy(which: String) {
        toast("$which ${if (which == POLICY_TERMS) PolicyVersions.TERMS else PolicyVersions.PRIVACY} " +
              "— document not yet written (§4.7, phase 5)")
    }

    private fun finishGate() {
        val outcome = state.toOutcome()
        if (outcome == null) {
            // Defensive: toOutcome() re-checks every condition, so reaching here
            // means the state machine and the UI disagree. Fail loudly rather
            // than creating an account (§2.8, invariant 3).
            fail("Something is incomplete. Go back and check each step.")
            return
        }
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(EXTRA_INVITE_CODE, outcome.inviteCode)
            putExtra(EXTRA_AGE_CONFIRMED, outcome.ageConfirmed)
            putExtra(EXTRA_TERMS_VERSION, PolicyVersions.TERMS)
            putExtra(EXTRA_PRIVACY_VERSION, PolicyVersions.PRIVACY)
            // Deliberately absent: the date of birth (§9.6.2).
        })
        finish()
    }

    companion object {
        const val EXTRA_INVITE_CODE = "invite_code"
        const val EXTRA_AGE_CONFIRMED = "age_confirmed"
        const val EXTRA_TERMS_VERSION = "terms_version"
        const val EXTRA_PRIVACY_VERSION = "privacy_version"

        private const val POLICY_TERMS = "Terms of Service"
        private const val POLICY_PRIVACY = "Privacy Policy"

        fun intent(context: Context) = Intent(context, AcceptanceGateActivity::class.java)
    }
}
