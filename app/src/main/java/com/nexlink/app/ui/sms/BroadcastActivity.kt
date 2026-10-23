package com.nexlink.app.ui.sms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexlink.app.databinding.ActivityConversationBinding
import com.nexlink.app.db.BroadcastStore
import com.nexlink.app.db.CryptoStore
import com.nexlink.app.db.NotificationPrefs
import com.nexlink.app.db.SmsHelper
import com.nexlink.shared.SmsMessage

/**
 * A broadcast list: each message goes out as a separate SMS to every member, so nobody sees who
 * else got it and every reply arrives privately in that person's own chat. Reuses the conversation
 * layout with attachments, voice and the info drawer turned off — broadcasts are text only.
 *
 * Being plain SMS, a broadcast needs no mobile data or MMS and works even where group MMS doesn't.
 */
class BroadcastActivity : AppCompatActivity() {

    private lateinit var b: ActivityConversationBinding
    private lateinit var adapter: BubbleAdapter
    private var broadcastId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(b.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(b.contentLayout) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(0, sys.top, 0, maxOf(ime.bottom, sys.bottom))
            insets
        }

        broadcastId = intent.getLongExtra("broadcast_id", 0L)
        if (BroadcastStore.get(this, broadcastId) == null) { finish(); return }

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        b.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        b.btnAttach.visibility = View.GONE
        b.btnVoice.visibility = View.GONE
        b.btnLoadEarlier.visibility = View.GONE
        b.dividerLoadEarlier.visibility = View.GONE
        b.etInput.hint = "Message everyone privately"

        adapter = BubbleAdapter(onDelete = { msg ->
            val bc = BroadcastStore.get(this, broadcastId) ?: return@BubbleAdapter
            // Removes it from this list only; the copies already in each person's chat stay.
            BroadcastStore.update(this, bc.copy(sent = bc.sent.filter { it.timestamp != msg.timestamp }))
            refresh()
        })
        b.recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        b.recycler.adapter = adapter
        b.btnSend.setOnClickListener { send() }
        refresh()
    }

    private fun refresh() {
        val bc = BroadcastStore.get(this, broadcastId) ?: return
        supportActionBar?.title = bc.name
        supportActionBar?.subtitle = "Broadcast · ${bc.members.size} " +
            if (bc.members.size == 1) "person" else "people"
        adapter.setData(bc.sent.map {
            SmsMessage(id = it.timestamp, address = "", body = it.body,
                       timestamp = it.timestamp, isIncoming = false)
        })
        if (bc.sent.isNotEmpty()) b.recycler.scrollToPosition(adapter.itemCount - 1)
    }

    private fun send() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SMS permission required", Toast.LENGTH_SHORT).show()
            return
        }
        val bc = BroadcastStore.get(this, broadcastId) ?: return
        val text = b.etInput.text.toString().trim()
        if (text.isEmpty()) return
        if (bc.members.isEmpty()) {
            Toast.makeText(this, "Add people to this broadcast first", Toast.LENGTH_SHORT).show()
            return
        }
        b.etInput.text?.clear()
        Thread {
            var failed = 0
            for (to in bc.members) {
                try {
                    // Same rule as a 1:1 send: each copy is that person's own message, so it is
                    // encrypted for them when they have a NexLink session.
                    val key = CryptoStore.getSessionKey(this, to)
                    val out = if (key != null && CryptoStore.isSessionReady(this, to) &&
                                  NotificationPrefs.isEncryptionEnabled(this))
                                  CryptoStore.encrypt(text, key) else text
                    SmsHelper.sendSms(this, to, out)
                } catch (_: Exception) { failed++ }
            }
            BroadcastStore.addSent(this, broadcastId, text, bc.members.size - failed)
            runOnUiThread {
                refresh()
                if (failed > 0) Toast.makeText(this,
                    "Couldn't send to $failed of ${bc.members.size} — check their chats",
                    Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // ── Menu ──────────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_MEMBERS, 0, "Members")
        menu.add(0, MENU_RENAME, 1, "Rename")
        menu.add(0, MENU_DELETE, 2, "Delete broadcast")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_MEMBERS -> showMembers()
            MENU_RENAME  -> rename()
            MENU_DELETE  -> confirmDelete()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /** A broadcast is only a list, so — unlike a group — members change in place. */
    private fun showMembers(members: List<String>? = null) {
        val bc = BroadcastStore.get(this, broadcastId) ?: return
        MembersDialog.show(this, "Broadcast members", members ?: bc.members,
            onAddPeople = { kept ->
                ContactPickerSheet(
                    multiSelect  = true,
                    onSinglePick = { _, number -> showMembers(kept + number) },
                    onGroupPick  = { contacts -> showMembers(kept + contacts.map { it.number }) },
                    titleText    = "Add people"
                ).show(supportFragmentManager, "add_people")
            },
            onSave = { kept ->
                BroadcastStore.update(this, bc.copy(members = kept))
                refresh()
            })
    }

    private fun rename() {
        val bc = BroadcastStore.get(this, broadcastId) ?: return
        val input = android.widget.EditText(this).apply {
            hint = "Broadcast name"; setText(bc.name); setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Rename broadcast")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) { BroadcastStore.update(this, bc.copy(name = n)); refresh() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete broadcast?")
            .setMessage("This removes the list. Messages already sent stay in each person's chat.")
            .setPositiveButton("Delete") { _, _ ->
                BroadcastStore.delete(this, broadcastId); finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private const val MENU_MEMBERS = 1
        private const val MENU_RENAME  = 2
        private const val MENU_DELETE  = 3
    }
}
