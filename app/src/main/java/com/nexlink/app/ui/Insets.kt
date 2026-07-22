package com.nexlink.app.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Pads [this] by the system-bar + display-cutout insets on top of whatever base padding it
 * already has. Needed because API 36 enforces edge-to-edge (§17) and can't be opted out of, so
 * content otherwise draws under the status bar and navigation bar.
 *
 * Call once after the view's base padding is set; the base padding is captured at call time and
 * the insets are added to it each time they change.
 */
fun View.applySystemBarInsetsPadding() {
    val baseL = paddingLeft
    val baseT = paddingTop
    val baseR = paddingRight
    val baseB = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        v.setPadding(baseL + bars.left, baseT + bars.top, baseR + bars.right, baseB + bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
