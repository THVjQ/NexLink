package com.nexlink.app.ui.sms

import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexlink.app.R
import com.nexlink.app.db.SmsMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private sealed class Row {
    data class DateSep(val label: String) : Row()
    data class Msg(val msg: SmsMessage)   : Row()
}

class BubbleAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows = listOf<Row>()

    fun setData(msgs: List<SmsMessage>) {
        rows = buildRows(msgs)
        notifyDataSetChanged()
    }

    private fun buildRows(msgs: List<SmsMessage>): List<Row> {
        val result = mutableListOf<Row>()
        var lastDay = ""
        for (msg in msgs) {
            val day = dayLabel(msg.timestamp)
            if (day != lastDay) {
                result += Row.DateSep(day)
                lastDay = day
            }
            result += Row.Msg(msg)
        }
        return result
    }

    private fun dayLabel(ms: Long): String {
        val msgCal   = Calendar.getInstance().apply { timeInMillis = ms }
        val todayCal = Calendar.getInstance()
        val yesCal   = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        fun same(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR)        == b.get(Calendar.YEAR) &&
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

    // 0=date-sep  1=text-in  2=text-out  3=voice-in  4=voice-out  5=image-in  6=image-out
    override fun getItemViewType(pos: Int) = when (val r = rows[pos]) {
        is Row.DateSep -> 0
        is Row.Msg     -> when {
            r.msg.isVoice && r.msg.isIncoming  -> 3
            r.msg.isVoice && !r.msg.isIncoming -> 4
            r.msg.mimeType?.startsWith("image/") == true && r.msg.isIncoming  -> 5
            r.msg.mimeType?.startsWith("image/") == true && !r.msg.isIncoming -> 6
            r.msg.isIncoming                   -> 1
            else                               -> 2
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

    // ── ViewHolders ──────────────────────────────────────────────────────────

    inner class DateVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvDate: TextView = v.findViewById(R.id.tvDate)
    }

    inner class MsgVH(v: View) : RecyclerView.ViewHolder(v) {
        val bubble:  TextView    = v.findViewById(R.id.tvBubble)
        val time:    TextView    = v.findViewById(R.id.tvTime)
        val btnPlay: ImageButton? = v.findViewById<View>(R.id.btnPlay) as? ImageButton
    }

    inner class ImageVH(v: View) : RecyclerView.ViewHolder(v) {
        val image: ImageView = v.findViewById(R.id.ivBubble)
        val time:  TextView  = v.findViewById(R.id.tvTime)
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

    private fun bindMsg(h: MsgVH, m: SmsMessage) {
        h.bubble.text = m.body
        h.time.text   = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(m.timestamp))
        if (m.isVoice && m.mediaUri != null) {
            h.btnPlay?.setOnClickListener { btn -> playVoice(btn, m.mediaUri) }
        }
    }

    private fun bindImage(h: ImageVH, m: SmsMessage) {
        h.time.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(m.timestamp))
        m.mediaUri?.let { uri ->
            try {
                h.image.setImageURI(android.net.Uri.parse(uri))
            } catch (_: Exception) {
                h.image.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }
    }

    private fun playVoice(btn: View, uri: String) {
        try {
            val player = MediaPlayer()
            player.setDataSource(btn.context, android.net.Uri.parse(uri))
            player.prepareAsync()
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            android.widget.Toast.makeText(btn.context, "Cannot play: ${e.message}",
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
