package com.nexlink.social.ui.onboarding

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.nexlink.social.ui.R

/**
 * The terms screen, driven rather than reasoned about.
 *
 * `AcceptanceGateStateTest` covers the rules and could not have caught what
 * shipped: `renderTerms` declared `next` as a `lateinit` above the checkboxes
 * and assigned it below them, and `Ui.checkbox` installs its change listener
 * *before* the caller's `.apply { isChecked = … }` runs. So restoring a ticked
 * box during a re-render fired a listener that touched a property that did not
 * exist yet, and the process died with
 * `UninitializedPropertyAccessException`.
 *
 * On the handset that looked like nothing at all: the gate vanished and an
 * empty account form came back. No message, no account, and the invite token
 * left `pending` — **account creation was impossible and the app said nothing**
 * (found 2026-09-16 while creating the operator's own account).
 *
 * The bug lived in the interaction between a view builder and an activity, so
 * the test has to build the views. Robolectric is already here for the chrome
 * measurements; this is the same argument.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcceptanceGateTermsScreenTest {

    /**
     * The theme has to be set by hand. A library module's test manifest carries
     * no `<activity android:theme=…>`, so `AppCompatActivity` finds the platform
     * default and throws "You need to use a Theme.AppCompat theme" before any of
     * the code under test runs.
     */
    private fun gate() = Robolectric
        .buildActivity(
            AcceptanceGateActivity::class.java,
            AcceptanceGateActivity.intent(
                org.robolectric.RuntimeEnvironment.getApplication(), "X7K29QMF3BTD"
            ),
        )
        .apply { get().setTheme(R.style.Theme_NexLinkSocial) }
        .setup()

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (i in 0 until childCount) yieldAll(getChildAt(i).descendants())
        }
    }

    private fun AcceptanceGateActivity.find(text: String): View? =
        window.decorView.descendants().firstOrNull {
            (it as? TextView)?.text?.toString() == text
        }

    private fun AcceptanceGateActivity.walkToTerms() {
        val cont = getString(R.string.gate_continue)
        val terms = getString(R.string.gate4_title)
        repeat(6) {
            if (find(terms) != null) return
            (find(cont) as? Button)?.performClick()
        }
    }

    @Test fun `ticking a box and then re-rendering does not crash`() {
        val controller = gate()
        val a = controller.get()
        a.walkToTerms()
        assertTrue("never reached the terms screen", a.find(a.getString(R.string.gate4_title)) != null)

        // Tick the one box that has no document, then force the re-render that
        // used to kill the process. Any uncaught exception fails the test here.
        (a.find(a.getString(R.string.gate4_cb_warranty)) as CheckBox).isChecked = true
        (a.find(a.getString(R.string.gate4_read_terms)) as Button).performClick()

        assertFalse("the gate finished instead of re-rendering", a.isFinishing)
        assertTrue("the tick did not survive the re-render",
            (a.find(a.getString(R.string.gate4_cb_warranty)) as CheckBox).isChecked)
    }

    /**
     * §9.6.4 — the operator removed the open-the-document-first rule on
     * 2026-09-16. Asserted at the screen, not just in the state, because the
     * screen enforced it a second time with `isEnabled`.
     */
    @Test fun `both policy boxes are tickable without opening anything`() {
        val a = gate().get()
        a.walkToTerms()

        val t = a.find(a.getString(R.string.gate4_cb_terms)) as CheckBox
        val p = a.find(a.getString(R.string.gate4_cb_privacy)) as CheckBox
        assertTrue("the terms box is disabled", t.isEnabled)
        assertTrue("the privacy box is disabled", p.isEnabled)

        t.isChecked = true
        p.isChecked = true
        (a.find(a.getString(R.string.gate4_cb_warranty)) as CheckBox).isChecked = true

        // All three ticked is the whole of §9.6.1's requirement, so Continue
        // must now be live.
        assertTrue("Continue stayed disabled with all three ticked",
            (a.find(a.getString(R.string.gate_continue)) as Button).isEnabled)
    }
}
