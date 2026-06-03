package com.nexlink.app.ui.inbox

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexlink.app.R
import com.nexlink.app.databinding.FragmentInboxBinding
import com.nexlink.app.db.NotificationStore
import com.nexlink.app.db.SocialNotification

class InboxFragment : Fragment() {

    private var _b: FragmentInboxBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: NotificationAdapter
    private var currentFilter = "all"

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentInboxBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = NotificationAdapter()
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        // Filter chips
        b.chipAll.setOnClickListener       { setFilter("all") }
        b.chipSignal.setOnClickListener    { setFilter("Signal") }
        b.chipTelegram.setOnClickListener  { setFilter("Telegram") }
        b.chipWhatsapp.setOnClickListener  { setFilter("WhatsApp") }
        b.chipMessenger.setOnClickListener { setFilter("Messenger") }

        // App card taps
        b.cardSignal.setOnClickListener    { setFilter("Signal") }
        b.cardTelegram.setOnClickListener  { setFilter("Telegram") }
        b.cardWhatsapp.setOnClickListener  { setFilter("WhatsApp") }
        b.cardMessenger.setOnClickListener { setFilter("Messenger") }

        NotificationStore.notifications.observe(viewLifecycleOwner) { applyFilter(it) }

        b.swipe.setOnRefreshListener {
            applyFilter(NotificationStore.notifications.value.orEmpty())
            b.swipe.isRefreshing = false
        }
        b.swipe.setColorSchemeResources(R.color.accent)
    }

    private fun setFilter(platform: String) {
        currentFilter = platform
        listOf(b.chipAll, b.chipSignal, b.chipTelegram, b.chipWhatsapp, b.chipMessenger).forEach {
            it.isSelected = false
        }
        when (platform) {
            "all"       -> b.chipAll.isSelected = true
            "Signal"    -> b.chipSignal.isSelected = true
            "Telegram"  -> b.chipTelegram.isSelected = true
            "WhatsApp"  -> b.chipWhatsapp.isSelected = true
            "Messenger" -> b.chipMessenger.isSelected = true
        }
        applyFilter(NotificationStore.notifications.value.orEmpty())
    }

    private fun applyFilter(all: List<SocialNotification>) {
        val filtered = if (currentFilter == "all") all
                       else all.filter { it.platform == currentFilter }
        adapter.setData(filtered)
        updateBadges(all)
        b.tvSub.text = if (filtered.isEmpty()) "No messages yet — grant Notification Access in Settings"
                       else "${all.size} unread · all platforms"
    }

    private fun updateBadges(all: List<SocialNotification>) {
        fun count(p: String) = all.count { it.platform == p }
        b.badgeSignal.text    = count("Signal").takeIf { it > 0 }?.toString() ?: ""
        b.badgeTelegram.text  = count("Telegram").takeIf { it > 0 }?.toString() ?: ""
        b.badgeWhatsapp.text  = count("WhatsApp").takeIf { it > 0 }?.toString() ?: ""
        b.badgeMessenger.text = count("Messenger").takeIf { it > 0 }?.toString() ?: ""
        listOf(b.badgeSignal, b.badgeTelegram, b.badgeWhatsapp, b.badgeMessenger).forEach {
            it.visibility = if (it.text.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
