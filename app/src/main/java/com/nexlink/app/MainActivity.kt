package com.nexlink.app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.nexlink.app.services.NexLinkNotificationListener
import com.nexlink.app.databinding.ActivityMainBinding
import com.nexlink.app.ui.calls.CallsFragment
import com.nexlink.app.ui.contacts.ContactsFragment
import com.nexlink.app.ui.inbox.InboxFragment
import com.nexlink.app.ui.settings.SettingsFragment
import com.nexlink.app.ui.sms.SmsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val smsFragment      = SmsFragment()
    private val inboxFragment    = InboxFragment()
    private val callsFragment    = CallsFragment()
    private val contactsFragment = ContactsFragment()
    private val settingsFragment = SettingsFragment()

    companion object {
        private val REQUIRED_PERMS = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestMissingPermissions()
        promptDefaultSmsApp()
        promptDefaultDialer()
        promptNotificationAccess()

        loadFragment(inboxFragment)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_sms      -> { loadFragment(smsFragment);      true }
                R.id.nav_inbox    -> { loadFragment(inboxFragment);     true }
                R.id.nav_calls    -> { loadFragment(callsFragment);     true }
                R.id.nav_contacts -> { loadFragment(contactsFragment);  true }
                R.id.nav_settings -> { loadFragment(settingsFragment);  true }
                else -> false
            }
        }
        binding.bottomNav.selectedItemId = R.id.nav_inbox
    }

    private fun loadFragment(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, f)
            .commit()
    }

    private fun requestMissingPermissions() {
        val missing = REQUIRED_PERMS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 0)
    }

    private fun promptNotificationAccess() {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val comp = ComponentName(this, NexLinkNotificationListener::class.java).flattenToString()
        if (comp in enabled) return
        AlertDialog.Builder(this)
            .setTitle("Allow Notification Access")
            .setMessage(
                "NexLink needs Notification Access to show messages from WhatsApp, " +
                "Signal, Telegram and Messenger in your Inbox.\n\n" +
                "Tap Grant, then enable NexLink in the list."
            )
            .setPositiveButton("Grant") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("Not Now", null)
            .show()
    }

    private fun promptDefaultDialer() {
        val tm = getSystemService(TelecomManager::class.java)
        if (tm.defaultDialerPackage == packageName) return
        startActivity(
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
        )
    }

    private fun promptDefaultSmsApp() {
        if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) return
        AlertDialog.Builder(this)
            .setTitle("Set NexLink as Default SMS App")
            .setMessage("To receive SMS notifications and see new messages here, set NexLink as your default SMS app.")
            .setPositiveButton("Set as Default") { _, _ ->
                startActivity(
                    Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                        .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                )
            }
            .setNegativeButton("Not Now", null)
            .show()
    }
}
