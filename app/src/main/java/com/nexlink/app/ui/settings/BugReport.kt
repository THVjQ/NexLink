package com.nexlink.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.nexlink.app.db.BridgePrefs
import com.nexlink.app.db.SmsHelper
import com.nexlink.app.services.NexLinkNotificationListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Report a Bug" — opens the user's own email app with a pre-filled report, so issues
 * arrive by email instead of as GitHub issues. Nothing is sent until the user taps Send.
 */
object BugReport {

    /** Where reports go. Compiled into the APK, so it is public. */
    const val ADDRESS = "google.alumni829@passmail.net"

    fun send(ctx: Context) {
        val subject = "NexLink bug report (${versionString(ctx)})"
        val body = template(ctx)

        // Subject/body go in the mailto URI as well as the extras: some mail apps
        // read only one or the other.
        val uri = Uri.parse(
            "mailto:$ADDRESS?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
        )
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(ADDRESS))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            ctx.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No email app — hand the report over by clipboard instead of failing silently.
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(subject, body))
            Toast.makeText(
                ctx,
                "No email app found. Report copied — please email it to $ADDRESS",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun versionString(ctx: Context): String {
        val info = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0) } catch (_: Exception) { null }
        @Suppress("DEPRECATION")
        val code = info?.let {
            if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong()
        } ?: 0L
        return "${info?.versionName ?: "?"} ($code)"
    }

    private fun template(ctx: Context): String {
        val enabledListeners = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        val listenerOn = ComponentName(ctx, NexLinkNotificationListener::class.java)
            .flattenToString() in enabledListeners
        val bridge = when {
            BridgePrefs.isEnabled(ctx) && BridgePrefs.isLinked(ctx) -> "connected"
            BridgePrefs.getServerUrl(ctx).isNotBlank() -> "set up, not linked"
            else -> "off"
        }
        fun yn(b: Boolean) = if (b) "yes" else "no"

        return buildString {
            appendLine("What happened:")
            appendLine()
            appendLine()
            appendLine("Steps to reproduce:")
            appendLine("1. ")
            appendLine("2. ")
            appendLine("3. ")
            appendLine()
            appendLine("What you expected to happen:")
            appendLine()
            appendLine()
            appendLine("How often does it happen? (every time / sometimes / once)")
            appendLine()
            appendLine()
            appendLine("Screenshots: attach any that help.")
            appendLine()
            appendLine("---- Device info (please leave this in) ----")
            appendLine("App version: ${versionString(ctx)}")
            appendLine("App id: ${ctx.packageName}")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Default SMS app: ${yn(SmsHelper.isDefaultSmsApp(ctx))}")
            appendLine("Notification listener: ${yn(listenerOn)}")
            appendLine("Computer bridge: $bridge")
            appendLine("Language: ${Locale.getDefault().toLanguageTag()}")
            appendLine("Reported: ${SimpleDateFormat("yyyy-MM-dd HH:mm Z", Locale.US).format(Date())}")
        }
    }
}
