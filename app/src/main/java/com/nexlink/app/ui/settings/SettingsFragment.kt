package com.nexlink.app.ui.settings

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.nexlink.app.databinding.FragmentSettingsBinding
import com.nexlink.app.db.BlockStore
import com.nexlink.app.db.NotificationPrefs
import com.nexlink.app.db.RecycleBinStore
import com.nexlink.app.services.NexLinkNotificationListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private var socialsExpanded = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.btnNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        b.btnAppSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            })
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
                AlertDialog.Builder(ctx)
                    .setTitle("Disable Encryption?")
                    .setMessage("Messages will no longer be encrypted. This cannot be undone for existing sessions without re-sending a key exchange.")
                    .setPositiveButton("Disable") { _, _ -> NotificationPrefs.setEncryptionEnabled(ctx, false) }
                    .setNegativeButton("Cancel") { _, _ -> b.switchEncryption.isChecked = true }
                    .show()
            } else {
                NotificationPrefs.setEncryptionEnabled(ctx, true)
            }
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
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        refreshBlockedCount()
        refreshRecycleBinCount()

        // Refresh all switch states in case they were changed externally
        val ctx = context ?: return
        b.switchRcs.isChecked = NotificationPrefs.isRcsEnabled(ctx)
        b.switchEncryption.isChecked = NotificationPrefs.isEncryptionEnabled(ctx)
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
        val count = RecycleBinStore.getAll(ctx).size
        b.tvRecycleBinCount.text = if (count == 0) "Empty" else "$count deleted conversation${if (count != 1) "s" else ""}"
    }

    private fun showBlockedNumbersDialog() {
        val ctx = requireContext()
        val blocked = BlockStore.blockedSet(ctx).toList()
        if (blocked.isEmpty()) {
            AlertDialog.Builder(ctx)
                .setTitle("Blocked Numbers")
                .setMessage("No blocked numbers.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        AlertDialog.Builder(ctx)
            .setTitle("Blocked Numbers")
            .setItems(blocked.toTypedArray()) { _, i ->
                val num = blocked[i]
                AlertDialog.Builder(ctx)
                    .setTitle("Unblock $num?")
                    .setPositiveButton("Unblock") { _, _ ->
                        BlockStore.unblock(ctx, num)
                        refreshBlockedCount()
                        Toast.makeText(ctx, "Unblocked $num", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showRecycleBinDialog() {
        val ctx = requireContext()
        val deleted = RecycleBinStore.getAll(ctx)
        if (deleted.isEmpty()) {
            AlertDialog.Builder(ctx)
                .setTitle("Recycle Bin")
                .setMessage("The recycle bin is empty.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        val names = deleted.map { d ->
            "${d.contactName.ifBlank { d.address }} — deleted ${fmt.format(Date(d.deletedAt))}"
        }.toTypedArray()
        AlertDialog.Builder(ctx)
            .setTitle("Recycle Bin")
            .setItems(names) { _, i ->
                val d = deleted[i]
                AlertDialog.Builder(ctx)
                    .setTitle(d.contactName.ifBlank { d.address })
                    .setItems(arrayOf("Restore", "Delete permanently")) { _, which ->
                        if (which == 0) {
                            // Restore: just remove from recycle bin; the thread may still exist in SMS DB
                            RecycleBinStore.remove(ctx, d.threadId)
                            refreshRecycleBinCount()
                            Toast.makeText(ctx, "Conversation restored", Toast.LENGTH_SHORT).show()
                        } else {
                            RecycleBinStore.remove(ctx, d.threadId)
                            refreshRecycleBinCount()
                            Toast.makeText(ctx, "Permanently deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNeutralButton("Clear all") { _, _ ->
                AlertDialog.Builder(ctx)
                    .setTitle("Clear recycle bin?")
                    .setMessage("All deleted conversations will be permanently removed.")
                    .setPositiveButton("Clear") { _, _ ->
                        RecycleBinStore.clear(ctx)
                        refreshRecycleBinCount()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showQrContactDialog() {
        val ctx = requireContext()
        val dialogView = layoutInflater.inflate(com.nexlink.app.R.layout.dialog_qr_contact, null)
        val etFirst = dialogView.findViewById<android.widget.EditText>(com.nexlink.app.R.id.etFirstName)
        val etLast  = dialogView.findViewById<android.widget.EditText>(com.nexlink.app.R.id.etLastName)
        val etPhone = dialogView.findViewById<android.widget.EditText>(com.nexlink.app.R.id.etPhone)
        val etEmail = dialogView.findViewById<android.widget.EditText>(com.nexlink.app.R.id.etEmail)
        val btnGen  = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.nexlink.app.R.id.btnGenerateQr)
        val ivQr    = dialogView.findViewById<android.widget.ImageView>(com.nexlink.app.R.id.ivQrCode)

        val dialog = AlertDialog.Builder(ctx).setView(dialogView).create()

        btnGen.setOnClickListener {
            val vcard = buildString {
                appendLine("BEGIN:VCARD"); appendLine("VERSION:3.0")
                val name = "${etFirst.text} ${etLast.text}".trim()
                if (name.isNotEmpty()) appendLine("FN:$name")
                val phone = etPhone.text.toString().trim()
                if (phone.isNotEmpty()) appendLine("TEL:$phone")
                val email = etEmail.text.toString().trim()
                if (email.isNotEmpty()) appendLine("EMAIL:$email")
                appendLine("END:VCARD")
            }
            try {
                val writer = com.google.zxing.qrcode.QRCodeWriter()
                val matrix = writer.encode(vcard, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512)
                val bmp = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.RGB_565)
                for (x in 0 until 512) for (y in 0 until 512)
                    bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                ivQr.setImageBitmap(bmp)
                ivQr.visibility = android.view.View.VISIBLE
            } catch (e: Exception) {
                Toast.makeText(ctx, "Failed to generate QR: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
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
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
