package com.nexlink.app.ui.inbox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexlink.app.R
import com.nexlink.app.db.DeepLinkHelper
import com.nexlink.app.db.NotificationStore
import com.nexlink.app.db.SocialNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NotificationThread(
    val platform: String,
    val sender: String,
    val latest: SocialNotification,
    val count: Int,
    val unreadCount: Int
)

class NotificationAdapter : RecyclerView.Adapter<NotificationAdapter.VH>() {

    private var items = listOf<NotificationThread>()

    fun setData(notifications: List<SocialNotification>) {
        // Group by platform + sender, pick latest message, keep count
        items = notifications
            .groupBy { it.platform to it.sender }
            .map { (key, msgs) ->
                val sorted = msgs.sortedByDescending { it.timestamp }
                val platform = key.first
                val sender   = key.second
                NotificationThread(
                    platform    = platform,
                    sender      = sender,
                    latest      = sorted.first(),
                    count       = sorted.size,
                    unreadCount = NotificationStore.unreadCountFor(platform, sender)
                )
            }
            .sortedByDescending { it.latest.timestamp }
        notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar:   TextView    = v.findViewById(R.id.tvAvatar)
        val name:     TextView    = v.findViewById(R.id.tvName)
        val platform: TextView    = v.findViewById(R.id.tvPlatform)
        val text:     TextView    = v.findViewById(R.id.tvText)
        val time:     TextView    = v.findViewById(R.id.tvTime)
        val count:    TextView    = v.findViewById(R.id.tvCount)
        val btnCall:  ImageButton = v.findViewById(R.id.btnOpenCall)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val thread = items[pos]
        val n = thread.latest
        val initials = n.sender.split(" ").take(2)
            .joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
        h.avatar.text   = initials
        h.name.text     = n.sender
        h.platform.text = n.platform
        h.text.text     = if (thread.count > 1) "(${thread.count}) ${n.text}" else n.text
        h.time.text     = formatTime(n.timestamp)

        val color = platformColor(n.platform)
        h.platform.setTextColor(color)
        h.avatar.background.mutate().setTint(color and 0x00FFFFFF or 0x33000000)

        // Count badge — only when there are unread messages from this sender
        if (thread.unreadCount > 0) {
            h.count.visibility = View.VISIBLE
            h.count.text = thread.unreadCount.toString()
            h.count.background.mutate().setTint(color)
        } else {
            h.count.visibility = View.GONE
        }

        h.itemView.setOnClickListener {
            DeepLinkHelper.openPlatform(it.context, n.platform)
            // Mark as read so badge disappears after opening
            NotificationStore.markRead(n.platform, n.sender)
        }

        h.btnCall.visibility = View.VISIBLE
        h.btnCall.setImageResource(
            when (n.platform) {
                "WhatsApp", "Telegram" -> R.drawable.ic_call
                else                   -> R.drawable.ic_open_in_app
            }
        )
        h.btnCall.setColorFilter(color)
        h.btnCall.setOnClickListener {
            val ctx = it.context
            when (n.platform) {
                "WhatsApp"  -> DeepLinkHelper.openPlatform(ctx, "WhatsApp")
                "Telegram"  -> DeepLinkHelper.openPlatform(ctx, "Telegram")
                "Signal"    -> DeepLinkHelper.signal(ctx)
                "Messenger" -> DeepLinkHelper.messenger(ctx)
                else        -> DeepLinkHelper.openPlatform(ctx, n.platform)
            }
            NotificationStore.markRead(n.platform, n.sender)
        }
    }

    private fun platformColor(platform: String) = when (platform) {
        "Signal"    -> 0xFF3a9bd5.toInt()
        "Telegram"  -> 0xFF229ed9.toInt()
        "WhatsApp"  -> 0xFF25d366.toInt()
        "Messenger" -> 0xFF0099ff.toInt()
        "Discord"   -> 0xFF5865F2.toInt()
        "Instagram" -> 0xFFE1306C.toInt()
        "Steam"     -> 0xFF1A9FFF.toInt()
        else        -> 0xFF6c5ce7.toInt()
    }

    private fun formatTime(ms: Long): String {
        val diff = System.currentTimeMillis() - ms
        return when {
            diff < 60_000     -> "now"
            diff < 3_600_000  -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else              -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
        }
    }
}
