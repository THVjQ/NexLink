package com.nexlink.app.ui.inbox

import android.os.Bundle
import android.view.*
import android.view.HapticFeedbackConstants
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

    // Set of currently active filter platforms (empty = show all)
    private val selectedPlatforms = mutableSetOf<String>()

    // Map platform name → its card view and platform colour
    private val cardInfo by lazy {
        mapOf(
            "Signal"    to Pair(b.cardSignal,    0xFF3a9bd5.toInt()),
            "Telegram"  to Pair(b.cardTelegram,  0xFF229ed9.toInt()),
            "WhatsApp"  to Pair(b.cardWhatsapp,  0xFF25d366.toInt()),
            "Messenger" to Pair(b.cardMessenger, 0xFF0099ff.toInt())
        )
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentInboxBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = NotificationAdapter()
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        // Wire up card taps — toggle filter on each tap
        cardInfo.forEach { (platform, pair) ->
            pair.first.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (platform in selectedPlatforms) {
                    selectedPlatforms.remove(platform)
                } else {
                    selectedPlatforms.add(platform)
                }
                updateCardVisuals()
                applyFilter(NotificationStore.notifications.value.orEmpty())
            }
        }

        NotificationStore.notifications.observe(viewLifecycleOwner) { applyFilter(it) }

        b.swipe.setOnRefreshListener {
            applyFilter(NotificationStore.notifications.value.orEmpty())
            b.swipe.isRefreshing = false
        }
        b.swipe.setColorSchemeResources(R.color.accent)
    }

    private fun updateCardVisuals() {
        cardInfo.forEach { (platform, pair) ->
            val (card, _) = pair
            val isSelected = platform in selectedPlatforms
            card.setBackgroundResource(
                if (isSelected) R.drawable.bg_card_selected
                else R.drawable.bg_card_unselected
            )
            card.alpha = if (selectedPlatforms.isEmpty() || isSelected) 1f else 0.55f
        }
    }

    private fun applyFilter(all: List<SocialNotification>) {
        val filtered = when {
            selectedPlatforms.isEmpty() -> all
            else -> all.filter { it.platform in selectedPlatforms }
        }
        adapter.setData(filtered)
        updateBadges(all)

        val filterLabel = when {
            selectedPlatforms.isEmpty() -> "all platforms"
            selectedPlatforms.size == 1 -> selectedPlatforms.first()
            else -> selectedPlatforms.joinToString(" + ")
        }
        b.tvSub.text = when {
            all.isEmpty() -> "No messages yet — grant Notification Access in Settings"
            filtered.isEmpty() -> "No messages from $filterLabel"
            else -> "${filtered.size} message${if (filtered.size != 1) "s" else ""} · $filterLabel"
        }
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
