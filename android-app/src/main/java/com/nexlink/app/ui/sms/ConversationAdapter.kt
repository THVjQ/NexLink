package com.nexlink.app.ui.sms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexlink.app.R
import com.nexlink.app.db.Conversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(private val onClick: (Conversation) -> Unit) :
    RecyclerView.Adapter<ConversationAdapter.VH>() {

    private var items = listOf<Conversation>()

    fun setData(data: List<Conversation>) { items = data; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar:  TextView = v.findViewById(R.id.tvAvatar)
        val name:    TextView = v.findViewById(R.id.tvName)
        val preview: TextView = v.findViewById(R.id.tvPreview)
        val time:    TextView = v.findViewById(R.id.tvTime)
        val badge:   TextView = v.findViewById(R.id.tvBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = items[pos]
        val initials = c.contactName.split(" ").take(2).joinToString("") { it.take(1).uppercase() }
            .ifBlank { c.address.take(2) }
        h.avatar.text  = initials
        h.name.text    = c.contactName
        h.preview.text = c.lastMessage
        h.time.text    = formatTime(c.timestamp)
        if (c.unreadCount > 0) {
            h.badge.visibility = View.VISIBLE
            h.badge.text = c.unreadCount.toString()
            h.name.alpha = 1f
        } else {
            h.badge.visibility = View.GONE
            h.name.alpha = 0.6f
        }
        h.itemView.setOnClickListener { onClick(c) }
    }

    private fun formatTime(ms: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - ms
        return when {
            diff < 60_000            -> "now"
            diff < 3_600_000         -> "${diff / 60_000}m"
            diff < 86_400_000        -> "${diff / 3_600_000}h"
            diff < 7 * 86_400_000L   -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ms))
            else                     -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
        }
    }
}
