package com.nexlink.app.ui.sms

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.nexlink.app.R
import com.nexlink.app.db.AudioTranscriber
import com.nexlink.app.db.CryptoStore
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
    private val onDelete: ((SmsMessage) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var isRcsConversation: Boolean = false

    private var rows = listOf<Row>()
    private var allMessages = listOf<SmsMessage>()
    private var searchQuery = ""

    // Outgoing MMS sent optimistically before content://mms is updated by the system.
    // Cleared when a real DB message appears within 10 seconds of the optimistic timestamp.
    private val optimistic = mutableListOf<SmsMessage>()

    /** Add an outgoing MMS immediately after dispatch so the user sees it as a bubble. */
    fun addOptimistic(msg: SmsMessage) {
        optimistic.add(msg)
        applyDiff(buildRows(mergeOptimistic(rows.filterIsInstance<Row.Msg>().map { it.msg })))
    }

    fun setData(msgs: List<SmsMessage>) {
        allMessages = msgs
        // Drop optimistic entries whose real DB counterpart has appeared (same mime, outgoing, ±30 s)
        optimistic.removeAll { opt ->
            msgs.any { real ->
                !real.isIncoming && real.isMms &&
                real.mimeType == opt.mimeType &&
                kotlin.math.abs(real.timestamp - opt.timestamp) < 30_000L
            }
        }
        val filtered = if (searchQuery.isBlank()) msgs else msgs.filter {
            it.body.contains(searchQuery, ignoreCase = true)
        }
        applyDiff(buildRows(mergeOptimistic(filtered)))
    }

    fun setSearchFilter(query: String) {
        searchQuery = query
        setData(allMessages)
    }

    private fun applyDiff(newRows: List<Row>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = rows.size
            override fun getNewListSize() = newRows.size
            override fun areItemsTheSame(o: Int, n: Int): Boolean {
                val a = rows[o]; val b = newRows[n]
                return when {
                    a is Row.DateSep && b is Row.DateSep -> a.label == b.label
                    a is Row.Msg     && b is Row.Msg     -> a.msg.id == b.msg.id
                    else -> false
                }
            }
            override fun areContentsTheSame(o: Int, n: Int): Boolean {
                val a = rows[o]; val b = newRows[n]
                return when {
                    a is Row.DateSep && b is Row.DateSep -> a == b
                    a is Row.Msg     && b is Row.Msg     ->
                        a.msg.body == b.msg.body && a.msg.mediaUri == b.msg.mediaUri
                    else -> false
                }
            }
        })
        rows = newRows
        diff.dispatchUpdatesTo(this)
    }

    private fun mergeOptimistic(msgs: List<SmsMessage>): List<SmsMessage> {
        if (optimistic.isEmpty()) return msgs
        return (msgs + optimistic).sortedBy { it.timestamp }
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
        val bubble:       TextView     = v.findViewById(R.id.tvBubble)
        val time:         TextView     = v.findViewById(R.id.tvTime)
        val sender:       TextView?    = v.findViewById(R.id.tvSenderName)
        val btnPlay:      ImageButton? = v.findViewById<View>(R.id.btnPlay) as? ImageButton
        val status:       ImageView?   = v.findViewById(R.id.ivStatus)
        val btnTranscript: TextView?   = v.findViewById(R.id.btnTranscript)
        val tvTranscript:  TextView?   = v.findViewById(R.id.tvTranscript)
    }

    inner class ImageVH(v: View) : RecyclerView.ViewHolder(v) {
        val image:  ImageView  = v.findViewById(R.id.ivBubble)
        val time:   TextView   = v.findViewById(R.id.tvTime)
        val sender: TextView?  = v.findViewById(R.id.tvSenderName)
        val status: ImageView? = v.findViewById(R.id.ivStatus)
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

    private fun bindStatus(iv: ImageView?, m: SmsMessage) {
        if (iv == null || m.isIncoming) { iv?.visibility = View.GONE; return }
        iv.visibility = View.VISIBLE
        val (drawable, tint) = when {
            m.id < 0                                   -> Pair(R.drawable.ic_status_pending,   0x80FFFFFF.toInt())
            isRcsConversation && m.deliveryStatus == 0 -> Pair(R.drawable.ic_status_delivered, 0xAAFFFFFF.toInt())
            else                                       -> Pair(R.drawable.ic_status_sent,      0xAAFFFFFF.toInt())
        }
        iv.setImageResource(drawable)
        iv.imageTintList = ColorStateList.valueOf(tint)
    }

    private fun decryptBody(ctx: Context, m: SmsMessage): String {
        if (!CryptoStore.isEncrypted(m.body)) return m.body
        val key = CryptoStore.getSessionKey(ctx, m.address) ?: return "🔒 Encrypted message"
        return CryptoStore.decrypt(m.body, key) ?: "🔒 Encrypted message"
    }

    private fun bindMsg(h: MsgVH, m: SmsMessage) {
        val ctx = h.itemView.context
        val body = decryptBody(ctx, m)
        h.bubble.text = body
        h.time.text   = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(m.timestamp))
        bindSender(h.sender, m)
        bindStatus(h.status, m)
        // File attachment (non-image, non-video, non-voice MMS) → tap to open
        val isFileMms = m.isMms && m.mediaUri != null && !m.isVoice &&
            m.mimeType?.startsWith("image/") == false &&
            m.mimeType?.startsWith("video/") == false
        if (isFileMms) {
            h.bubble.setOnClickListener {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(m.mediaUri), m.mimeType ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                } catch (_: Exception) {
                    Toast.makeText(ctx, "No app to open this file", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            h.bubble.isClickable = false
        }
        // Long-press on both bubble and item row so it fires regardless of which view consumes touch
        val longClick = View.OnLongClickListener { showMenu(it.context, m, body); true }
        h.bubble.setOnLongClickListener(longClick)
        h.itemView.setOnLongClickListener(longClick)
        if (m.isVoice && m.mediaUri != null) {
            h.btnPlay?.setOnClickListener { btn -> playAudio(btn.context, m.mediaUri) }
            h.tvTranscript?.visibility = View.GONE
            h.btnTranscript?.let { btn ->
                btn.visibility = View.VISIBLE
                btn.text = if (m.isIncoming) " · Transcript" else "Transcript · "
                btn.isClickable = true
                btn.setOnClickListener { v ->
                    btn.text = if (m.isIncoming) " · …" else "… · "
                    btn.isClickable = false
                    AudioTranscriber.transcribe(v.context, m.mediaUri) { result ->
                        if (result != null) {
                            btn.visibility = View.GONE
                            h.tvTranscript?.text = "“$result”"
                            h.tvTranscript?.visibility = View.VISIBLE
                        } else {
                            btn.text = if (m.isIncoming) " · Transcript" else "Transcript · "
                            btn.isClickable = true
                            Toast.makeText(v.context, "Speech recognition unavailable on this device", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } else {
            h.btnTranscript?.visibility = View.GONE
            h.tvTranscript?.visibility = View.GONE
        }
    }

    private fun bindImage(h: ImageVH, m: SmsMessage) {
        h.time.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(m.timestamp))
        bindSender(h.sender, m)
        bindStatus(h.status, m)
        val isVideo = m.mimeType?.startsWith("video/") == true
        if (isVideo) {
            h.image.setImageResource(android.R.drawable.ic_media_play)
            h.image.setOnClickListener { openMedia(it.context, m.mediaUri, m.mimeType) }
        } else {
            m.mediaUri?.let { uri -> loadImageAsync(h.image, uri) }
            h.image.setOnClickListener { ctx ->
                m.mediaUri?.let { showFullscreen(ctx.context, Uri.parse(it)) }
            }
        }
        // Long-press on the image itself (the click listener above makes h.image intercept all
        // touch events, so putting long-click on h.itemView would never fire)
        h.image.setOnLongClickListener { showMediaMenu(it.context, m); true }
        h.itemView.setOnLongClickListener { showMediaMenu(it.context, m); true }
    }

    private fun showFullscreen(ctx: Context, uri: Uri) {
        val dialog = Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val iv = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            try { setImageURI(uri) } catch (_: Exception) { setImageResource(android.R.drawable.ic_menu_gallery) }
            setOnClickListener { dialog.dismiss() }
        }
        dialog.setContentView(iv)
        dialog.show()
    }

    private fun showMenu(ctx: Context, m: SmsMessage, displayBody: String = m.body) {
        val opts = mutableListOf<String>()
        if (displayBody.isNotBlank()) opts += "Copy text"
        opts += "Forward"
        opts += "Delete message"
        AlertDialog.Builder(ctx)
            .setItems(opts.toTypedArray()) { _, i ->
                when (opts[i]) {
                    "Copy text" -> {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("msg", displayBody))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    }
                    "Forward" -> onForward?.invoke(m)
                    "Delete message" -> {
                        AlertDialog.Builder(ctx)
                            .setMessage("Delete this message?")
                            .setPositiveButton("Delete") { _, _ -> onDelete?.invoke(m) }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showMediaMenu(ctx: Context, m: SmsMessage) {
        val isVideo = m.mimeType?.startsWith("video/") == true
        val opts = mutableListOf("Download", "Forward")
        if (!isVideo) opts += "Copy image"
        opts += "Delete"
        AlertDialog.Builder(ctx)
            .setItems(opts.toTypedArray()) { _, i ->
                when (opts[i]) {
                    "Download"   -> downloadMedia(ctx, m)
                    "Forward"    -> onForward?.invoke(m)
                    "Copy image" -> copyImageToClipboard(ctx, m)
                    "Delete"     -> AlertDialog.Builder(ctx)
                        .setMessage("Delete this message?")
                        .setPositiveButton("Delete") { _, _ -> onDelete?.invoke(m) }
                        .setNegativeButton("Cancel", null).show()
                }
            }.show()
    }

    private fun downloadMedia(ctx: Context, m: SmsMessage) {
        val uriStr   = m.mediaUri ?: return
        val mimeType = m.mimeType ?: "image/jpeg"
        val isVideo  = mimeType.startsWith("video/")
        val ext = when {
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
            mimeType.contains("png")  -> "png"
            mimeType.contains("gif")  -> "gif"
            mimeType.contains("mp4")  -> "mp4"
            mimeType.contains("3gp")  -> "3gp"
            else -> mimeType.substringAfter("/").take(8)
        }
        Thread {
            try {
                val displayName = "NexLink_${System.currentTimeMillis()}.$ext"
                val collection  = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else         MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else         MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/NexLink" else "Pictures/NexLink")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                val destUri = ctx.contentResolver.insert(collection, cv) ?: return@Thread
                ctx.contentResolver.openInputStream(Uri.parse(uriStr))?.use { input ->
                    ctx.contentResolver.openOutputStream(destUri)?.use { it.write(input.readBytes()) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ctx.contentResolver.update(destUri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                }
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(ctx, "Saved to ${if (isVideo) "Movies" else "Pictures"}/NexLink", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(ctx, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun copyImageToClipboard(ctx: Context, m: SmsMessage) {
        val uri = m.mediaUri ?: return
        try {
            val clip = ClipData.newUri(ctx.contentResolver, "Image", Uri.parse(uri))
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            Toast.makeText(ctx, "Image copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadImageAsync(iv: ImageView, uriStr: String) {
        iv.setImageResource(android.R.drawable.ic_menu_gallery)
        val uri = Uri.parse(uriStr)
        Thread {
            try {
                val bmp = iv.context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
                iv.post { if (bmp != null) iv.setImageBitmap(bmp) }
            } catch (_: Exception) {}
        }.start()
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
