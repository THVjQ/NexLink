package com.nexlink.app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.nexlink.app.db.NotificationPrefs
import com.nexlink.app.db.NotificationStore
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

    // Tab back-stack: Back pops one visited tab at a time (e.g. Signal→Inbox→SMS) instead of
    // jumping straight to SMS, and only exits the app once history is empty.
    private val navHistory = ArrayDeque<Int>()
    private var currentNavId = R.id.nav_sms
    private var navigatingBack = false

    // Play's SMS/Call Log policy: the default-handler prompt must come BEFORE any runtime
    // permission request, and SMS / call-log permissions may only be asked for while NexLink
    // holds that role. Firing them together put the permission dialog on top of the role
    // prompt, which is what got 2.6.0 rejected. So first launch runs as a chain:
    // SMS role → dialer role → remaining runtime permissions → notification access.
    private val smsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { promptDefaultDialerApp() }

    private val dialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { requestMissingPermissions() }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { promptNotificationAccess() }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyDarkMode()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.fragmentContainer.setPadding(0, sys.top, 0, 0)
            insets
        }

        if (savedInstanceState == null) promptDefaultSmsApp()

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_sms      -> smsFragment
                R.id.nav_inbox    -> inboxFragment
                R.id.nav_calls    -> callsFragment
                R.id.nav_contacts -> contactsFragment
                R.id.nav_settings -> settingsFragment
                else -> null
            }
            if (fragment != null) {
                // Record the tab we're leaving so Back can return to it. Skipped when the
                // selection is itself a Back-pop, so we never re-push what we just popped.
                if (!navigatingBack && item.itemId != currentNavId) {
                    navHistory.addLast(currentNavId)
                }
                currentNavId = item.itemId
                loadFragment(fragment)
                true
            } else false
        }

        handleSocialNotifTap(intent)
        val startNav = intent.getIntExtra("navigate_to", R.id.nav_sms)
        // Load the initial fragment directly so there is never a double-load or Z-order race.
        loadFragment(when (startNav) {
            R.id.nav_inbox    -> inboxFragment
            R.id.nav_calls    -> callsFragment
            R.id.nav_contacts -> contactsFragment
            R.id.nav_settings -> settingsFragment
            else              -> smsFragment
        })
        currentNavId = startNav
        binding.bottomNav.selectedItemId = startNav
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (navHistory.isNotEmpty()) {
            // Step back exactly one tab through the visit history.
            val prev = navHistory.removeLast()
            navigatingBack = true
            binding.bottomNav.selectedItemId = prev
            navigatingBack = false
        } else {
            // At the root of the history stack — let Back close the app.
            super.onBackPressed()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSocialNotifTap(intent)
        val navTo = intent.getIntExtra("navigate_to", -1)
        if (navTo != -1) binding.bottomNav.selectedItemId = navTo
    }

    private fun handleSocialNotifTap(intent: Intent) {
        val platform = intent.getStringExtra("social_platform") ?: return
        val sender   = intent.getStringExtra("social_sender")   ?: ""
        val key      = intent.getStringExtra("social_key")      ?: ""
        NotificationStore.markRead(platform, sender)
        NexLinkNotificationListener.popContentIntent(key)
    }

    private fun applyDarkMode() {
        val mode = NotificationPrefs.getDarkMode(applicationContext)
        AppCompatDelegate.setDefaultNightMode(when (mode) {
            1    -> AppCompatDelegate.MODE_NIGHT_NO
            2    -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
    }

    private fun loadFragment(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, f)
            .commit()
    }

    private fun requestMissingPermissions() {
        val wanted = mutableListOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
        if (isDefaultSmsApp()) wanted += listOf(
            Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
        if (isDefaultDialer()) wanted += Manifest.permission.READ_CALL_LOG
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionsLauncher.launch(missing.toTypedArray())
        else promptNotificationAccess()
    }

    private fun promptNotificationAccess() {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val comp = ComponentName(this, NexLinkNotificationListener::class.java).flattenToString()
        if (comp in enabled) return
        MaterialAlertDialogBuilder(this)
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

    private fun promptDefaultSmsApp() {
        val prefs = getSharedPreferences("nexlink_prefs", MODE_PRIVATE)
        if (isDefaultSmsApp() || prefs.getBoolean("sms_prompt_shown", false)) {
            promptDefaultDialerApp(); return
        }
        prefs.edit().putBoolean("sms_prompt_shown", true).apply()
        requestDefaultSmsRole()
    }

    private fun promptDefaultDialerApp() {
        val prefs = getSharedPreferences("nexlink_prefs", MODE_PRIVATE)
        if (isDefaultDialer() || prefs.getBoolean("dialer_prompt_shown", false)) {
            requestMissingPermissions(); return
        }
        prefs.edit().putBoolean("dialer_prompt_shown", true).apply()
        if (!requestDefaultDialerRole()) requestMissingPermissions()
    }

    private fun isDefaultSmsApp() = com.nexlink.app.db.SmsHelper.isDefaultSmsApp(this)

    fun isDefaultDialer() =
        getSystemService(TelecomManager::class.java)?.defaultDialerPackage == packageName

    /** Returns false when no prompt could be shown, so the caller can carry on. */
    fun requestDefaultDialerRole(): Boolean {
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val rm = getSystemService(android.app.role.RoleManager::class.java)
            if (rm == null || !rm.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER) ||
                rm.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) return false
            rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
        } else {
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
        }
        return try { dialerRoleLauncher.launch(intent); true } catch (_: Exception) { false }
    }

    fun requestDefaultSmsRole() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val rm = getSystemService(android.app.role.RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS) &&
                !rm.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)) {
                smsRoleLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS))
                return
            }
        }
        try {
            smsRoleLauncher.launch(
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            )
        } catch (_: Exception) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                }
            )
        }
    }
}
