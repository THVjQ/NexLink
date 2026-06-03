package com.nexlink.app.ui.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexlink.app.R
import com.nexlink.app.databinding.FragmentContactsBinding
import com.nexlink.app.ui.sms.ConversationActivity

data class Contact(val name: String, val number: String)

class ContactsFragment : Fragment() {

    private var _b: FragmentContactsBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: ContactsAdapter
    private var allContacts = listOf<Contact>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentContactsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ContactsAdapter(
            onSms  = { c -> openSms(c) },
            onCall = { c -> placeCall(c) }
        )
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b2: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                b.btnClear.isVisible = q.isNotEmpty()
                filter(q)
            }
        })
        b.btnClear.setOnClickListener { b.etSearch.text?.clear() }

        loadContacts()
    }

    override fun onResume() { super.onResume(); loadContacts() }

    private fun loadContacts() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return
        val ctx = context ?: return
        Thread {
            val list = mutableListOf<Contact>()
            val proj = arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            ctx.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                proj, null, null,
                "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { c ->
                val seen = mutableSetOf<String>()
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val num  = c.getString(1)?.replace("\\s".toRegex(), "") ?: continue
                    if (num in seen) continue
                    seen += num
                    list += Contact(name, num)
                }
            }
            if (!isAdded) return@Thread
            activity?.runOnUiThread {
                allContacts = list
                filter(b.etSearch.text?.toString() ?: "")
                b.tvCount.text = "${list.size} contacts"
            }
        }.start()
    }

    private fun filter(q: String) {
        val filtered = if (q.isBlank()) allContacts
        else allContacts.filter {
            it.name.lowercase().contains(q.lowercase()) || it.number.contains(q)
        }
        adapter.setData(filtered)
    }

    private fun openSms(c: Contact) {
        startActivity(Intent(requireContext(), ConversationActivity::class.java).apply {
            putExtra("address", c.number)
            putExtra("contact_name", c.name)
        })
    }

    private fun placeCall(c: Contact) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) return
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${c.number}")))
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
