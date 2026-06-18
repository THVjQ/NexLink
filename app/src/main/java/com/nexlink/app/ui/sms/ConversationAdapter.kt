package com.nexlink.app.ui.sms

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexlink.app.R
import com.nexlink.app.db.ChatCustomizationStore
import com.nexlink.shared.Conversation
import com.nexlink.app.db.CryptoStore
import com.nexlink.app.db.NotificationPrefs
import com.nexlink.app.db.PinStore
import com.nexlink.app.db.ReadTracker
import com.nexlink.shared.AvatarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val onClick: (Conversation) -> Unit,
    private val onLongClick: ((Conversation, View) -> Unit)? = null
) :
    RecyclerView.Adapter<ConversationAdapter.VH>() {

    private var items = listOf<Conversation>()

    fun setData(data: List<Conversation>) { items = data; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar:      TextView   = v.findViewById(R.id.tvAvatar)
        val avatarPhoto: ImageView  = v.findViewById(R.id.ivAvatarPhoto)
        val name:        TextView   = v.findViewById(R.id.tvName)
        val preview:     TextView   = v.findViewById(R.id.tvPreview)
        val time:        TextView   = v.findViewById(R.id.tvTime)
        val badge:       TextView   = v.findViewById(R.id.tvBadge)
        val lock:        ImageView  = v.findViewById(R.id.ivLock)
        val pin:         ImageView  = v.findViewById(R.id.ivPin)
        val rcs:         ImageView  = v.findViewById(R.id.ivRcs)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = items[pos]
        val ctx = h.itemView.context
        val initials = c.contactName.split(" ").take(2)
            .joinToString("") { it.take(1).uppercase() }.ifBlank { c.address.take(2) }

        // Custom icon takes highest priority, then contact photo, then coloured initials
        val iconUri = ChatCustomizationStore.getIcon(ctx, c.address)
        if (!iconUri.isNullOrEmpty()) {
            h.avatarPhoto.visibility = View.VISIBLE
            h.avatar.visibility = View.INVISIBLE
            try {
                val raw = ctx.contentResolver.openInputStream(Uri.parse(iconUri))?.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
                if (raw != null) h.avatarPhoto.setImageBitmap(cropToCircle(raw))
                else h.avatarPhoto.setImageURI(Uri.parse(iconUri))
            } catch (_: Exception) {
                h.avatarPhoto.visibility = View.GONE
                h.avatar.visibility = View.VISIBLE
                h.avatar.background.mutate().setTint(AvatarColors.colorFor(initials))
                h.avatar.text = initials
            }
        } else {
            // Reset to initials first so recycled views don't show stale photos
            h.avatarPhoto.visibility = View.GONE
            h.avatar.visibility = View.VISIBLE
            h.avatar.background.mutate().setTint(AvatarColors.colorFor(initials))
            h.avatar.text = initials
            // Tag the photo view with the address to guard against RecyclerView recycling races
            h.avatarPhoto.tag = c.address
            Thread {
                val photoUri = queryContactPhoto(ctx, c.address)
                h.avatarPhoto.post {
                    if (h.avatarPhoto.tag == c.address && photoUri != null) {
                        try {
                            val raw = ctx.contentResolver.openInputStream(photoUri)?.use {
                                android.graphics.BitmapFactory.decodeStream(it)
                            }
                            if (raw != null) {
                                h.avatarPhoto.setImageBitmap(cropToCircle(raw))
                                h.avatarPhoto.visibility = View.VISIBLE
                                h.avatar.visibility = View.INVISIBLE
                            }
                        } catch (_: Exception) { /* leave initials */ }
                    }
                }
            }.start()
        }
        h.name.text    = c.contactName.ifBlank { c.address }
        h.preview.text = c.lastMessage
        h.time.text    = formatTime(c.timestamp)
        h.lock.visibility = if (CryptoStore.getSessionKey(h.itemView.context, c.address) != null)
            View.VISIBLE else View.GONE
        h.pin.visibility = if (PinStore.isPinned(ctx, c.threadId)) View.VISIBLE else View.GONE
        h.rcs.visibility = if (NotificationPrefs.isRcsEnabled(ctx)) View.VISIBLE else View.GONE

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
        h.itemView.setOnLongClickListener { onLongClick?.invoke(c, h.itemView); true }
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

    private fun cropToCircle(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f
        canvas.drawCircle(r, r, r, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, ((size - src.width) / 2f), ((size - src.height) / 2f), paint)
        paint.xfermode = null
        if (!src.isRecycled) src.recycle()
        return out
    }

    private fun queryContactPhoto(ctx: android.content.Context, address: String): Uri? {
        if (address.isBlank()) return null
        return try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
            ctx.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { Uri.parse(it) } else null
            }
        } catch (_: Exception) { null }
    }
}
