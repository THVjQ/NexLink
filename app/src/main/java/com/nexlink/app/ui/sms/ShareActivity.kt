package com.nexlink.app.ui.sms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.nexlink.app.R
import com.nexlink.app.databinding.ActivityShareBinding
import com.nexlink.app.db.DeepLinkHelper
import com.nexlink.app.db.NotificationStore
import com.nexlink.app.db.SmsHelper
import com.nexlink.app.db.SocialNotification
import com.nexlink.shared.AvatarColors
import com.nexlink.shared.Conversation

class ShareActivity : AppCompatActivity() {

    private lateinit var b: ActivityShareBinding

    private var sharedText: String? = null
    private var sharedUri:  String? = null
    private var sharedMime: String? = null

    private data class ShareContact(val name: String, val number: String)

    private enum class Tab { SMS, INBOX, CONTACTS }
    private var activeTab = Tab.SMS

    private var conversations = listOf<Conversation>()
    private var allContacts   = listOf<ShareContact>()

    private val shareAdapter = ShareAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityShareBinding.inflate(layoutInflater)
        setContentView(b.root)

        val action = intent.action ?: run { finish(); return }
        val type   = intent.type  ?: ""

        if (action == Intent.ACTION_SEND) {
            if (type.startsWith("text/") || type.isEmpty()) {
                // EXTRA_TEXT first, then EXTRA_SUBJECT as fallback (some apps put URLs there)
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
                    ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }
            }
            if (sharedText == null) {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                sharedUri  = uri?.toString()
                sharedMime = if (uri != null) type.ifEmpty { "*/*" } else null
            }
        }

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        val preview = sharedText?.take(70)?.let { if ((sharedText?.length ?: 0) > 70) "$it…" else it }
            ?: sharedMime?.substringBefore("/")?.let { "📎 $it attachment" } ?: ""
        if (preview.isNotEmpty()) {
            b.tvSharePreview.text = "Sharing: $preview"
            b.tvSharePreview.visibility = View.VISIBLE
        }

        b.tabLayout.addTab(b.tabLayout.newTab().setText("SMS"))
        b.tabLayout.addTab(b.tabLayout.newTab().setText("Inbox"))
        b.tabLayout.addTab(b.tabLayout.newTab().setText("Contacts"))

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = shareAdapter

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b2: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { filterCurrent(s?.toString() ?: "") }
        })

        b.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                activeTab = when (tab.position) { 1 -> Tab.INBOX; 2 -> Tab.CONTACTS; else -> Tab.SMS }
                b.searchRow.visibility = if (activeTab == Tab.INBOX) View.GONE else View.VISIBLE
                b.etSearch.text?.clear()
                loadTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        loadTab()
    }

    private fun loadTab() {
        when (activeTab) {
            Tab.SMS -> {
                if (conversations.isNotEmpty()) {
                    shareAdapter.setConversations(conversations, ::pickConversation)
                } else {
                    Thread {
                        val list = SmsHelper.getConversations(this)
                        runOnUiThread { conversations = list; shareAdapter.setConversations(list, ::pickConversation) }
                    }.start()
                }
            }
            Tab.INBOX -> {
                val items = NotificationStore.notifications.value.orEmpty()
                    .groupBy { it.platform to it.sender }
                    .mapNotNull { (_, msgs) -> msgs.maxByOrNull { it.timestamp } }
                    .sortedByDescending { it.timestamp }
                shareAdapter.setInbox(items) { n ->
                    sharedText?.let { text ->
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("share", text))
                        Toast.makeText(this, "Copied — paste in ${n.platform}", Toast.LENGTH_SHORT).show()
                    }
                    DeepLinkHelper.openPlatform(this, n.platform)
                    finish()
                }
            }
            Tab.CONTACTS -> {
                if (allContacts.isNotEmpty()) {
                    shareAdapter.setContacts(allContacts, ::pickContact)
                } else {
                    Thread {
                        val list = loadContacts()
                        runOnUiThread { allContacts = list; shareAdapter.setContacts(list, ::pickContact) }
                    }.start()
                }
            }
        }
    }

    private fun filterCurrent(q: String) {
        when (activeTab) {
            Tab.SMS -> {
                val f = if (q.isBlank()) conversations
                    else conversations.filter { it.contactName.contains(q, true) || it.address.contains(q) }
                shareAdapter.setConversations(f, ::pickConversation)
            }
            Tab.CONTACTS -> {
                val f = if (q.isBlank()) allContacts
                    else allContacts.filter { it.name.contains(q, true) || it.number.contains(q) }
                shareAdapter.setContacts(f, ::pickContact)
            }
            Tab.INBOX -> {}
        }
    }

    private fun pickConversation(conv: Conversation) {
        startActivity(Intent(this, ConversationActivity::class.java).apply {
            putExtra("address", conv.address)
            putExtra("contact_name", conv.contactName)
            putExtra("thread_id", conv.threadId)
            putStringArrayListExtra("participants", ArrayList(conv.participants))
            sharedText?.let { putExtra("share_text", it) }
            sharedUri?.let {
                putExtra("share_uri", it)
                putExtra("share_mime", sharedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        })
        finish()
    }

    private fun pickContact(c: ShareContact) {
        startActivity(Intent(this, ConversationActivity::class.java).apply {
            putExtra("address", c.number)
            putExtra("contact_name", c.name)
            sharedText?.let { putExtra("share_text", it) }
            sharedUri?.let {
                putExtra("share_uri", it)
                putExtra("share_mime", sharedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        })
        finish()
    }

    private fun loadContacts(): List<ShareContact> {
        val seen = mutableSetOf<String>()
        val list = mutableListOf<ShareContact>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val num  = (c.getString(1) ?: continue).replace(Regex("[^+0-9]"), "")
                if (num.isNotBlank() && seen.add(num)) list += ShareContact(name, num)
            }
        }
        return list
    }

    // ── Unified adapter ───────────────────────────────────────────────────────

    private inner class ShareAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_ROW   = 0
        private val TYPE_INBOX = 1

        private var items: List<Any> = emptyList()
        private var clickHandler: ((Any) -> Unit)? = null

        fun setConversations(list: List<Conversation>, onClick: (Conversation) -> Unit) {
            items = list; clickHandler = { onClick(it as Conversation) }; notifyDataSetChanged()
        }

        fun setInbox(list: List<SocialNotification>, onClick: (SocialNotification) -> Unit) {
            items = list; clickHandler = { onClick(it as SocialNotification) }; notifyDataSetChanged()
        }

        fun setContacts(list: List<ShareContact>, onClick: (ShareContact) -> Unit) {
            items = list; clickHandler = { onClick(it as ShareContact) }; notifyDataSetChanged()
        }

        override fun getItemViewType(pos: Int) =
            if (items[pos] is SocialNotification) TYPE_INBOX else TYPE_ROW

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_INBOX)
                InboxVH(inf.inflate(R.layout.item_notification, parent, false))
            else
                RowVH(inf.inflate(R.layout.item_share_row, parent, false))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
            val item = items[pos]
            when (holder) {
                is RowVH -> {
                    val (name, sub) = when (item) {
                        is Conversation  -> item.contactName.ifBlank { item.address } to item.lastMessage
                        is ShareContact  -> item.name to item.number
                        else             -> return
                    }
                    val initials = name.split(" ").take(2)
                        .joinToString("") { it.take(1).uppercase() }.ifBlank { name.take(2).uppercase() }
                    holder.tvAvatar.text = initials
                    holder.tvAvatar.background.mutate().setTint(AvatarColors.colorFor(initials))
                    holder.tvName.text = name
                    holder.tvSub.text  = sub
                    holder.itemView.setOnClickListener { clickHandler?.invoke(item) }
                }
                is InboxVH -> {
                    val n = item as SocialNotification
                    val color = platformColor(n.platform)
                    val initials = n.sender.split(" ").take(2)
                        .joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
                    holder.tvAvatar.text = initials
                    holder.tvAvatar.background.mutate().setTint(color and 0x00FFFFFF or 0x33000000)
                    holder.tvName.text     = n.sender
                    holder.tvPlatform.text = n.platform
                    holder.tvPlatform.setTextColor(color)
                    holder.tvText.text     = n.text
                    holder.tvTime.text     = ""
                    holder.tvCount.visibility    = View.GONE
                    holder.btnOpenCall.visibility = View.GONE
                    holder.itemView.setOnClickListener { clickHandler?.invoke(item) }
                }
            }
        }

        private fun platformColor(platform: String) = when (platform) {
            "Signal"    -> 0xFF3a9bd5.toInt()
            "Telegram"  -> 0xFF229ed9.toInt()
            "WhatsApp"  -> 0xFF25d366.toInt()
            "Messenger" -> 0xFF0099ff.toInt()
            "Discord"   -> 0xFF5865F2.toInt()
            "Instagram" -> 0xFFE1306C.toInt()
            "Steam"     -> 0xFF1A9FFF.toInt()
            else        -> 0xFF6c5ce7.toInt()
        }

        inner class RowVH(v: View) : RecyclerView.ViewHolder(v) {
            val tvAvatar: TextView = v.findViewById(R.id.tvAvatar)
            val tvName:   TextView = v.findViewById(R.id.tvName)
            val tvSub:    TextView = v.findViewById(R.id.tvSub)
        }

        inner class InboxVH(v: View) : RecyclerView.ViewHolder(v) {
            val tvAvatar:   TextView    = v.findViewById(R.id.tvAvatar)
            val tvName:     TextView    = v.findViewById(R.id.tvName)
            val tvPlatform: TextView    = v.findViewById(R.id.tvPlatform)
            val tvText:     TextView    = v.findViewById(R.id.tvText)
            val tvTime:     TextView    = v.findViewById(R.id.tvTime)
            val tvCount:    TextView    = v.findViewById(R.id.tvCount)
            val btnOpenCall: ImageButton = v.findViewById(R.id.btnOpenCall)
        }
    }
}
