package com.nexlink.app.ui.contacts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexlink.app.R

class ContactsAdapter(
    private val onSms:  (Contact) -> Unit,
    private val onCall: (Contact) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.VH>() {

    private var items = listOf<Contact>()
    fun setData(data: List<Contact>) { items = data; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar:  TextView    = v.findViewById(R.id.tvAvatar)
        val name:    TextView    = v.findViewById(R.id.tvName)
        val number:  TextView    = v.findViewById(R.id.tvNumber)
        val btnSms:  ImageButton = v.findViewById(R.id.btnSms)
        val btnCall: ImageButton = v.findViewById(R.id.btnCall)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = items[pos]
        val initials = c.name.split(" ").take(2).joinToString("") { it.take(1).uppercase() }
        h.avatar.text   = initials
        h.name.text     = c.name
        h.number.text   = c.number
        h.btnSms.setOnClickListener  { onSms(c) }
        h.btnCall.setOnClickListener { onCall(c) }
        h.itemView.setOnClickListener { onSms(c) }
    }
}
