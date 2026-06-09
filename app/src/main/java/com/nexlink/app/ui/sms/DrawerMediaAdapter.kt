package com.nexlink.app.ui.sms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class DrawerMediaAdapter(
    private val uris: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<DrawerMediaAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val image: ImageView = v.findViewById(android.R.id.icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val iv = ImageView(parent.context).apply {
            val size = (parent.width / 3).coerceAtLeast(80)
            layoutParams = RecyclerView.LayoutParams(size, size).apply { setMargins(2, 2, 2, 2) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            id = android.R.id.icon
        }
        return VH(iv)
    }

    override fun getItemCount() = uris.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val uri = uris[pos]
        try {
            h.image.setImageURI(android.net.Uri.parse(uri))
        } catch (_: Exception) {
            h.image.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        h.image.setOnClickListener { onClick(uri) }
    }
}
