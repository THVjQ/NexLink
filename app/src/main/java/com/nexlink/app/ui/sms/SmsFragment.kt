package com.nexlink.app.ui.sms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexlink.app.R
import com.nexlink.app.databinding.FragmentSmsBinding
import com.nexlink.app.db.Conversation
import com.nexlink.app.db.SmsHelper

class SmsFragment : Fragment() {

    private var _b: FragmentSmsBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: ConversationAdapter
    private var allConversations = listOf<Conversation>()
    private var filterUnreadOnly = false

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { loadConversations() }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSmsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ConversationAdapter(
            onClick     = { conv -> openConversation(conv) },
            onLongClick = { conv -> confirmDeleteConversation(conv) }
        )
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.swipe.setOnRefreshListener { loadConversations() }
        b.swipe.setColorSchemeResources(R.color.accent)

        b.btnFilterUnread.setOnClickListener {
            filterUnreadOnly = !filterUnreadOnly
            updateFilterButton()
            filterConversations(b.etSearch.text?.toString() ?: "")
        }

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b2: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                b.btnClear.isVisible = q.isNotEmpty()
                filterConversations(q)
            }
        })

        b.btnClear.setOnClickListener {
            b.etSearch.text?.clear()
            b.etSearch.clearFocus()
        }

        b.fabNewChat.setOnClickListener { showNewChatMenu() }

        loadConversations()
    }

    override fun onResume() {
        super.onResume()
        requireContext().contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)
        loadConversations()
    }

    override fun onPause() {
        super.onPause()
        requireContext().contentResolver.unregisterContentObserver(smsObserver)
    }

    private fun updateFilterButton() {
        if (filterUnreadOnly) {
            b.btnFilterUnread.setBackgroundResource(R.drawable.bg_card_selected)
            b.btnFilterUnread.setTextColor(resources.getColor(R.color.accent, null))
        } else {
            b.btnFilterUnread.setBackgroundResource(R.drawable.bg_card_unselected)
            b.btnFilterUnread.setTextColor(resources.getColor(R.color.muted, null))
        }
    }

    private fun loadConversations() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) return
        val ctx = context ?: return
        b.swipe.isRefreshing = true
        Thread {
            val convs = SmsHelper.getConversations(ctx)
            if (!isAdded) return@Thread
            activity?.runOnUiThread {
                allConversations = convs
                filterConversations(b.etSearch.text?.toString() ?: "")
                b.swipe.isRefreshing = false
                val unread = convs.sumOf { it.unreadCount }
                b.tvUnread.text = if (unread > 0) "$unread unread" else "${convs.size} threads"
            }
        }.start()
    }

    private fun filterConversations(query: String) {
        var result = allConversations
        if (filterUnreadOnly) {
            val ctx = requireContext()
            result = result.filter { conv ->
                conv.unreadCount > 0 &&
                    !com.nexlink.app.db.ReadTracker.isLocallyRead(ctx, conv.address, conv.timestamp)
            }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter {
                it.contactName.lowercase().contains(q) ||
                it.address.contains(q) ||
                it.lastMessage.lowercase().contains(q)
            }
        }
        adapter.setData(result)
    }

    private fun openConversation(conv: Conversation) {
        startActivity(Intent(requireContext(), ConversationActivity::class.java).apply {
            putExtra("address", conv.address)
            putExtra("contact_name", conv.contactName)
            putExtra("thread_id", conv.threadId)
            putStringArrayListExtra("participants", ArrayList(conv.participants))
        })
    }

    private fun confirmDeleteConversation(conv: Conversation) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete conversation")
            .setMessage("Delete conversation with ${conv.contactName.ifBlank { conv.address }}?")
            .setPositiveButton("Delete") { _, _ ->
                Thread {
                    SmsHelper.deleteThread(requireContext(), conv.threadId)
                    loadConversations()
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── FAB — new chat / new group ─────────────────────────────────────────────

    private fun showNewChatMenu() {
        AlertDialog.Builder(requireContext())
            .setItems(arrayOf("New message", "New group chat")) { _, which ->
                if (which == 0) showContactPicker(multiSelect = false)
                else showContactPicker(multiSelect = true)
            }
            .show()
    }

    private fun showContactPicker(multiSelect: Boolean) {
        val ctx = context ?: return
        Thread {
            val contacts = loadContactsForPicker(ctx)
            if (!isAdded) return@Thread
            activity?.runOnUiThread {
                if (contacts.isEmpty()) {
                    Toast.makeText(ctx, "No contacts found", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val names = contacts.map { "${it.first}\n${it.second}" }.toTypedArray()
                if (!multiSelect) {
                    AlertDialog.Builder(ctx)
                        .setTitle("New message")
                        .setItems(names) { _, i ->
                            val (name, number) = contacts[i]
                            startActivity(Intent(ctx, ConversationActivity::class.java).apply {
                                putExtra("address", number)
                                putExtra("contact_name", name)
                            })
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    val checked = BooleanArray(contacts.size) { false }
                    AlertDialog.Builder(ctx)
                        .setTitle("New group")
                        .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
                        .setPositiveButton("Create") { _, _ ->
                            val selected = contacts.filterIndexed { i, _ -> checked[i] }
                            if (selected.size < 2) {
                                Toast.makeText(ctx, "Select at least 2 contacts for a group", Toast.LENGTH_SHORT).show()
                                return@setPositiveButton
                            }
                            val participants = selected.map { it.second }
                            val groupName   = selected.joinToString(", ") { it.first.split(" ").first() }
                            startActivity(Intent(ctx, ConversationActivity::class.java).apply {
                                putExtra("address", participants.first())
                                putExtra("contact_name", groupName)
                                putExtra("thread_id", 0L)
                                putStringArrayListExtra("participants", ArrayList(participants))
                            })
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }.start()
    }

    private fun loadContactsForPicker(ctx: android.content.Context): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val proj = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                           ContactsContract.CommonDataKinds.Phone.NUMBER)
        try {
            ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                proj, null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")
                ?.use { c ->
                    val seen = mutableSetOf<String>()
                    while (c.moveToNext()) {
                        val name = c.getString(0) ?: continue
                        val num  = c.getString(1)?.replace("\\s".toRegex(), "") ?: continue
                        if (num in seen) continue
                        seen += num
                        list += Pair(name, num)
                    }
                }
        } catch (_: Exception) {}
        return list
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
