package com.nexlink.app.ui.sms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DrawerFileAdapter(
    private val files: List<Pair<String, String>>,
    private val onClick: (Pair<String, String>) -> Unit
) : RecyclerView.Adapter<DrawerFileAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            id = android.R.id.text1
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            setPadding(16.dp(context), 12.dp(context), 16.dp(context), 12.dp(context))
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            background = android.util.TypedValue().let { tv ->
                parent.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                android.content.res.ColorStateList.valueOf(0).let { parent.context.getDrawable(tv.resourceId) }
            }
        }
        return VH(tv)
    }

    override fun getItemCount() = files.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val pair = files[pos]
        h.name.text = "📎 ${pair.second}"
        h.name.setOnClickListener { onClick(pair) }
    }

    private fun Int.dp(ctx: android.content.Context) =
        (this * ctx.resources.displayMetrics.density).toInt()
}
