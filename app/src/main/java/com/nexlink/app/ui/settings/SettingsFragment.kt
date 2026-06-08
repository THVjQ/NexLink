package com.nexlink.app.ui.settings

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.nexlink.app.databinding.FragmentSettingsBinding
import com.nexlink.app.db.NotificationPrefs
import com.nexlink.app.services.NexLinkNotificationListener

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

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
            val body = buildString {
                appendLine("Bug Report — NexLink")
                appendLine("====================")
                appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine()
                appendLine("Describe the bug:")
                appendLine()
                appendLine("Steps to reproduce:")
                appendLine()
                appendLine("Expected behaviour:")
            }
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("google.alumni829@passmail.net"))
                putExtra(Intent.EXTRA_SUBJECT, "NexLink Bug Report")
                putExtra(Intent.EXTRA_TEXT, body)
            }
            startActivity(intent)
        }

        val ctx = requireContext()

        // RCS toggle
        b.switchRcs.isChecked = NotificationPrefs.isRcsEnabled(ctx)
        b.switchRcs.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefs.setRcsEnabled(ctx, isChecked)
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
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()

        // Refresh all switch states in case they were changed externally
        val ctx = context ?: return
        b.switchRcs.isChecked = NotificationPrefs.isRcsEnabled(ctx)
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
