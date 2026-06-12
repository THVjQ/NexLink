package com.nexlink.app.ui.sms

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ShareActivity : AppCompatActivity() {

    private data class Contact(val name: String, val number: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.action ?: run { finish(); return }
        val type   = intent.type  ?: ""

        val sharedText = if (action == Intent.ACTION_SEND && type.startsWith("text/"))
            intent.getStringExtra(Intent.EXTRA_TEXT) else null
        @Suppress("DEPRECATION")
        val sharedUri = if (action == Intent.ACTION_SEND && !type.startsWith("text/"))
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else null

        Thread {
            val contacts = loadContacts()
            runOnUiThread {
                if (contacts.isEmpty()) { finish(); return@runOnUiThread }
                showPicker(contacts, sharedText, sharedUri, type)
            }
        }.start()
    }

    private fun loadContacts(): List<Contact> {
        val seen = mutableSetOf<String>()
        val list = mutableListOf<Contact>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val raw  = c.getString(1) ?: continue
                val num  = raw.replace(Regex("[^+0-9]"), "")
                if (num.isNotBlank() && seen.add(num)) list += Contact(name, num)
            }
        }
        return list
    }

    private fun showPicker(
        contacts: List<Contact>,
        sharedText: String?,
        sharedUri: Uri?,
        mimeType: String
    ) {
        val names = contacts.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Share via NexLink")
            .setItems(names) { _, idx ->
                val c = contacts[idx]
                launchConversation(c.number, c.name, sharedText, sharedUri, mimeType)
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun launchConversation(
        address: String,
        name: String,
        sharedText: String?,
        sharedUri: Uri?,
        mimeType: String
    ) {
        val i = Intent(this, ConversationActivity::class.java).apply {
            putExtra("address", address)
            putExtra("contact_name", name)
            sharedText?.let { putExtra("share_text", it) }
            sharedUri?.let {
                putExtra("share_uri", it.toString())
                putExtra("share_mime", mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        startActivity(i)
        finish()
    }
}
