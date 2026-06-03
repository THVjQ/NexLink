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
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
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
