package com.nexlink.app.ui.settings

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.nexlink.app.R
import com.nexlink.app.databinding.FragmentSettingsBinding
import com.nexlink.app.ui.NexPopup
import com.nexlink.app.db.BlockStore
import com.nexlink.app.db.DeletedConversation
import com.nexlink.app.db.DeletedMessage
import com.nexlink.app.db.IconPrefs
import com.nexlink.app.db.NotificationPrefs
import com.nexlink.app.db.RecycleBinStore
import com.nexlink.app.db.SmsBackupHelper
import com.nexlink.app.services.NexLinkNotificationListener
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private sealed class BinEntry {
        abstract val deletedAt: Long
        data class Conv(val d: DeletedConversation) : BinEntry() { override val deletedAt get() = d.deletedAt }
        data class Msg(val d: DeletedMessage)       : BinEntry() { override val deletedAt get() = d.deletedAt }
    }

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private var socialsExpanded = false

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        uri ?: return@registerForActivityResult
        val ctx = requireContext()
        Thread {
            try {
                val count = ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    SmsBackupHelper.exportToXml(ctx, out)
                } ?: 0
                activity?.runOnUiThread {
                    Toast.makeText(ctx, "Exported $count messages", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        val ctx = requireContext()
        Thread {
            try {
                val result = ctx.contentResolver.openInputStream(uri)?.use { inp ->
                    SmsBackupHelper.importFromXml(ctx, inp)
                } ?: SmsBackupHelper.ImportResult(0, 0)
                activity?.runOnUiThread {
                    Toast.makeText(ctx,
                        "Imported ${result.imported} messages (${result.skipped} skipped)",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(ctx, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.btnNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        b.btnAccessibility.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Sent Message Capture")
                .setMessage(
                    "NexLink uses Android's Accessibility Service to read the messages " +
                    "you send inside Signal, WhatsApp, Telegram, Instagram, Discord, and Steam " +
                    "while those apps are open on your screen.\n\n" +
                    "This lets NexLink show both sides of your conversations in the inbox — " +
                    "including replies you send — not just incoming messages.\n\n" +
                    "What NexLink reads:\n" +
                    "• Text you send in the supported social apps\n" +
                    "• The name of the chat you are currently in\n\n" +
                    "What NexLink does NOT do:\n" +
                    "• Does not read passwords, banking apps, or any other app\n" +
                    "• Does not send or share any data off your device\n" +
                    "• All captured messages are stored locally on your phone only\n\n" +
                    "Tap Enable to open Accessibility Settings and turn on NexLink."
                )
                .setPositiveButton("Enable") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        b.btnAppSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            })
        }
        b.btnSetDefaultSms.setOnClickListener {
            (activity as? com.nexlink.app.MainActivity)?.requestDefaultSmsRole()
        }
        b.btnBuyMeCoffee.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/THVjQ")))
        }

        b.btnExportSms.setOnClickListener {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            exportLauncher.launch("NexLink_SMS_$ts.xml")
        }

        b.btnImportSms.setOnClickListener {
            val ctx = requireContext()
            if (!com.nexlink.app.db.SmsHelper.isDefaultSmsApp(ctx)) {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("Default SMS app required")
                    .setMessage("To import messages, NexLink must be set as your default SMS app.")
                    .setPositiveButton("Open settings") { _, _ ->
                        (activity as? com.nexlink.app.MainActivity)?.requestDefaultSmsRole()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@setOnClickListener
            }
            importLauncher.launch(arrayOf("text/xml", "application/xml", "*/*"))
        }

        b.btnReportBug.setOnClickListener {
            val template = buildString {
                appendLine("**Device:** ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("**Android:** ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine()
                appendLine("**Describe the bug:**")
                appendLine()
                appendLine("**Steps to reproduce:**")
                appendLine()
                appendLine("**Expected behaviour:**")
            }
            val encodedBody = android.net.Uri.encode(template)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/THVjQ/NexLink/issues/new?body=$encodedBody"))
            startActivity(intent)
        }

        val ctx = requireContext()

        // RCS toggle
        b.switchRcs.isChecked = NotificationPrefs.isRcsEnabled(ctx)
        b.switchRcs.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefs.setRcsEnabled(ctx, isChecked)
        }

        // Encryption master toggle
        b.switchEncryption.isChecked = NotificationPrefs.isEncryptionEnabled(ctx)
        b.switchEncryption.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("Disable Encryption?")
                    .setMessage("Messages will no longer be encrypted. This cannot be undone for existing sessions without re-sending a key exchange.")
                    .setPositiveButton("Disable") { _, _ -> NotificationPrefs.setEncryptionEnabled(ctx, false) }
                    .setNegativeButton("Cancel") { _, _ -> b.switchEncryption.isChecked = true }
                    .show()
            } else {
                NotificationPrefs.setEncryptionEnabled(ctx, true)
            }
        }

        // Key exchange promo toggle
        b.switchKeyExchangePromo.isChecked = NotificationPrefs.isKeyExchangePromoEnabled(ctx)
        b.switchKeyExchangePromo.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefs.setKeyExchangePromoEnabled(ctx, isChecked)
        }

        // Suppress-source toggle
        b.switchSuppressSource.isChecked = NotificationPrefs.isSuppressSourceEnabled(ctx)
        b.switchSuppressSource.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefs.setSuppressSource(ctx, isChecked)
        }

        // Suppress call notifications toggle
        b.switchSuppressCallNotifs.isChecked = NotificationPrefs.isSuppressCallNotifs(ctx)
        b.switchSuppressCallNotifs.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefs.setSuppressCallNotifs(ctx, isChecked)
        }

        // Pass-through media toggle
        b.switchPassThroughMedia.isChecked = NotificationPrefs.isPassThroughMedia(ctx)
        b.switchPassThroughMedia.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefs.setPassThroughMedia(ctx, isChecked)
        }

        // Platform toggles
        val platformSwitches = mapOf(
            "Signal"    to b.switchPlatformSignal,
            "Telegram"  to b.switchPlatformTelegram,
            "WhatsApp"  to b.switchPlatformWhatsapp,
            "Messenger" to b.switchPlatformMessenger,
            "Discord"   to b.switchPlatformDiscord,
            "Instagram" to b.switchPlatformInstagram,
            "Steam"     to b.switchPlatformSteam
        )
        platformSwitches.forEach { (platform, switch) ->
            switch.isChecked = NotificationPrefs.isPlatformEnabled(ctx, platform)
            switch.setOnCheckedChangeListener { _, isChecked ->
                NotificationPrefs.setPlatformEnabled(ctx, platform, isChecked)
            }
        }

        // Social platforms collapsible
        b.cardSocialPlatforms.visibility = View.GONE
        b.btnToggleSocials.setOnClickListener {
            socialsExpanded = !socialsExpanded
            b.cardSocialPlatforms.visibility = if (socialsExpanded) View.VISIBLE else View.GONE
            b.btnToggleSocials.setImageResource(
                if (socialsExpanded) com.nexlink.app.R.drawable.ic_expand_less
                else com.nexlink.app.R.drawable.ic_expand_more
            )
        }

        // Blocked Numbers
        refreshBlockedCount()
        b.rowBlockedNumbers.setOnClickListener { showBlockedNumbersDialog() }

        // Recycle Bin
        refreshRecycleBinCount()
        b.rowRecycleBin.setOnClickListener { showRecycleBinDialog() }

        // Share Contact (QR)
        b.rowShareContact.setOnClickListener { showQrContactDialog() }

        // Appearance — Dark Mode
        refreshDarkModeLabel()
        b.rowDarkMode.setOnClickListener { showDarkModeDialog(it) }

        // Appearance — App Icon
        refreshIconLabels()
        b.rowAppIcon.setOnClickListener { showAppIconDialog() }
        b.rowNotifIcon.setOnClickListener { showNotifIconDialog() }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        refreshBlockedCount()
        refreshRecycleBinCount()
        refreshDarkModeLabel()
        refreshIconLabels()

        // Refresh all switch states in case they were changed externally
        val ctx = context ?: return
        b.switchRcs.isChecked = NotificationPrefs.isRcsEnabled(ctx)
        b.switchEncryption.isChecked = NotificationPrefs.isEncryptionEnabled(ctx)
        b.switchKeyExchangePromo.isChecked = NotificationPrefs.isKeyExchangePromoEnabled(ctx)
        b.switchSuppressSource.isChecked = NotificationPrefs.isSuppressSourceEnabled(ctx)
        b.switchSuppressCallNotifs.isChecked = NotificationPrefs.isSuppressCallNotifs(ctx)
        b.switchPassThroughMedia.isChecked = NotificationPrefs.isPassThroughMedia(ctx)

        val platformSwitches = mapOf(
            "Signal"    to b.switchPlatformSignal,
            "Telegram"  to b.switchPlatformTelegram,
            "WhatsApp"  to b.switchPlatformWhatsapp,
            "Messenger" to b.switchPlatformMessenger,
            "Discord"   to b.switchPlatformDiscord,
            "Instagram" to b.switchPlatformInstagram,
            "Steam"     to b.switchPlatformSteam
        )
        platformSwitches.forEach { (platform, switch) ->
            switch.isChecked = NotificationPrefs.isPlatformEnabled(ctx, platform)
        }
    }

    private fun refreshBlockedCount() {
        val ctx = context ?: return
        val count = BlockStore.blockedSet(ctx).size
        b.tvBlockedCount.text = if (count == 0) "No blocked numbers" else "$count blocked"
    }

    private fun refreshRecycleBinCount() {
        val ctx = context ?: return
        val convCount = RecycleBinStore.getAll(ctx).size
        val msgCount  = RecycleBinStore.getMessages(ctx).size
        val total = convCount + msgCount
        b.tvRecycleBinCount.text = when {
            total == 0   -> "Empty"
            convCount > 0 && msgCount > 0 ->
                "$convCount chat${if (convCount != 1) "s" else ""}, $msgCount message${if (msgCount != 1) "s" else ""}"
            convCount > 0 ->
                "$convCount deleted chat${if (convCount != 1) "s" else ""}"
            else ->
                "$msgCount deleted message${if (msgCount != 1) "s" else ""}"
        }
    }

    private fun showBlockedNumbersDialog() {
        val ctx = requireContext()
        val blocked = BlockStore.blockedSet(ctx).toList()
        if (blocked.isEmpty()) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle("Blocked Numbers")
                .setMessage("No blocked numbers.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val dp = ctx.resources.displayMetrics.density
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
        var dialog: AlertDialog? = null
        dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("Blocked Numbers")
            .setView(android.widget.ScrollView(ctx).apply { addView(container) })
            .setNegativeButton("Close", null)
            .show()
        blocked.forEach { num ->
            val row = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding((20 * dp).toInt(), (14 * dp).toInt(), (20 * dp).toInt(), (14 * dp).toInt())
                isClickable = true; isFocusable = true
            }
            row.addView(android.widget.TextView(ctx).apply {
                text = num; textSize = 15f
                setTextColor(resources.getColor(R.color.text, null))
                layoutParams = android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            container.addView(row)
            row.setOnClickListener {
                NexPopup.show(row, listOf(
                    NexPopup.Item("Unblock", R.drawable.ic_clear, isDestructive = true) {
                        dialog?.dismiss()
                        BlockStore.unblock(ctx, num)
                        refreshBlockedCount()
                        Toast.makeText(ctx, "Unblocked $num", Toast.LENGTH_SHORT).show()
                    }
                ))
            }
        }
    }

    private fun showRecycleBinDialog() {
        val ctx = requireContext()
        val convs = RecycleBinStore.getAll(ctx)
        val msgs  = RecycleBinStore.getMessages(ctx)
        if (convs.isEmpty() && msgs.isEmpty()) {
            MaterialAlertDialogBuilder(ctx).setTitle("Recycle Bin").setMessage("The recycle bin is empty.")
                .setPositiveButton("OK", null).show()
            return
        }
        NexPopup.show(b.rowRecycleBin, buildList {
            add(NexPopup.Item("All (${convs.size + msgs.size})", R.drawable.ic_inbox) {
                showBinSection(ctx, (convs.map { BinEntry.Conv(it) } + msgs.map { BinEntry.Msg(it) })
                    .sortedByDescending { it.deletedAt })
            })
            if (convs.isNotEmpty()) add(NexPopup.Item("Chats (${convs.size})", R.drawable.ic_sms) {
                showBinSection(ctx, convs.map { BinEntry.Conv(it) })
            })
            if (msgs.isNotEmpty()) add(NexPopup.Item("Messages (${msgs.size})", R.drawable.ic_compose) {
                showBinSection(ctx, msgs.map { BinEntry.Msg(it) })
            })
            add(NexPopup.Item("Clear all", R.drawable.ic_delete, isDestructive = true) {
                MaterialAlertDialogBuilder(ctx).setTitle("Clear recycle bin?")
                    .setMessage("All items will be permanently removed.")
                    .setPositiveButton("Clear") { _, _ -> RecycleBinStore.clear(ctx); refreshRecycleBinCount() }
                    .setNegativeButton("Cancel", null).show()
            })
        })
    }

    private fun showBinSection(ctx: android.content.Context, entries: List<BinEntry>) {
        val now = System.currentTimeMillis()
        fun daysLeft(at: Long) = (30 - ((now - at) / 86_400_000)).coerceAtLeast(0)
        val dp = ctx.resources.displayMetrics.density

        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        var dialog: AlertDialog? = null
        dialog = MaterialAlertDialogBuilder(ctx).setTitle("Recycle Bin")
            .setView(android.widget.ScrollView(ctx).apply { addView(container) })
            .setNegativeButton("Close", null)
            .show()

        entries.forEach { entry ->
            val row = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding((20 * dp).toInt(), (14 * dp).toInt(), (20 * dp).toInt(), (14 * dp).toInt())
                isClickable = true; isFocusable = true
            }
            when (entry) {
                is BinEntry.Conv -> {
                    val d = entry.d
                    row.addView(android.widget.TextView(ctx).apply {
                        text = "💬 ${d.contactName.ifBlank { d.address }}"
                        textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(resources.getColor(R.color.text, null))
                    })
                    if (d.lastMessage.isNotBlank()) row.addView(android.widget.TextView(ctx).apply {
                        text = d.lastMessage.take(80); textSize = 12f
                        setTextColor(resources.getColor(R.color.muted, null))
                    })
                    row.addView(android.widget.TextView(ctx).apply {
                        text = "${daysLeft(d.deletedAt)} days left"; textSize = 11f
                        setTextColor(resources.getColor(R.color.accent, null))
                    })
                    row.setOnClickListener {
                        NexPopup.show(row, listOf(
                            NexPopup.Item("Restore", R.drawable.ic_open_in_app) {
                                dialog?.dismiss()
                                RecycleBinStore.remove(ctx, d.threadId); refreshRecycleBinCount()
                                Toast.makeText(ctx, "Conversation restored", Toast.LENGTH_SHORT).show()
                            },
                            NexPopup.Item("Delete permanently", R.drawable.ic_delete, isDestructive = true) {
                                dialog?.dismiss()
                                RecycleBinStore.remove(ctx, d.threadId); refreshRecycleBinCount()
                                Toast.makeText(ctx, "Permanently deleted", Toast.LENGTH_SHORT).show()
                            }
                        ))
                    }
                }
                is BinEntry.Msg -> {
                    val d = entry.d
                    row.addView(android.widget.TextView(ctx).apply {
                        text = "✉ ${d.address}"
                        textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(resources.getColor(R.color.text, null))
                    })
                    row.addView(android.widget.TextView(ctx).apply {
                        text = d.body.trim().take(80).ifBlank { "(media)" }; textSize = 12f
                        setTextColor(resources.getColor(R.color.muted, null))
                    })
                    row.addView(android.widget.TextView(ctx).apply {
                        text = "${daysLeft(d.deletedAt)} days left"; textSize = 11f
                        setTextColor(resources.getColor(R.color.accent, null))
                    })
                    row.setOnClickListener {
                        NexPopup.show(row, listOf(
                            NexPopup.Item("Delete permanently", R.drawable.ic_delete, isDestructive = true) {
                                dialog?.dismiss()
                                val remaining = RecycleBinStore.getMessages(ctx).filter { it.id != d.id }
                                ctx.getSharedPreferences("nx_recycle_bin", android.content.Context.MODE_PRIVATE)
                                    .edit().putString("deleted_msgs", org.json.JSONArray().apply {
                                        remaining.forEach { m ->
                                            put(org.json.JSONObject().apply {
                                                put("id", m.id); put("threadId", m.threadId)
                                                put("address", m.address); put("body", m.body)
                                                put("timestamp", m.timestamp); put("isMms", m.isMms)
                                                put("deletedAt", m.deletedAt)
                                            })
                                        }
                                    }.toString()).apply()
                                refreshRecycleBinCount()
                                Toast.makeText(ctx, "Message permanently deleted", Toast.LENGTH_SHORT).show()
                            }
                        ))
                    }
                }
            }
            container.addView(row)
            // Thin divider
            container.addView(android.view.View(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { marginStart = (20 * dp).toInt() }
                setBackgroundColor(0x22FFFFFF)
            })
        }
    }

    private fun showQrContactDialog() {
        val ctx = requireContext()
        val dialogView = layoutInflater.inflate(com.nexlink.app.R.layout.dialog_qr_contact, null)
        val dialog = MaterialAlertDialogBuilder(ctx).setView(dialogView).create()

        val ivQr         = dialogView.findViewById<android.widget.ImageView>(com.nexlink.app.R.id.ivQrCode)
        val tvQrTitle    = dialogView.findViewById<android.widget.TextView>(com.nexlink.app.R.id.tvQrTitle)
        val tvIndex      = dialogView.findViewById<android.widget.TextView>(com.nexlink.app.R.id.tvContactIndex)
        val btnPrev      = dialogView.findViewById<android.widget.ImageButton>(com.nexlink.app.R.id.btnPrev)
        val btnNext      = dialogView.findViewById<android.widget.ImageButton>(com.nexlink.app.R.id.btnNext)
        val btnAdd       = dialogView.findViewById<android.widget.ImageButton>(com.nexlink.app.R.id.btnAddContact)
        val rowDetails   = dialogView.findViewById<android.view.View>(com.nexlink.app.R.id.rowDetails)
        val ivToggleArrow= dialogView.findViewById<android.widget.ImageView>(com.nexlink.app.R.id.ivDetailsArrow)
        val layoutDetails= dialogView.findViewById<android.view.View>(com.nexlink.app.R.id.layoutDetails)
        val etTitle      = dialogView.findViewById<android.widget.EditText>(com.nexlink.app.R.id.etTitle)
        val etFirst      = dialogView.findViewById<android.widget.EditText>(com.nexlink.app.R.id.etFirstName)
        val etLast       = dialogView.findViewById<android.widget.EditText>(com.nexlink.app.R.id.etLastName)
        val etPhone      = dialogView.findViewById<android.widget.EditText>(com.nexlink.app.R.id.etPhone)
        val etEmail      = dialogView.findViewById<android.widget.EditText>(com.nexlink.app.R.id.etEmail)
        val btnSave      = dialogView.findViewById<android.widget.Button>(com.nexlink.app.R.id.btnSaveContact)
        val btnDelete    = dialogView.findViewById<android.widget.Button>(com.nexlink.app.R.id.btnDeleteContact)

        val prefs = ctx.getSharedPreferences("nx_qr_contact", android.content.Context.MODE_PRIVATE)
        val emptyContact = mapOf("title" to "", "first" to "", "last" to "", "phone" to "", "email" to "")
        val contacts = loadQrContacts(prefs).toMutableList()
        if (contacts.isEmpty()) contacts.add(emptyContact)
        var currentIndex = 0

        fun generateQr(vcard: String) {
            try {
                val writer = com.google.zxing.qrcode.QRCodeWriter()
                val matrix = writer.encode(vcard, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512)
                val bmp = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.RGB_565)
                for (x in 0 until 512) for (y in 0 until 512)
                    bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                ivQr.setImageBitmap(bmp)
            } catch (e: Exception) {
                Toast.makeText(ctx, "QR error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        fun buildVcard(c: Map<String, String>) = buildString {
            appendLine("BEGIN:VCARD"); appendLine("VERSION:3.0")
            val name = "${c["first"].orEmpty()} ${c["last"].orEmpty()}".trim()
            if (name.isNotEmpty()) appendLine("FN:$name")
            val phone = c["phone"].orEmpty()
            if (phone.isNotEmpty()) appendLine("TEL:$phone")
            val email = c["email"].orEmpty()
            if (email.isNotEmpty()) appendLine("EMAIL:$email")
            appendLine("END:VCARD")
        }

        fun refresh() {
            val c = contacts[currentIndex]
            tvIndex.text = "${currentIndex + 1} / ${contacts.size}"
            btnPrev.isEnabled = currentIndex > 0
            btnNext.isEnabled = currentIndex < contacts.size - 1
            val title = c["title"].orEmpty()
            tvQrTitle.text = title
            tvQrTitle.visibility = if (title.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            etTitle.setText(title)
            etFirst.setText(c["first"].orEmpty())
            etLast.setText(c["last"].orEmpty())
            etPhone.setText(c["phone"].orEmpty())
            etEmail.setText(c["email"].orEmpty())
            generateQr(buildVcard(c))
        }

        btnPrev.setOnClickListener { if (currentIndex > 0) { currentIndex--; refresh() } }
        btnNext.setOnClickListener { if (currentIndex < contacts.size - 1) { currentIndex++; refresh() } }
        btnAdd.setOnClickListener {
            contacts.add(emptyContact.toMutableMap())
            currentIndex = contacts.size - 1
            saveQrContacts(prefs, contacts)
            refresh()
        }

        var detailsVisible = false
        rowDetails.setOnClickListener {
            detailsVisible = !detailsVisible
            layoutDetails.visibility = if (detailsVisible) android.view.View.VISIBLE else android.view.View.GONE
            ivToggleArrow.setImageResource(
                if (detailsVisible) com.nexlink.app.R.drawable.ic_expand_less
                else com.nexlink.app.R.drawable.ic_expand_more
            )
        }

        btnSave.setOnClickListener {
            val updated = mapOf(
                "title" to etTitle.text.toString().trim(),
                "first" to etFirst.text.toString().trim(),
                "last"  to etLast.text.toString().trim(),
                "phone" to etPhone.text.toString().trim(),
                "email" to etEmail.text.toString().trim()
            )
            contacts[currentIndex] = updated
            saveQrContacts(prefs, contacts)
            val title = updated["title"].orEmpty()
            tvQrTitle.text = title
            tvQrTitle.visibility = if (title.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            generateQr(buildVcard(updated))
            Toast.makeText(ctx, "Contact saved", Toast.LENGTH_SHORT).show()
        }

        btnDelete.setOnClickListener {
            if (contacts.size == 1) {
                contacts[0] = emptyContact.toMutableMap()
            } else {
                contacts.removeAt(currentIndex)
                if (currentIndex >= contacts.size) currentIndex = contacts.size - 1
            }
            saveQrContacts(prefs, contacts)
            refresh()
        }

        refresh()
        dialog.show()
    }

    private fun loadQrContacts(prefs: android.content.SharedPreferences): List<Map<String, String>> {
        val json = prefs.getString("contacts_list", null)
        if (json != null) {
            return try {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    mapOf("title" to o.optString("title"), "first" to o.optString("first"),
                          "last"  to o.optString("last"),  "phone" to o.optString("phone"),
                          "email" to o.optString("email"))
                }
            } catch (_: Exception) { emptyList() }
        }
        // Migrate legacy single-contact prefs
        val first = prefs.getString("first", "").orEmpty()
        val last  = prefs.getString("last",  "").orEmpty()
        val phone = prefs.getString("phone", "").orEmpty()
        val email = prefs.getString("email", "").orEmpty()
        if (first.isNotEmpty() || phone.isNotEmpty()) {
            return listOf(mapOf("title" to "", "first" to first, "last" to last, "phone" to phone, "email" to email))
        }
        return emptyList()
    }

    private fun saveQrContacts(prefs: android.content.SharedPreferences, contacts: List<Map<String, String>>) {
        val arr = org.json.JSONArray()
        contacts.forEach { c ->
            arr.put(org.json.JSONObject().apply {
                put("title", c["title"].orEmpty()); put("first", c["first"].orEmpty())
                put("last",  c["last"].orEmpty());  put("phone", c["phone"].orEmpty())
                put("email", c["email"].orEmpty())
            })
        }
        prefs.edit().putString("contacts_list", arr.toString()).apply()
    }

    private fun updatePermissionStatus() {
        val ctx = requireContext()
        fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

        b.statusSms.text      = if (granted(Manifest.permission.READ_SMS)) "Granted ✓" else "Not granted"
        b.statusContacts.text = if (granted(Manifest.permission.READ_CONTACTS)) "Granted ✓" else "Not granted"
        b.statusCalls.text    = if (granted(Manifest.permission.READ_CALL_LOG)) "Granted ✓" else "Not granted"
        b.statusPhone.text    = if (granted(Manifest.permission.CALL_PHONE)) "Granted ✓" else "Not granted"

        val enabledListeners = Settings.Secure.getString(
            ctx.contentResolver, "enabled_notification_listeners"
        ) ?: ""
        val listenerComp = ComponentName(ctx, NexLinkNotificationListener::class.java).flattenToString()
        b.statusNotif.text = if (listenerComp in enabledListeners) "Granted ✓" else "Not granted — tap button below"

        val accessibilityEnabled = try {
            val enabledServices = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val accessComp = ComponentName(ctx, com.nexlink.app.services.NexLinkAccessibilityService::class.java).flattenToString()
            accessComp in enabledServices
        } catch (_: Exception) { false }
        b.statusAccessibility.text = if (accessibilityEnabled) "Enabled ✓" else "Not enabled — tap button below"

        val isDefault = android.provider.Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName
        b.btnSetDefaultSms.visibility = if (isDefault) View.GONE else View.VISIBLE
    }

    // ── Dark mode ─────────────────────────────────────────────────────────────

    private fun refreshDarkModeLabel() {
        val ctx = context ?: return
        b.tvDarkModeValue.text = when (NotificationPrefs.getDarkMode(ctx)) {
            1    -> "Light"
            2    -> "Dark"
            else -> "System default"
        }
    }

    private fun showDarkModeDialog(anchor: View) {
        val ctx = requireContext()
        NexPopup.show(anchor, listOf("System default", "Light", "Dark").mapIndexed { i, label ->
            NexPopup.Item(label, R.drawable.ic_settings) {
                NotificationPrefs.setDarkMode(ctx, i)
                AppCompatDelegate.setDefaultNightMode(when (i) {
                    1    -> AppCompatDelegate.MODE_NIGHT_NO
                    2    -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                })
                refreshDarkModeLabel()
            }
        })
    }

    // ── Icon settings ─────────────────────────────────────────────────────────

    private val iconNames = arrayOf(
        "Default", "Design 1", "Design 2", "Design 3", "Design 4", "Design 5",
        "Design 6", "Design 7", "Design 8", "Design 9", "Design 10"
    )

    private val appIconDrawables = intArrayOf(
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher_1, R.mipmap.ic_launcher_2, R.mipmap.ic_launcher_3,
        R.mipmap.ic_launcher_4, R.mipmap.ic_launcher_5, R.mipmap.ic_launcher_6,
        R.mipmap.ic_launcher_7, R.mipmap.ic_launcher_8, R.mipmap.ic_launcher_9,
        R.mipmap.ic_launcher_10
    )

    private val notifIconDrawables = intArrayOf(
        R.drawable.ic_notif_nexlink,
        R.drawable.ic_notif_custom_1, R.drawable.ic_notif_custom_2, R.drawable.ic_notif_custom_3,
        R.drawable.ic_notif_custom_4, R.drawable.ic_notif_custom_5, R.drawable.ic_notif_custom_6,
        R.drawable.ic_notif_custom_7, R.drawable.ic_notif_custom_8, R.drawable.ic_notif_custom_9,
        R.drawable.ic_notif_custom_10
    )

    private fun refreshIconLabels() {
        val ctx = context ?: return
        b.tvAppIconValue.text   = iconNames[IconPrefs.getAppIconIndex(ctx)]
        b.tvNotifIconValue.text = iconNames[IconPrefs.getNotifIconIndex(ctx)]
    }

    private fun showIconPickerDialog(
        title: String,
        drawables: IntArray,
        current: Int,
        onSelect: (Int) -> Unit
    ) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        fun Int.px() = (this * dp).toInt()

        val grid = android.widget.GridView(ctx).apply {
            numColumns = 3
            setPadding(8.px(), 8.px(), 8.px(), 8.px())
            horizontalSpacing = 4.px()
            verticalSpacing = 4.px()
        }

        var dialog: AlertDialog? = null

        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = drawables.size
            override fun getItem(i: Int) = drawables[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
                val cell = android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setPadding(8.px(), 12.px(), 8.px(), 8.px())
                    if (pos == current) {
                        background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = 12f * dp
                            setStroke((2f * dp).toInt(), ContextCompat.getColor(ctx, R.color.accent))
                            setColor(ContextCompat.getColor(ctx, R.color.surface2))
                        }
                    }
                }
                val iv = android.widget.ImageView(ctx).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(52.px(), 52.px())
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    try { setImageResource(drawables[pos]) } catch (_: Exception) {}
                }
                val tv = android.widget.TextView(ctx).apply {
                    text = iconNames[pos]
                    textSize = 11f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(ContextCompat.getColor(ctx, if (pos == current) R.color.accent else R.color.text2))
                    setPadding(0, 4.px(), 0, 0)
                }
                cell.addView(iv)
                cell.addView(tv)
                return cell
            }
        }

        grid.adapter = adapter

        dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(grid)
            .setNegativeButton("Cancel", null)
            .create()

        grid.setOnItemClickListener { _, _, pos, _ ->
            onSelect(pos)
            dialog?.dismiss()
        }

        dialog.show()
    }

    private fun showAppIconDialog() {
        val ctx = requireContext()
        val current = IconPrefs.getAppIconIndex(ctx)
        showIconPickerDialog("App Icon", appIconDrawables, current) { which ->
            IconPrefs.setAppIconIndex(ctx, which)
            switchAppIcon(ctx, which)
            if (IconPrefs.getNotifIconIndex(ctx) == 0 || IconPrefs.getNotifIconIndex(ctx) == current) {
                IconPrefs.setNotifIconIndex(ctx, which)
            }
            refreshIconLabels()
            Toast.makeText(ctx, "Icon changed — may take a moment to update on home screen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNotifIconDialog() {
        val ctx = requireContext()
        val current = IconPrefs.getNotifIconIndex(ctx)
        showIconPickerDialog("Notification Icon", notifIconDrawables, current) { which ->
            IconPrefs.setNotifIconIndex(ctx, which)
            refreshIconLabels()
        }
    }

    private fun switchAppIcon(ctx: android.content.Context, index: Int) {
        val pm = ctx.packageManager
        val pkg = ctx.packageName        // applicationId  = com.thvjq.nexlink
        val ns  = "com.nexlink.app"      // namespace / class prefix
        val allAliases = listOf(".MainActivityDefault") +
            (1..10).map { ".MainActivityIcon$it" }
        val target = if (index == 0) ".MainActivityDefault" else ".MainActivityIcon$index"
        allAliases.forEach { alias ->
            val comp = ComponentName(pkg, ns + alias)  // pkg=identity, class=namespace+name
            val state = if (alias == target)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            try { pm.setComponentEnabledSetting(comp, state, PackageManager.DONT_KILL_APP) }
            catch (_: Exception) {}
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
