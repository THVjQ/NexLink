package com.nexlink.app.ui.sms

import android.content.Context
import android.graphics.Paint
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexlink.app.R
import com.nexlink.app.db.SmsHelper

/**
 * The member list shared by group chats and broadcasts: one row per person with a Remove button,
 * plus "Add people…". It only edits a list — what a changed list *means* (a new MMS thread for a
 * group, an in-place edit for a broadcast) is up to [onSave].
 *
 * Removal used to be an unticked checkbox, which nobody read as "remove".
 */
object MembersDialog {

    /** Normalised identity for a number, so 0412… and +61412… are one person. */
    fun memberKey(address: String): String {
        val digits = address.filter { it.isDigit() }
        return when {
            digits.length >= 9  -> digits.takeLast(9)
            digits.isNotEmpty() -> digits
            else                -> address.trim().lowercase()
        }
    }

    fun clean(members: List<String>) =
        members.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { memberKey(it) }

    fun show(
        ctx: Context,
        title: String,
        members: List<String>,
        onAddPeople: (kept: List<String>) -> Unit,
        onSave: (kept: List<String>) -> Unit
    ) {
        val current = clean(members)
        val removed = BooleanArray(current.size)
        val dp = ctx.resources.displayMetrics.density

        val list = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), 0)
        }
        current.forEachIndexed { i, num ->
            val name = SmsHelper.getContactName(ctx, num)
            val label = TextView(ctx).apply {
                text = if (name == num) num else "$name\n$num"
                setTextColor(ctx.getColor(R.color.text))
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btn = MaterialButton(ctx, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = "Remove"
                setTextColor(ctx.getColor(R.color.danger))
                contentDescription = "Remove $name"
            }
            btn.setOnClickListener {
                removed[i] = !removed[i]
                btn.text = if (removed[i]) "Undo" else "Remove"
                btn.setTextColor(ctx.getColor(if (removed[i]) R.color.accent else R.color.danger))
                label.paintFlags = if (removed[i]) label.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                                   else label.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                label.alpha = if (removed[i]) 0.5f else 1f
            }
            list.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = (48 * dp).toInt()
                addView(label); addView(btn)
            })
        }
        if (current.isEmpty()) list.addView(TextView(ctx).apply {
            text = "No one yet — add people below."
            setTextColor(ctx.getColor(R.color.muted))
        })

        fun kept() = current.filterIndexed { i, _ -> !removed[i] }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(ScrollView(ctx).apply { addView(list) })
            .setNeutralButton("Add people…", null)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .show()

        // Wired after show() so a refusal can leave the dialog open.
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            dialog.dismiss(); onAddPeople(kept())
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val k = kept()
            if (k.isEmpty()) Toast.makeText(ctx, "Keep at least one person", Toast.LENGTH_SHORT).show()
            else { dialog.dismiss(); onSave(k) }
        }
    }
}
