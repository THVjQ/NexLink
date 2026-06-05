package com.nexlink.app.ui.sms

import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
        val bubble:  TextView    = v.findViewById(R.id.tvBubble)
        val time:    TextView    = v.findViewById(R.id.tvTime)
        val btnPlay: ImageButton? = v.findViewById<View>(R.id.btnPlay) as? ImageButton
    }

    // View types: 0=text-in, 1=text-out, 2=voice-in, 3=voice-out
    override fun getItemViewType(pos: Int): Int {
        val m = items[pos]
        return when {
            m.isVoice && m.isIncoming  -> 2
            m.isVoice && !m.isIncoming -> 3
            m.isIncoming               -> 0
            else                       -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = when (viewType) {
            0    -> R.layout.item_bubble_in
            1    -> R.layout.item_bubble_out
            2    -> R.layout.item_bubble_voice_in
            else -> R.layout.item_bubble_voice_out
        }
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val m = items[pos]
        h.bubble.text = if (m.isVoice) "🎤 Voice message" else m.body
        h.time.text   = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.timestamp))

        if (m.isVoice && m.mediaUri != null) {
            h.btnPlay?.setOnClickListener { btn -> playVoice(btn, m.mediaUri) }
        }
    }

    private fun playVoice(btn: View, uri: String) {
        try {
            val player = MediaPlayer()
            player.setDataSource(btn.context, android.net.Uri.parse(uri))
            player.prepareAsync()
            player.setOnPreparedListener {
                it.start()
                (btn as? ImageButton)?.setImageResource(R.drawable.ic_play)
            }
            player.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            android.widget.Toast.makeText(btn.context, "Cannot play: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
