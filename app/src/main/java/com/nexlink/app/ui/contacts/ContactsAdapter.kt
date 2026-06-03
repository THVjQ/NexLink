package com.nexlink.app.ui.contacts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexlink.app.R

sealed class ContactListItem {
    data class Header(val letter: String) : ContactListItem()
    data class Item(val contact: Contact) : ContactListItem()
}

class ContactsAdapter(
    private val onSms:  (Contact) -> Unit,
    private val onCall: (Contact) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items = listOf<ContactListItem>()

    fun setData(contacts: List<Contact>) {
        val sorted = contacts.sortedBy { it.name.lowercase() }
        val built  = mutableListOf<ContactListItem>()
        var lastLetter = ""
        for (c in sorted) {
            val letter = c.name.firstOrNull()?.uppercaseChar()?.toString()
                ?.takeIf { it[0].isLetter() } ?: "#"
            if (letter != lastLetter) {
                built += ContactListItem.Header(letter)
                lastLetter = letter
            }
            built += ContactListItem.Item(c)
        }
        items = built
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ContactListItem.Header -> 0
        is ContactListItem.Item   -> 1
    }

    override fun getItemCount() = items.size

    // ── Header ViewHolder ──
    inner class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        val letter: TextView = v.findViewById(R.id.tvLetter)
    }

    // ── Contact ViewHolder ──
    inner class ContactVH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar:  TextView    = v.findViewById(R.id.tvAvatar)
        val name:    TextView    = v.findViewById(R.id.tvName)
        val number:  TextView    = v.findViewById(R.id.tvNumber)
        val btnSms:  ImageButton = v.findViewById(R.id.btnSms)
        val btnCall: ImageButton = v.findViewById(R.id.btnCall)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 0)
            HeaderVH(inf.inflate(R.layout.item_contact_header, parent, false))
        else
            ContactVH(inf.inflate(R.layout.item_contact, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ContactListItem.Header -> (holder as HeaderVH).letter.text = item.letter
            is ContactListItem.Item   -> {
                val h = holder as ContactVH
                val c = item.contact
                val initials = c.name.split(" ").take(2)
                    .joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
                h.avatar.text  = initials
                h.name.text    = c.name
                h.number.text  = c.number
                h.btnSms.setOnClickListener  { onSms(c) }
                h.btnCall.setOnClickListener { onCall(c) }
                h.itemView.setOnClickListener { onSms(c) }
            }
        }
    }
}
