package com.nexlink.app.ui.sms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexlink.app.R
import com.nexlink.app.ui.NexPopup
import com.nexlink.app.ui.contacts.Contact
import com.nexlink.shared.AvatarColors

class ContactPickerSheet(
    private val multiSelect: Boolean,
    private val onSinglePick: (contact: Contact, number: String) -> Unit,
    private val onGroupPick: ((List<Contact>) -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var allContacts = listOf<Contact>()
    private val selectedKeys   = mutableSetOf<String>()
    private val manualNumbers  = mutableListOf<String>() // numbers typed that aren't in contacts

    private fun looksLikeNumber(q: String): Boolean {
        val digits = q.filter { it.isDigit() }
        return digits.length >= 4 && q.matches(Regex("[+\\d\\s()\\-.]+"))
    }

    override fun getTheme() = R.style.DialerSheet

    override fun onCreateView(inf: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inf.inflate(R.layout.fragment_contact_picker, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvTitle        = view.findViewById<TextView>(R.id.tvPickerTitle)
        val tvCount        = view.findViewById<TextView>(R.id.tvSelectedCount)
        val etSearch       = view.findViewById<EditText>(R.id.etPickerSearch)
        val btnClear       = view.findViewById<ImageButton>(R.id.btnPickerClearSearch)
        val recycler       = view.findViewById<RecyclerView>(R.id.pickerRecycler)
        val btnDone        = view.findViewById<View>(R.id.btnPickerDone)
        val bannerUseNum   = view.findViewById<View>(R.id.bannerUseNumber)
        val tvUseNumLabel  = view.findViewById<TextView>(R.id.tvUseNumberLabel)
        val tvUseNumValue  = view.findViewById<TextView>(R.id.tvUseNumberValue)
        val dividerUseNum  = view.findViewById<View>(R.id.dividerUseNumber)

        tvTitle.text      = if (multiSelect) "New group" else "New message"
        btnDone.isVisible = multiSelect

        fun totalSelected() = selectedKeys.size + manualNumbers.size

        var adapter: PickerAdapter? = null
        adapter = PickerAdapter(
            multiSelect  = multiSelect,
            selectedKeys = selectedKeys,
            onTap        = { contact, anchor ->
                if (!multiSelect) {
                    if (contact.numbers.size <= 1) {
                        onSinglePick(contact, contact.number)
                        dismiss()
                    } else {
                        NexPopup.show(anchor, contact.numbers.map { num ->
                            NexPopup.Item(num, R.drawable.ic_sms) {
                                onSinglePick(contact, num)
                                dismiss()
                            }
                        })
                    }
                } else {
                    val key = contact.lookupKey.ifBlank { contact.number }
                    if (key in selectedKeys) selectedKeys.remove(key) else selectedKeys.add(key)
                    val n = totalSelected()
                    tvCount.text = if (n == 0) "" else "$n selected"
                    tvCount.isVisible = n > 0
                    adapter?.notifyDataSetChanged()
                }
            }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        // "Use this number" banner — tap to start chat (single) or add to group (multi)
        bannerUseNum.setOnClickListener {
            val num = etSearch.text?.toString()?.trim() ?: return@setOnClickListener
            if (!looksLikeNumber(num)) return@setOnClickListener
            val normalised = num.replace("\\s".toRegex(), "")
            if (!multiSelect) {
                val synthetic = Contact(normalised, normalised, listOf(normalised))
                onSinglePick(synthetic, normalised)
                dismiss()
            } else {
                if (normalised !in manualNumbers) {
                    manualNumbers.add(normalised)
                    val n = totalSelected()
                    tvCount.text    = "$n selected"
                    tvCount.isVisible = true
                }
                etSearch.text?.clear()
            }
        }

        btnDone.setOnClickListener {
            val contactPicks = allContacts.filter {
                it.lookupKey.ifBlank { it.number } in selectedKeys
            }
            val manualPicks = manualNumbers.map { Contact(it, it, listOf(it)) }
            val all = contactPicks + manualPicks
            if (all.isNotEmpty()) { onGroupPick?.invoke(all); dismiss() }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                btnClear.isVisible = q.isNotEmpty()
                val isNum = looksLikeNumber(q)
                bannerUseNum.isVisible  = isNum
                dividerUseNum.isVisible = isNum
                if (isNum) {
                    tvUseNumLabel.text = if (multiSelect) "Add this number to group" else "Message this number"
                    tvUseNumValue.text = q.trim()
                }
                adapter?.let { applyFilter(q, it) }
            }
        })
        btnClear.setOnClickListener { etSearch.text?.clear() }

        adapter?.let { loadContacts(it) }
    }

    private fun applyFilter(query: String, adapter: PickerAdapter) {
        val q = query.trim().lowercase()
        adapter.setData(if (q.isEmpty()) allContacts
        else allContacts.filter {
            it.name.lowercase().contains(q) || it.number.contains(q)
        })
    }

    private fun loadContacts(adapter: PickerAdapter) {
        val ctx = context ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return
        Thread {
            val grouped = linkedMapOf<String, Pair<String, MutableList<String>>>()
            try {
                ctx.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
                    ),
                    null, null,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                )?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(0) ?: continue
                        val num  = c.getString(1)?.replace("\\s".toRegex(), "") ?: continue
                        val key  = c.getString(2)?.takeIf { it.isNotBlank() } ?: num
                        val entry = grouped.getOrPut(key) { Pair(name, mutableListOf()) }
                        if (num !in entry.second) entry.second.add(num)
                    }
                }
            } catch (_: Exception) {}
            val contacts = grouped.entries.map { (key, pair) ->
                Contact(pair.first, pair.second.first(), pair.second, key)
            }
            if (!isAdded) return@Thread
            activity?.runOnUiThread {
                allContacts = contacts
                adapter.setData(contacts)
            }
        }.start()
    }

    // ── Inner adapter ─────────────────────────────────────────────────────────

    class PickerAdapter(
        private val multiSelect: Boolean,
        private val selectedKeys: Set<String>,
        private val onTap: (Contact, View) -> Unit
    ) : RecyclerView.Adapter<PickerAdapter.VH>() {

        private var items = listOf<Contact>()
        fun setData(c: List<Contact>) { items = c; notifyDataSetChanged() }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val avatar: TextView  = v.findViewById(R.id.tvPickAvatar)
            val name:   TextView  = v.findViewById(R.id.tvPickName)
            val number: TextView  = v.findViewById(R.id.tvPickNumber)
            val check:  ImageView = v.findViewById(R.id.ivPickCheck)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_contact_pick, p, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val c = items[pos]
            val initials = c.name.split(" ").take(2)
                .joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
            h.avatar.background.mutate().setTint(AvatarColors.colorFor(initials))
            h.avatar.text   = initials
            h.name.text     = c.name
            h.number.text   = if (c.numbers.size > 1)
                "${c.number}  +${c.numbers.size - 1} more" else c.number

            val key = c.lookupKey.ifBlank { c.number }
            val isSelected = key in selectedKeys
            if (multiSelect) {
                h.check.isVisible = true
                h.check.setImageResource(
                    if (isSelected) R.drawable.ic_check else R.drawable.ic_add
                )
                h.check.alpha = if (isSelected) 1f else 0.25f
            } else {
                h.check.isVisible = false
            }

            h.itemView.setOnClickListener { onTap(c, it) }
        }
    }
}
