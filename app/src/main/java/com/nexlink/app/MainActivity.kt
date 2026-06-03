package com.nexlink.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nexlink.app.databinding.ActivityMainBinding
import com.nexlink.app.ui.calls.CallsFragment
import com.nexlink.app.ui.inbox.InboxFragment
import com.nexlink.app.ui.settings.SettingsFragment
import com.nexlink.app.ui.sms.SmsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val smsFragment     = SmsFragment()
    private val inboxFragment   = InboxFragment()
    private val callsFragment   = CallsFragment()
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

        loadFragment(inboxFragment)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_sms      -> { loadFragment(smsFragment);      true }
                R.id.nav_inbox    -> { loadFragment(inboxFragment);     true }
                R.id.nav_calls    -> { loadFragment(callsFragment);     true }
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
}
