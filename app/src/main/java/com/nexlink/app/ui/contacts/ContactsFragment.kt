package com.nexlink.app.ui.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexlink.app.R
import com.nexlink.app.databinding.FragmentContactsBinding
import com.nexlink.app.db.DeepLinkHelper
import com.nexlink.app.ui.sms.ConversationActivity

data class Contact(val name: String, val number: String, val lookupKey: String = "")

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
            onSms    = { c -> openSms(c) },
            onCall   = { c -> placeCall(c) },
            onDelete = { c -> confirmDeleteContact(c) }
        )
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        // Swipe gestures: right = edit, left = delete
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            private val deleteBg   = ColorDrawable(Color.parseColor("#B71C1C"))
            private val editBg     = ColorDrawable(Color.parseColor("#1565C0"))

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                 t: RecyclerView.ViewHolder) = false

            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                return if (vh is ContactsAdapter.ContactVH) super.getSwipeDirs(rv, vh) else 0
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos     = vh.adapterPosition
                val contact = adapter.getContactAt(pos)
                adapter.notifyItemChanged(pos) // restore item after swipe
                contact ?: return
                if (direction == ItemTouchHelper.RIGHT) editContact(contact)
                else confirmDeleteContact(contact)
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                      dX: Float, dY: Float, actionState: Int, isActive: Boolean) {
                val v = vh.itemView
                if (dX > 0) {
                    editBg.setBounds(v.left, v.top, v.left + dX.toInt(), v.bottom)
                    editBg.draw(c)
                    ContextCompat.getDrawable(v.context, R.drawable.ic_edit)?.let { icon ->
                        icon.setTint(Color.WHITE)
                        val margin = (v.height - icon.intrinsicHeight) / 2
                        icon.setBounds(v.left + margin, v.top + margin,
                            v.left + margin + icon.intrinsicWidth, v.bottom - margin)
                        icon.draw(c)
                    }
                } else if (dX < 0) {
                    deleteBg.setBounds(v.right + dX.toInt(), v.top, v.right, v.bottom)
                    deleteBg.draw(c)
                    ContextCompat.getDrawable(v.context, R.drawable.ic_delete)?.let { icon ->
                        icon.setTint(Color.WHITE)
                        val margin = (v.height - icon.intrinsicHeight) / 2
                        icon.setBounds(v.right - margin - icon.intrinsicWidth, v.top + margin,
                            v.right - margin, v.bottom - margin)
                        icon.draw(c)
                    }
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(b.recycler)

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

        b.fabAddContact.setOnClickListener {
            startActivity(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI))
        }

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
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
            )
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                proj, null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { c ->
                val seen = mutableSetOf<String>()
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val num  = c.getString(1)?.replace("\\s".toRegex(), "") ?: continue
                    if (num in seen) continue
                    seen += num
                    list += Contact(name, num, c.getString(2) ?: "")
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

    private fun editContact(c: Contact) {
        if (c.lookupKey.isBlank()) {
            Toast.makeText(requireContext(), "Cannot edit this contact", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = ContactsContract.Contacts.CONTENT_LOOKUP_URI.buildUpon().appendPath(c.lookupKey).build()
        startActivity(Intent(Intent.ACTION_EDIT, uri))
    }

    private fun confirmDeleteContact(c: Contact) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete contact")
            .setMessage("Delete ${c.name} from contacts?")
            .setPositiveButton("Delete") { _, _ ->
                if (c.lookupKey.isNotBlank()) {
                    Thread {
                        val uri = ContactsContract.Contacts.CONTENT_LOOKUP_URI
                            .buildUpon().appendPath(c.lookupKey).build()
                        try { requireContext().contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                        loadContacts()
                    }.start()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun placeCall(c: Contact) {
        val ctx = requireContext()
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        options += "📞  Phone call"
        actions += {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${c.number}")))
            }
        }

        if (ctx.packageManager.getLaunchIntentForPackage("com.whatsapp") != null) {
            options += "🟢  WhatsApp"
            actions += { DeepLinkHelper.whatsApp(ctx, c.number) }
        }
        if (ctx.packageManager.getLaunchIntentForPackage("org.telegram.messenger") != null) {
            options += "✈️  Telegram"
            actions += { DeepLinkHelper.telegram(ctx, c.number) }
        }
        if (ctx.packageManager.getLaunchIntentForPackage("org.thoughtcrime.securesms") != null) {
            options += "🔵  Signal"
            actions += { DeepLinkHelper.signal(ctx) }
        }

        AlertDialog.Builder(ctx)
            .setTitle("Call ${c.name} via…")
            .setItems(options.toTypedArray()) { _, i -> actions[i]() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
