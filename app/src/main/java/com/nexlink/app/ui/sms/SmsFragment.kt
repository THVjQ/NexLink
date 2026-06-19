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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexlink.app.R
import com.nexlink.app.databinding.FragmentSmsBinding
import com.nexlink.app.db.BlockStore
import com.nexlink.app.ui.NexPopup
import com.nexlink.app.db.CategoryStore
import com.nexlink.shared.Conversation
import com.nexlink.app.db.GroupNameStore
import com.nexlink.app.ui.contacts.Contact
import com.nexlink.app.db.PinStore
import com.nexlink.app.db.RecycleBinStore
import com.nexlink.app.db.SmsHelper

class SmsFragment : Fragment() {

    private var _b: FragmentSmsBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: ConversationAdapter
    private var allConversations = listOf<Conversation>()
    private var filterUnreadOnly = false
    private var activeCategoryId: String? = null

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
            onLongClick = { conv, view -> showConversationOptions(conv, view) }
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
        b.btnMoreOptions.setOnClickListener { showCategoryMenu() }

        refreshCategoryChips()
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
            b.btnFilterUnread.setTextColor(android.graphics.Color.WHITE)
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
                refreshCategoryChips()
            }
        }.start()
    }

    private fun filterConversations(query: String) {
        var result = allConversations

        // Filter blocked numbers
        val blocked = BlockStore.blockedSet(requireContext())
        result = result.filter { conv -> !blocked.contains(conv.address.replace("\\s".toRegex(), "")) }

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

        // Category filter
        val catId = activeCategoryId
        if (catId != null) {
            val cat = CategoryStore.getAll(requireContext()).find { it.id == catId }
            if (cat != null) result = result.filter { it.threadId in cat.threadIds }
        }

        // Sort pinned first, then by timestamp descending
        val pinned = PinStore.pinnedSet(requireContext())
        result = result.sortedWith(compareByDescending<Conversation> {
            it.threadId.toString() in pinned
        }.thenByDescending { it.timestamp })

        val ctx = requireContext()
        val renamed = result.map { conv ->
            val custom = GroupNameStore.getName(ctx, conv.participants)
            if (custom != null) conv.copy(contactName = custom) else conv
        }
        adapter.setData(renamed)
    }

    private fun openConversation(conv: Conversation) {
        val ctx = requireContext()
        val displayName = if (conv.participants.size > 1)
            GroupNameStore.getName(ctx, conv.participants) ?: conv.contactName
        else conv.contactName
        startActivity(Intent(ctx, ConversationActivity::class.java).apply {
            putExtra("address", conv.address)
            putExtra("contact_name", displayName)
            putExtra("thread_id", conv.threadId)
            putStringArrayListExtra("participants", ArrayList(conv.participants))
        })
    }

    private fun showConversationOptions(conv: Conversation, anchor: View) {
        val ctx = requireContext()
        val isPinned  = PinStore.isPinned(ctx, conv.threadId)
        val isBlocked = BlockStore.isBlocked(ctx, conv.address)
        val cats      = CategoryStore.getAll(ctx)
        NexPopup.show(anchor, buildList {
            add(NexPopup.Item(
                if (isPinned) "Unpin" else "Pin", R.drawable.ic_pin
            ) {
                if (isPinned) PinStore.unpin(ctx, conv.threadId) else PinStore.pin(ctx, conv.threadId)
                loadConversations()
            })
            add(NexPopup.Item(
                if (isBlocked) "Unblock" else "Block", R.drawable.ic_clear,
                isDestructive = !isBlocked
            ) {
                if (isBlocked) {
                    BlockStore.unblock(ctx, conv.address)
                    loadConversations()
                } else {
                    MaterialAlertDialogBuilder(ctx)
                        .setTitle("Block ${conv.contactName.ifBlank { conv.address }}?")
                        .setMessage("You won't receive messages from this number.")
                        .setPositiveButton("Block") { _, _ ->
                            RecycleBinStore.add(ctx, conv)
                            BlockStore.block(ctx, conv.address)
                            loadConversations()
                        }
                        .setNegativeButton("Cancel", null).show()
                }
            })
            if (cats.isNotEmpty()) add(NexPopup.Item("Add to category", R.drawable.ic_inbox) {
                showCategoryAssignDialog(conv, cats)
            })
            add(NexPopup.Item("Delete", R.drawable.ic_delete, isDestructive = true) {
                confirmDeleteConversation(conv)
            })
        })
    }

    private fun showCategoryAssignDialog(conv: Conversation, cats: List<com.nexlink.app.db.ChatCategory>) {
        val ctx = requireContext()
        val names = cats.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Add to category")
            .setItems(names) { _, i ->
                CategoryStore.assignThread(ctx, cats[i].id, conv.threadId)
                Toast.makeText(ctx, "Added to ${cats[i].name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteConversation(conv: Conversation) {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Delete conversation")
            .setMessage("Delete conversation with ${conv.contactName.ifBlank { conv.address }}?")
            .setPositiveButton("Delete") { _, _ ->
                RecycleBinStore.add(ctx, conv)
                Thread {
                    SmsHelper.deleteThread(ctx, conv.threadId)
                    activity?.runOnUiThread { loadConversations() }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Category management ───────────────────────────────────────────────────

    private fun showCategoryMenu() {
        val ctx = requireContext()
        val cats = CategoryStore.getAll(ctx)
        NexPopup.show(b.btnMoreOptions, buildList {
            add(NexPopup.Item("New category", R.drawable.ic_add) { showNewCategoryDialog() })
            cats.forEach { cat ->
                add(NexPopup.Item(cat.name, R.drawable.ic_inbox) {
                    activeCategoryId = if (activeCategoryId == cat.id) null else cat.id
                    refreshCategoryChips()
                    filterConversations(b.etSearch.text?.toString() ?: "")
                })
            }
            if (cats.isNotEmpty()) add(NexPopup.Item("Edit categories", R.drawable.ic_edit) {
                showEditCategoriesDialog()
            })
        })
    }

    private fun showNewCategoryDialog() {
        val ctx = requireContext()
        val input = android.widget.EditText(ctx).apply {
            hint = "Category name"
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("New category")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    CategoryStore.addCategory(ctx, name)
                    refreshCategoryChips()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditCategoriesDialog() {
        val ctx = requireContext()
        val cats = CategoryStore.getAll(ctx)
        if (cats.isEmpty()) return
        val names = cats.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Edit categories")
            .setItems(names) { _, i ->
                val cat = cats[i]
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(cat.name)
                    .setItems(arrayOf("Rename", "Delete")) { _, which ->
                        if (which == 0) {
                            val input = android.widget.EditText(ctx).apply { setText(cat.name); setPadding(48, 24, 48, 24) }
                            MaterialAlertDialogBuilder(ctx).setTitle("Rename")
                                .setView(input)
                                .setPositiveButton("OK") { _, _ ->
                                    CategoryStore.renameCategory(ctx, cat.id, input.text.toString().trim())
                                    refreshCategoryChips()
                                }
                                .setNegativeButton("Cancel", null).show()
                        } else {
                            if (activeCategoryId == cat.id) activeCategoryId = null
                            CategoryStore.deleteCategory(ctx, cat.id)
                            refreshCategoryChips()
                            filterConversations(b.etSearch.text?.toString() ?: "")
                        }
                    }
                    .show()
            }
            .show()
    }

    private fun refreshCategoryChips() {
        val ctx = context ?: return
        val cats = CategoryStore.getAll(ctx)
        b.scrollCategories.visibility = if (cats.isEmpty()) View.GONE else View.VISIBLE
        b.chipBar.removeAllViews()
        cats.forEach { cat ->
            val chip = android.widget.TextView(ctx).apply {
                text = cat.name
                tag  = cat.id   // store id for visual-update without view recreation
                textSize = 14f
                setPadding(28.dpToPx(ctx), 10.dpToPx(ctx), 28.dpToPx(ctx), 10.dpToPx(ctx))
                val isActive = cat.id == activeCategoryId
                setBackgroundResource(if (isActive) R.drawable.bg_card_selected else R.drawable.bg_card_unselected)
                setTextColor(if (isActive) android.graphics.Color.WHITE
                             else resources.getColor(R.color.muted, null))
                setOnClickListener {
                    activeCategoryId = if (activeCategoryId == cat.id) null else cat.id
                    // Update visuals in-place — do NOT call refreshCategoryChips() here, as removing
                    // and recreating views during a touch event causes the wrong chip to be activated.
                    updateCategoryChipVisuals()
                    filterConversations(b.etSearch.text?.toString() ?: "")
                }
            }
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8.dpToPx(ctx) }
            b.chipBar.addView(chip, params)
        }
    }

    private fun updateCategoryChipVisuals() {
        val muted = resources.getColor(R.color.muted, null)
        for (i in 0 until b.chipBar.childCount) {
            val chip = b.chipBar.getChildAt(i) as? android.widget.TextView ?: continue
            val isActive = chip.tag == activeCategoryId
            chip.setBackgroundResource(if (isActive) R.drawable.bg_card_selected else R.drawable.bg_card_unselected)
            chip.setTextColor(if (isActive) android.graphics.Color.WHITE else muted)
        }
    }

    private fun Int.dpToPx(ctx: android.content.Context) =
        (this * ctx.resources.displayMetrics.density).toInt()

    // ── FAB — new chat / new group ─────────────────────────────────────────────

    private fun showNewChatMenu() {
        NexPopup.show(b.fabNewChat, listOf(
            NexPopup.Item("New message",    R.drawable.ic_compose)  { openContactPicker(multiSelect = false) },
            NexPopup.Item("New group chat", R.drawable.ic_contacts) { openContactPicker(multiSelect = true)  }
        ))
    }

    private fun openContactPicker(multiSelect: Boolean) {
        ContactPickerSheet(
            multiSelect  = multiSelect,
            onSinglePick = { contact, number ->
                startActivity(Intent(requireContext(), ConversationActivity::class.java).apply {
                    putExtra("address", number)
                    putExtra("contact_name", contact.name)
                })
            },
            onGroupPick  = { contacts -> showGroupNameDialog(contacts) }
        ).show(parentFragmentManager, "contact_picker")
    }

    private fun showGroupNameDialog(contacts: List<Contact>) {
        val ctx = requireContext()
        val defaultName = contacts.joinToString(", ") { it.name.split(" ").first() }
        val nameInput = android.widget.EditText(ctx).apply {
            hint = "Group name"; setText(defaultName); setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Name your group")
            .setView(nameInput)
            .setPositiveButton("Start") { _, _ ->
                val groupName = nameInput.text.toString().trim().ifEmpty { defaultName }
                val participants = contacts.map { it.number }
                GroupNameStore.setName(ctx, participants, groupName)
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


    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
