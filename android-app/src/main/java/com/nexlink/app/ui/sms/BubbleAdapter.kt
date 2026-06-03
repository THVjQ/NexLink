package com.nexlink.app.ui.sms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexlink.app.R
import com.nexlink.app.db.SmsMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BubbleAdapter : RecyclerView.Adapter<BubbleAdapter.VH>() {

    private var items = listOf<SmsMessage>()
    fun setData(data: List<SmsMessage>) { items = data; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val bubble: TextView = v.findViewById(R.id.tvBubble)
        val time:   TextView = v.findViewById(R.id.tvTime)
    }

    override fun getItemViewType(pos: Int) = if (items[pos].isIncoming) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == 0) R.layout.item_bubble_in else R.layout.item_bubble_out
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val m = items[pos]
        h.bubble.text = m.body
        h.time.text   = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.timestamp))
    }
}
