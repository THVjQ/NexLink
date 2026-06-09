package com.nexlink.app.ui.sms

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class DrawerMediaAdapter(
    private val uris: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<DrawerMediaAdapter.VH>() {

    inner class VH(val image: ImageView) : RecyclerView.ViewHolder(image)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val size = (parent.width / 3).coerceAtLeast(80)
        val iv = ImageView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(size, size).apply { setMargins(2, 2, 2, 2) }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        return VH(iv)
    }

    override fun getItemCount() = uris.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val uri = uris[pos]
        h.image.setImageResource(android.R.drawable.ic_menu_gallery)
        val parsedUri = Uri.parse(uri)
        Thread {
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = h.image.context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
                h.image.post { if (bmp != null) h.image.setImageBitmap(bmp) }
            } catch (_: Exception) {}
        }.start()
        h.image.setOnClickListener { onClick(uri) }
    }
}
