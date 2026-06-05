package com.nexlink.app.ui.sms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.text.Editable
import android.text.TextWatcher
import android.view.*
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

        adapter = ConversationAdapter { conv -> openConversation(conv) }
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.swipe.setOnRefreshListener { loadConversations() }
        b.swipe.setColorSchemeResources(R.color.accent)

        // Unread filter toggle
        b.btnFilterUnread.setOnClickListener {
            filterUnreadOnly = !filterUnreadOnly
            updateFilterButton()
            filterConversations(b.etSearch.text?.toString() ?: "")
        }

        // Search
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
            // Match the same "effectively read" logic used by ConversationAdapter
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
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
