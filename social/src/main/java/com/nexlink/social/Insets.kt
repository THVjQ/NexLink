package com.nexlink.social

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * §14.10 — keep content out from under the system bars.
 *
 * This exists because the bug is invisible in development and obvious on a
 * phone. The emulator is unusable here (§34.10), so every screen is checked on
 * a Samsung Galaxy S21 FE (SM-G990E, Android 16), which has both a status bar and
 * a gesture bar — and
 * a screen with no inset handling puts its title behind the clock and its last
 * row behind the gesture bar, where the row is both unreadable and **untappable**:
 * the gesture bar eats the touch.
 *
 * That is not cosmetic. §13.5's "Retry" and this screen's "Unlimited" are
 * controls a user cannot reach, which for someone relying on a large font or a
 * screen reader is the difference between a working setting and an absent one.
 *
 * The same three lines were being copied into screens one at a time, which is
 * how the other nine ended up without them. One helper, called from every
 * activity's `onCreate`, is the fix.
 *
 * @param h horizontal padding in px, applied unchanged — side insets are only
 *   non-zero in landscape with a notch, and the existing screens all want a
 *   fixed gutter.
 */
fun View.padForSystemBars(h: Int, top: Int, bottom: Int) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(h + bars.left, bars.top + top, h + bars.right, bars.bottom + bottom)
        // Returned unconsumed: a child that also cares (the composer's IME
        // handling in ConversationActivity) must still see them.
        insets
    }
    // Insets can arrive before or after this call depending on whether the view
    // is already attached. Requesting them explicitly covers the late case,
    // which is what makes this work when called from onCreate.
    ViewCompat.requestApplyInsets(this)
}
