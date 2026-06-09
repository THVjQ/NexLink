package com.nexlink.app.ui.sms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexlink.app.R
import com.nexlink.app.db.Conversation
import com.nexlink.app.db.CryptoStore
import com.nexlink.app.db.PinStore
import com.nexlink.app.db.ReadTracker
import com.nexlink.app.util.AvatarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val onClick: (Conversation) -> Unit,
    private val onLongClick: ((Conversation) -> Unit)? = null
) :
    RecyclerView.Adapter<ConversationAdapter.VH>() {

    private var items = listOf<Conversation>()

    fun setData(data: List<Conversation>) { items = data; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar:  TextView   = v.findViewById(R.id.tvAvatar)
        val name:    TextView   = v.findViewById(R.id.tvName)
        val preview: TextView   = v.findViewById(R.id.tvPreview)
        val time:    TextView   = v.findViewById(R.id.tvTime)
        val badge:   TextView   = v.findViewById(R.id.tvBadge)
        val lock:    ImageView  = v.findViewById(R.id.ivLock)
        val pin:     ImageView  = v.findViewById(R.id.ivPin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = items[pos]
        val ctx = h.itemView.context
        val initials = c.contactName.split(" ").take(2)
            .joinToString("") { it.take(1).uppercase() }.ifBlank { c.address.take(2) }

        h.avatar.background.mutate().setTint(AvatarColors.colorFor(initials))
        h.avatar.text  = initials
        h.name.text    = c.contactName.ifBlank { c.address }
        h.preview.text = c.lastMessage
        h.time.text    = formatTime(c.timestamp)
        h.lock.visibility = if (CryptoStore.getSessionKey(h.itemView.context, c.address) != null)
            View.VISIBLE else View.GONE
        h.pin.visibility = if (PinStore.isPinned(ctx, c.threadId)) View.VISIBLE else View.GONE

        // Treat as read if system says 0 unread OR user locally viewed it
        val effectivelyRead = c.unreadCount == 0 ||
            ReadTracker.isLocallyRead(ctx, c.address, c.timestamp)

        if (!effectivelyRead) {
            h.badge.visibility = View.VISIBLE
            h.badge.text       = c.unreadCount.toString()
            h.name.setTypeface(null, android.graphics.Typeface.BOLD)
            h.preview.alpha    = 1f
        } else {
            h.badge.visibility = View.GONE
            h.name.setTypeface(null, android.graphics.Typeface.NORMAL)
            h.preview.alpha    = 0.6f
        }

        h.itemView.setOnClickListener { onClick(c) }
        h.itemView.setOnLongClickListener { onLongClick?.invoke(c); true }
    }

    private fun formatTime(ms: Long): String {
        val diff = System.currentTimeMillis() - ms
        return when {
            diff < 60_000           -> "now"
            diff < 3_600_000        -> "${diff / 60_000}m"
            diff < 86_400_000       -> "${diff / 3_600_000}h"
            diff < 7 * 86_400_000L  -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ms))
            else                    -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
        }
    }
}
