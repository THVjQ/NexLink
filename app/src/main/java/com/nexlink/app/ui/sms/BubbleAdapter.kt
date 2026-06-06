package com.nexlink.app.ui.sms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.nexlink.app.R
import com.nexlink.app.db.SmsMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private sealed class Row {
    data class DateSep(val label: String) : Row()
    data class Msg(val msg: SmsMessage) : Row()
}

class BubbleAdapter(
    private val isGroup: Boolean = false,
    private val onForward: ((SmsMessage) -> Unit)? = null,
    private val onDelete: ((id: Long, isMms: Boolean) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows = listOf<Row>()

    fun setData(msgs: List<SmsMessage>) {
        rows = buildRows(msgs)
        notifyDataSetChanged()
    }

    private fun buildRows(msgs: List<SmsMessage>): List<Row> {
        val result  = mutableListOf<Row>()
        var lastDay = ""
        for (msg in msgs) {
            val day = dayLabel(msg.timestamp)
            if (day != lastDay) { result += Row.DateSep(day); lastDay = day }
            result += Row.Msg(msg)
        }
        return result
    }

    private fun dayLabel(ms: Long): String {
        val msgCal   = Calendar.getInstance().apply { timeInMillis = ms }
        val todayCal = Calendar.getInstance()
        val yesCal   = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        fun same(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return when {
            same(msgCal, todayCal) -> "Today"
            same(msgCal, yesCal)   -> "Yesterday"
            msgCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) ->
                SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(ms))
            else ->
                SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(ms))
        }
    }

    // 0=date  1=text-in  2=text-out  3=voice-in  4=voice-out  5=image/video-in  6=image/video-out
    override fun getItemViewType(pos: Int) = when (val r = rows[pos]) {
        is Row.DateSep -> 0
        is Row.Msg -> {
            val m = r.msg
            val isMedia = m.mimeType?.startsWith("image/") == true || m.mimeType?.startsWith("video/") == true
            when {
                m.isVoice  &&  m.isIncoming  -> 3
                m.isVoice  && !m.isIncoming  -> 4
                isMedia    &&  m.isIncoming  -> 5
                isMedia    && !m.isIncoming  -> 6
                m.isIncoming                 -> 1
                else                         -> 2
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            0    -> DateVH(inf.inflate(R.layout.item_date_header, parent, false))
            1    -> MsgVH(inf.inflate(R.layout.item_bubble_in, parent, false))
            2    -> MsgVH(inf.inflate(R.layout.item_bubble_out, parent, false))
            3    -> MsgVH(inf.inflate(R.layout.item_bubble_voice_in, parent, false))
            4    -> MsgVH(inf.inflate(R.layout.item_bubble_voice_out, parent, false))
            5    -> ImageVH(inf.inflate(R.layout.item_bubble_image_in, parent, false))
            else -> ImageVH(inf.inflate(R.layout.item_bubble_image_out, parent, false))
        }
    }

    override fun getItemCount() = rows.size

    inner class DateVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvDate: TextView = v.findViewById(R.id.tvDate)
    }

    inner class MsgVH(v: View) : RecyclerView.ViewHolder(v) {
        val bubble:  TextView     = v.findViewById(R.id.tvBubble)
        val time:    TextView     = v.findViewById(R.id.tvTime)
        val sender:  TextView?    = v.findViewById(R.id.tvSenderName)
        val btnPlay: ImageButton? = v.findViewById<View>(R.id.btnPlay) as? ImageButton
    }

    inner class ImageVH(v: View) : RecyclerView.ViewHolder(v) {
        val image:  ImageView = v.findViewById(R.id.ivBubble)
        val time:   TextView  = v.findViewById(R.id.tvTime)
        val sender: TextView? = v.findViewById(R.id.tvSenderName)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        when (val r = rows[pos]) {
            is Row.DateSep -> (holder as DateVH).tvDate.text = r.label
            is Row.Msg     -> when (holder) {
                is MsgVH   -> bindMsg(holder, r.msg)
                is ImageVH -> bindImage(holder, r.msg)
                else       -> {}
            }
        }
    }

    private fun bindSender(sender: TextView?, m: SmsMessage) {
        if (sender == null) return
        if (isGroup && m.isIncoming) {
            sender.text      = m.senderName ?: m.address
            sender.visibility = View.VISIBLE
        } else {
            sender.visibility = View.GONE
        }
    }

    private fun bindMsg(h: MsgVH, m: SmsMessage) {
        h.bubble.text = m.body
        h.time.text   = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(m.timestamp))
        bindSender(h.sender, m)
        if (m.isVoice && m.mediaUri != null) {
            h.btnPlay?.setOnClickListener { btn -> playAudio(btn.context, m.mediaUri) }
        }
        h.itemView.setOnLongClickListener { showMenu(it.context, m); true }
    }

    private fun bindImage(h: ImageVH, m: SmsMessage) {
        h.time.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(m.timestamp))
        bindSender(h.sender, m)
        val isVideo = m.mimeType?.startsWith("video/") == true
        if (isVideo) {
            h.image.setImageResource(android.R.drawable.ic_media_play)
        } else {
            m.mediaUri?.let { uri ->
                try { h.image.setImageURI(Uri.parse(uri)) }
                catch (_: Exception) { h.image.setImageResource(android.R.drawable.ic_menu_gallery) }
            }
        }
        h.image.setOnClickListener { openMedia(it.context, m.mediaUri, m.mimeType) }
        h.itemView.setOnLongClickListener { showMenu(it.context, m); true }
    }

    private fun showMenu(ctx: Context, m: SmsMessage) {
        val opts = mutableListOf<String>()
        if (m.body.isNotBlank()) opts += "Copy text"
        opts += "Forward"
        opts += "Delete message"
        AlertDialog.Builder(ctx)
            .setItems(opts.toTypedArray()) { _, i ->
                when (opts[i]) {
                    "Copy text" -> {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("msg", m.body))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    }
                    "Forward" -> onForward?.invoke(m)
                    "Delete message" -> {
                        AlertDialog.Builder(ctx)
                            .setMessage("Delete this message?")
                            .setPositiveButton("Delete") { _, _ -> onDelete?.invoke(m.id, m.isMms) }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun openMedia(ctx: Context, uri: String?, mimeType: String?) {
        uri ?: return
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uri), mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (e: Exception) {
            Toast.makeText(ctx, "Cannot open: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playAudio(ctx: Context, uri: String) {
        try {
            val player = MediaPlayer()
            player.setDataSource(ctx, Uri.parse(uri))
            player.prepareAsync()
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Cannot play: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
