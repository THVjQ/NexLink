package com.nexlink.app.ui.inbox

import android.os.Bundle
import android.view.*
import android.view.HapticFeedbackConstants
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexlink.app.R
import com.nexlink.app.databinding.FragmentInboxBinding
import com.nexlink.app.db.NotificationPrefs
import com.nexlink.app.db.NotificationStore
import com.nexlink.app.db.SocialNotification

class InboxFragment : Fragment() {

    private var _b: FragmentInboxBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: NotificationAdapter

    private val selectedPlatforms = mutableSetOf<String>()

    // Initialized in onViewCreated — NOT lazy, so view refs are always current
    private data class CardEntry(val card: View, val muteBtn: ImageButton, val color: Int)
    private var cardMap = emptyMap<String, CardEntry>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentInboxBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = NotificationAdapter()
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        // Build card map fresh every time the view is created — fixes stale view reference bug
        cardMap = mapOf(
            "Signal"    to CardEntry(b.cardSignal,    b.btnMuteSignal,    0xFF3a9bd5.toInt()),
            "Telegram"  to CardEntry(b.cardTelegram,  b.btnMuteTelegram,  0xFF229ed9.toInt()),
            "WhatsApp"  to CardEntry(b.cardWhatsapp,  b.btnMuteWhatsapp,  0xFF25d366.toInt()),
            "Messenger" to CardEntry(b.cardMessenger, b.btnMuteMessenger, 0xFF0099ff.toInt()),
            "Discord"   to CardEntry(b.cardDiscord,   b.btnMuteDiscord,   0xFF5865F2.toInt()),
            "Instagram" to CardEntry(b.cardInstagram, b.btnMuteInstagram, 0xFFE1306C.toInt()),
            "Steam"     to CardEntry(b.cardSteam,     b.btnMuteSteam,     0xFF1A9FFF.toInt())
        )

        // Apply platform visibility based on prefs
        applyPlatformVisibility()

        cardMap.forEach { (platform, entry) ->
            // Tap card body → toggle filter
            entry.card.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (platform in selectedPlatforms) selectedPlatforms.remove(platform)
                else selectedPlatforms.add(platform)
                updateCardVisuals()
                applyFilter(NotificationStore.notifications.value.orEmpty())
            }

            // Tap mute bell → toggle mute for this platform
            entry.muteBtn.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val ctx = requireContext()
                val nowMuted = !NotificationPrefs.isMuted(ctx, platform)
                NotificationPrefs.setMuted(ctx, platform, nowMuted)
                updateMuteVisuals()
            }
        }

        // Load saved mute state visuals
        updateMuteVisuals()

        NotificationStore.notifications.observe(viewLifecycleOwner) { applyFilter(it) }

        b.swipe.setOnRefreshListener {
            applyFilter(NotificationStore.notifications.value.orEmpty())
            b.swipe.isRefreshing = false
        }
        b.swipe.setColorSchemeResources(R.color.accent)
    }

    override fun onResume() {
        super.onResume()
        applyPlatformVisibility()
        updateMuteVisuals()
    }

    private fun applyPlatformVisibility() {
        val ctx = context ?: return
        cardMap.forEach { (platform, entry) ->
            entry.card.visibility =
                if (NotificationPrefs.isPlatformEnabled(ctx, platform)) View.VISIBLE else View.GONE
        }
    }

    private fun updateCardVisuals() {
        cardMap.forEach { (platform, entry) ->
            val isSelected = platform in selectedPlatforms
            entry.card.setBackgroundResource(
                if (isSelected) R.drawable.bg_card_selected else R.drawable.bg_card_unselected
            )
            entry.card.alpha = if (selectedPlatforms.isEmpty() || isSelected) 1f else 0.55f
        }
    }

    private fun updateMuteVisuals() {
        val ctx = context ?: return
        cardMap.forEach { (platform, entry) ->
            val isMuted = NotificationPrefs.isMuted(ctx, platform)
            entry.muteBtn.setImageResource(
                if (isMuted) R.drawable.ic_bell_off else R.drawable.ic_bell
            )
            entry.muteBtn.alpha = if (isMuted) 0.55f else 1f
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
            all.isEmpty()      -> "No messages yet — grant Notification Access in Settings"
            filtered.isEmpty() -> "No messages from $filterLabel"
            else               -> "${filtered.size} message${if (filtered.size != 1) "s" else ""} · $filterLabel"
        }
    }

    private fun updateBadges(all: List<SocialNotification>) {
        // Use unread counts from NotificationStore read-state
        val platformBadges = mapOf(
            "Signal"    to b.badgeSignal,
            "Telegram"  to b.badgeTelegram,
            "WhatsApp"  to b.badgeWhatsapp,
            "Messenger" to b.badgeMessenger,
            "Discord"   to b.badgeDiscord,
            "Instagram" to b.badgeInstagram,
            "Steam"     to b.badgeSteam
        )
        platformBadges.forEach { (platform, badge) ->
            // Sum unread across all senders for this platform
            val senders = all.filter { it.platform == platform }.map { it.sender }.toSet()
            val unread = senders.sumOf { sender ->
                NotificationStore.unreadCountFor(platform, sender)
            }
            if (unread > 0) {
                badge.text = unread.toString()
                badge.visibility = View.VISIBLE
            } else {
                badge.text = ""
                badge.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cardMap = emptyMap()
        _b = null
    }
}
