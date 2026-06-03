package com.nexlink.app.ui.sms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.view.*
import androidx.core.content.ContextCompat
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

    private fun loadConversations() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) return
        val ctx = context ?: return
        b.swipe.isRefreshing = true
        Thread {
            val convs = SmsHelper.getConversations(ctx)
            if (!isAdded) return@Thread
            activity?.runOnUiThread {
                adapter.setData(convs)
                b.swipe.isRefreshing = false
                b.tvUnread.text = convs.sumOf { it.unreadCount }.let {
                    if (it > 0) "$it unread" else "${convs.size} threads"
                }
            }
        }.start()
    }

    private fun openConversation(conv: Conversation) {
        startActivity(Intent(requireContext(), ConversationActivity::class.java).apply {
            putExtra("address", conv.address)
            putExtra("contact_name", conv.contactName)
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
