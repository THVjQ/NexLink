package com.nexlink.app.services

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nexlink.app.db.NotificationPrefs
import com.nexlink.app.db.NotificationStore
import com.nexlink.app.db.SocialNotification

/**
 * Captures OUTGOING messages — the ones you send — which never raise a notification
 * on your own phone and so are invisible to NexLinkNotificationListener.
 *
 * Reads right-aligned (outgoing) bubbles currently visible on screen while you're
 * inside a chat in one of the watched apps, then writes them into NotificationStore
 * with outgoing=true so the inbox can show both sides of the thread.
 *
 * Enable: Settings → Accessibility → NexLink → On.
 */
class NexLinkAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in NotificationStore.watchedPackages) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val platform = NotificationStore.platform(pkg)
        if (!NotificationPrefs.isPlatformEnabled(applicationContext, platform)) return

        val root = rootInActiveWindow ?: return
        val screenWidth = resources.displayMetrics.widthPixels.takeIf { it > 0 } ?: return

        val title = extractChatTitle(root) ?: return
        val outgoing = extractOutgoingLines(pkg, root, screenWidth)
        if (outgoing.isEmpty()) return

        val now = System.currentTimeMillis()
        for (body in outgoing) {
            if (isRecentDuplicate(platform, title, body, now)) continue
            NotificationStore.add(
                SocialNotification(
                    id          = "out_${platform}_${title}_${body.hashCode()}",
                    platform    = platform,
                    packageName = pkg,
                    sender      = title,
                    text        = body,
                    timestamp   = now,
                    isReaction  = false,
                    outgoing    = true
                ),
                applicationContext
            )
        }
    }

    override fun onInterrupt() {}

    private fun extractOutgoingLines(pkg: String, root: AccessibilityNodeInfo, screenWidth: Int): List<String> {
        val candidates = collectMessageNodes(pkg, root)
        val out = mutableListOf<String>()
        val rect = Rect()
        val rightZone = screenWidth * 0.42f

        for (node in candidates) {
            val text = node.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) continue
            if (node.className?.contains("EditText") == true) continue
            node.getBoundsInScreen(rect)
            if (rect.width() <= 0) continue
            if (rect.left.toFloat() >= rightZone) out.add(text)
        }
        return out.distinct()
    }

    private fun collectMessageNodes(pkg: String, root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        MESSAGE_TEXT_IDS[pkg]?.let { id ->
            val byId = root.findAccessibilityNodeInfosByViewId(id)
            if (!byId.isNullOrEmpty()) return byId
        }
        val list = findScrollable(root) ?: root
        return collectTextViews(list)
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            val cls = n.className?.toString().orEmpty()
            if (n.isScrollable || cls.contains("RecyclerView") || cls.contains("ListView")) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
        return null
    }

    private fun collectTextViews(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n.className?.contains("TextView") == true && !n.text.isNullOrBlank()) result.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
        return result
    }

    private fun extractChatTitle(root: AccessibilityNodeInfo): String? {
        TITLE_IDS.firstNotNullOfOrNull { id ->
            root.findAccessibilityNodeInfosByViewId(id)
                ?.firstOrNull { !it.text.isNullOrBlank() }
                ?.text?.toString()
        }?.let { return it }

        val rect = Rect()
        return collectTextViews(root).firstOrNull {
            it.getBoundsInScreen(rect); rect.top < resources.displayMetrics.heightPixels * 0.15
        }?.text?.toString()
    }

    private fun isRecentDuplicate(platform: String, thread: String, body: String, now: Long): Boolean {
        return NotificationStore.notifications.value.orEmpty().any {
            it.outgoing && it.platform == platform && it.sender == thread &&
                it.text == body && now - it.timestamp < DEDUP_WINDOW_MS
        }
    }

    companion object {
        private const val DEDUP_WINDOW_MS = 10 * 60 * 1000L

        private val MESSAGE_TEXT_IDS = mapOf(
            "com.whatsapp"               to "com.whatsapp:id/message_text",
            "org.thoughtcrime.securesms" to "org.thoughtcrime.securesms:id/conversation_item_body",
            "org.telegram.messenger"     to "org.telegram.messenger:id/chat_message_text"
        )

        private val TITLE_IDS = listOf(
            "com.whatsapp:id/conversation_contact_name",
            "org.thoughtcrime.securesms:id/title",
            "org.telegram.messenger:id/action_bar_title",
            "com.instagram.android:id/thread_title",
            "com.discord:id/action_bar_title"
        )
    }
}
