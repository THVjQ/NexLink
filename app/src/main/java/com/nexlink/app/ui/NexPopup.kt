package com.nexlink.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexlink.app.R

/**
 * Consistent popup-menu used everywhere in the app.
 * Appears above or below the anchor view depending on available screen space.
 */
object NexPopup {

    data class Item(
        val label: String,
        val iconRes: Int,
        val isDestructive: Boolean = false,
        val action: () -> Unit
    )

    fun show(anchor: View, items: List<Item>) {
        val ctx = anchor.context
        val inf = LayoutInflater.from(ctx)
        val root = inf.inflate(R.layout.popup_message_menu, null) as LinearLayout

        items.forEach { item ->
            val row = inf.inflate(R.layout.item_popup_menu, root, false)
            val icon  = row.findViewById<ImageView>(R.id.menuIcon)
            val label = row.findViewById<TextView>(R.id.menuLabel)
            icon.setImageResource(item.iconRes)
            label.text = item.label
            if (item.isDestructive) {
                val red = ContextCompat.getColor(ctx, android.R.color.holo_red_light)
                icon.imageTintList = ColorStateList.valueOf(red)
                label.setTextColor(red)
            }
            root.addView(row)
        }

        val popup = PopupWindow(root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.elevation = 20f
        popup.isOutsideTouchable = true

        // Wire clicks after popup is created so dismiss() works
        items.forEachIndexed { i, item ->
            (root.getChildAt(i) as View).setOnClickListener { popup.dismiss(); item.action() }
        }

        // Measure popup height before showing so we can decide above/below
        root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popH = root.measuredHeight
        val popW = root.measuredWidth

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val screenH = ctx.resources.displayMetrics.heightPixels
        val screenW = ctx.resources.displayMetrics.widthPixels

        // Clamp x so the popup never clips off the right edge
        val x = maxOf(8, minOf(loc[0], screenW - popW - 8))
        val showBelow = loc[1] + anchor.height + popH + 32 < screenH
        val y = if (showBelow) loc[1] + anchor.height + 8 else loc[1] - popH - 8

        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
    }
}
