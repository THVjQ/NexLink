package com.nexlink.app.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import android.provider.Telephony
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexlink.app.R
import com.nexlink.app.db.DebugLog
import com.nexlink.app.db.NotificationStore
import com.nexlink.app.db.SmsHelper
import com.nexlink.app.ui.applySystemBarInsetsPadding
import com.nexlink.app.services.NexLinkAccessibilityService
import com.nexlink.app.services.NexLinkNotificationListener

/**
 * Full in-app debug console reachable from Settings ▸ Developer / Debug.
 *
 * Shows the live message pipeline (everything captured by [DebugLog]) with per-category
 * filtering, plus a diagnostics snapshot (device, permissions, default-SMS state, telephony
 * counts, encryption sessions) and copy / share / clear actions.
 */
class DebugActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var logContainer: LinearLayout
    private lateinit var diagContainer: LinearLayout
    private val chipButtons = mutableMapOf<String, TextView>()
    private var activeFilter: String = FILTER_ALL
    private var searchQuery: String = ""
    private var printWebView: WebView? = null   // held so the print job survives

    private val dp by lazy { resources.displayMetrics.density }
    private fun Int.px() = (this * dp).toInt()

    companion object { private const val FILTER_ALL = "ALL" }

    private fun col(id: Int) = ContextCompat.getColor(this, id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Debug"

        val scroll = ScrollView(this).apply {
            setBackgroundColor(col(R.color.bg))
            isFillViewport = true
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.px(), 16.px(), 16.px(), 24.px())
        }
        scroll.addView(root)
        setContentView(scroll)
        root.applySystemBarInsetsPadding()

        buildActionRow()
        buildSearchBox()
        buildFilterChips()

        root.addView(sectionHeader("PIPELINE EVENTS"))
        logContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(logContainer)

        root.addView(sectionHeader("DIAGNOSTICS"))
        diagContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(diagContainer)

        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    // ── Top actions ────────────────────────────────────────────────────────────

    private fun buildActionRow() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        fun action(label: String, onClick: () -> Unit): Button = Button(this).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            maxLines = 1
            minWidth = 0; minimumWidth = 0
            setPadding(4.px(), 0, 4.px(), 0)
            backgroundTintList = ContextCompat.getColorStateList(this@DebugActivity, R.color.surface2)
            setTextColor(col(R.color.text))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = 5.px() }
            setOnClickListener { onClick() }
        }
        row.addView(action("Refresh") { refresh() })
        row.addView(action("Copy") { copyLog() })
        row.addView(action("Share") { shareLog() })
        row.addView(action("Print") { printLog() })
        row.addView(action("Clear") { confirmClear() }.also {
            (it.layoutParams as LinearLayout.LayoutParams).marginEnd = 0
        })
        root.addView(row)
    }

    /** Live search box — filters the pipeline events after every keystroke (§ user request). */
    private fun buildSearchBox() {
        val box = EditText(this).apply {
            hint = "Search events — text, number, category…"
            setText(searchQuery)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = true
            textSize = 14f
            setTextColor(col(R.color.text)); setHintTextColor(col(R.color.muted))
            background = GradientDrawable().apply {
                cornerRadius = 10f * dp; setColor(col(R.color.surface)); setStroke(1, col(R.color.divider))
            }
            setPadding(14.px(), 10.px(), 14.px(), 10.px())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.px() }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    searchQuery = s?.toString()?.trim().orEmpty()
                    renderLog()   // re-filter only the log list; leave focus in the box
                }
            })
        }
        root.addView(box)
    }

    private fun printLog() {
        val html = "<html><body style='font-family:monospace;font-size:11px;white-space:pre-wrap'>" +
            TextUtils.htmlEncode(DebugLog.dump(this)) + "</body></html>"
        val wv = WebView(this)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = view.createPrintDocumentAdapter("NexLink-debug-log")
                pm.print("NexLink debug log", adapter, PrintAttributes.Builder().build())
                printWebView = null   // release after handing off to the print framework
            }
        }
        printWebView = wv
        wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun copyLog() {
        val text = DebugLog.dump(this)
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("NexLink debug log", text))
        Toast.makeText(this, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun shareLog() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NexLink debug log")
            putExtra(Intent.EXTRA_TEXT, DebugLog.dump(this@DebugActivity))
        }
        startActivity(Intent.createChooser(send, "Share debug log"))
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear debug log?")
            .setMessage("This removes all captured pipeline events. Diagnostics are unaffected.")
            .setPositiveButton("Clear") { _, _ ->
                DebugLog.clear(this); refresh()
                Toast.makeText(this, "Debug log cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Filter chips ───────────────────────────────────────────────────────────

    private fun buildFilterChips() {
        val hs = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 10.px(), 0, 4.px())
        }
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hs.addView(strip)

        (listOf(FILTER_ALL) + DebugLog.ALL_CATEGORIES).forEach { cat ->
            val chip = TextView(this).apply {
                text = if (cat == FILTER_ALL) "All" else cat
                textSize = 12f
                setPadding(14.px(), 7.px(), 14.px(), 7.px())
                setOnClickListener { activeFilter = cat; refresh() }
            }
            chipButtons[cat] = chip
            strip.addView(chip)
            (chip.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                .also { it.marginEnd = 6.px(); chip.layoutParams = it }
        }
        root.addView(hs)
    }

    private fun styleChips() {
        chipButtons.forEach { (cat, chip) ->
            val active = cat == activeFilter
            chip.background = GradientDrawable().apply {
                cornerRadius = 20f * dp
                setColor(if (active) col(R.color.accent) else col(R.color.surface2))
            }
            chip.setTextColor(if (active) Color.WHITE else col(R.color.text2))
            chip.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    private fun refresh() {
        styleChips()
        renderLog()
        renderDiagnostics()
    }

    private fun renderLog() {
        logContainer.removeAllViews()
        val all = DebugLog.all(this)
        val q = searchQuery.lowercase()
        val filtered = all
            .filter { activeFilter == FILTER_ALL || it.category == activeFilter }
            .filter { q.isEmpty() ||
                it.tag.lowercase().contains(q) ||
                it.message.lowercase().contains(q) ||
                it.category.lowercase().contains(q) }

        // Counts summary line
        val counts = DebugLog.ALL_CATEGORIES.associateWith { c -> all.count { it.category == c } }
        logContainer.addView(TextView(this).apply {
            text = "sent ${counts[DebugLog.CAT_SENT]} · received ${counts[DebugLog.CAT_RECEIVED]} · " +
                   "shown ${counts[DebugLog.CAT_SHOWN]} · errors ${counts[DebugLog.CAT_ERROR]} · " +
                   "total ${all.size}"
            textSize = 11f
            setTextColor(col(R.color.muted))
            setPadding(2.px(), 0, 0, 8.px())
        })

        if (filtered.isEmpty()) {
            logContainer.addView(TextView(this).apply {
                text = if (searchQuery.isNotEmpty() || activeFilter != FILTER_ALL)
                    "No events match your search/filter."
                else
                    "No events yet. Send or receive a message, or wait for a social notification."
                textSize = 13f
                setTextColor(col(R.color.muted))
                setPadding(4.px(), 12.px(), 4.px(), 12.px())
            })
            return
        }

        filtered.forEach { e ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = 10f * dp
                    setColor(col(R.color.surface))
                }
                setPadding(12.px(), 9.px(), 12.px(), 9.px())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = 6.px() }
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(TextView(this).apply {
                text = e.category
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(7.px(), 2.px(), 7.px(), 2.px())
                background = GradientDrawable().apply {
                    cornerRadius = 6f * dp
                    setColor(categoryColor(e.category))
                }
            })
            header.addView(TextView(this).apply {
                text = "  ${e.tag}"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(col(R.color.text))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                maxLines = 1
            })
            header.addView(TextView(this).apply {
                text = DebugLog.formatTime(e.timestamp)
                textSize = 10f
                setTextColor(col(R.color.muted))
            })
            card.addView(header)
            card.addView(TextView(this).apply {
                text = e.message
                textSize = 12f
                setTextColor(col(R.color.text2))
                setPadding(0, 3.px(), 0, 0)
            })
            logContainer.addView(card)
        }
    }

    private fun categoryColor(cat: String): Int = when (cat) {
        DebugLog.CAT_SENT     -> 0xFF2E7D32.toInt()
        DebugLog.CAT_RECEIVED -> 0xFF1565C0.toInt()
        DebugLog.CAT_SHOWN    -> 0xFF6A1B9A.toInt()
        DebugLog.CAT_MMS      -> 0xFF00838F.toInt()
        DebugLog.CAT_CRYPTO   -> 0xFFAD6800.toInt()
        DebugLog.CAT_ERROR    -> 0xFFC62828.toInt()
        else                  -> 0xFF546E7A.toInt()
    }

    // ── Diagnostics ────────────────────────────────────────────────────────────

    private fun renderDiagnostics() {
        diagContainer.removeAllViews()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 10f * dp
                setColor(col(R.color.surface))
            }
            setPadding(14.px(), 12.px(), 14.px(), 12.px())
        }
        diagContainer.addView(card)

        fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
        fun yn(b: Boolean) = if (b) "yes ✓" else "no ✗"

        // App / device
        val pkgInfo = try { packageManager.getPackageInfo(packageName, 0) } catch (_: Exception) { null }
        val versionName = pkgInfo?.versionName ?: "?"
        @Suppress("DEPRECATION")
        val versionCode = pkgInfo?.let {
            if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong()
        } ?: 0L

        addDiag(card, "App version", "$versionName ($versionCode)")
        addDiag(card, "Application id", packageName)
        addDiag(card, "Device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        addDiag(card, "Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")

        addDivider(card)

        // Default SMS + roles
        val isDefault = SmsHelper.isDefaultSmsApp(this)
        addDiag(card, "Default SMS app", yn(isDefault), if (isDefault) R.color.text else R.color.accent2)
        val defaultPkg = try { Telephony.Sms.getDefaultSmsPackage(this) } catch (_: Exception) { null }
        addDiag(card, "System default pkg", defaultPkg ?: "unknown")

        // Listener + accessibility
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val listenerOn = ComponentName(this, NexLinkNotificationListener::class.java).flattenToString() in enabledListeners
        addDiag(card, "Notification listener", yn(listenerOn), if (listenerOn) R.color.text else R.color.accent2)

        val enabledA11y = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val a11yOn = ComponentName(this, NexLinkAccessibilityService::class.java).flattenToString() in enabledA11y
        addDiag(card, "Accessibility (sent capture)", yn(a11yOn), if (a11yOn) R.color.text else R.color.accent2)

        addDivider(card)

        // Permissions
        addDiag(card, "READ_SMS", yn(granted(Manifest.permission.READ_SMS)))
        addDiag(card, "SEND_SMS", yn(granted(Manifest.permission.SEND_SMS)))
        addDiag(card, "RECEIVE_SMS", yn(granted(Manifest.permission.RECEIVE_SMS)))
        addDiag(card, "READ_CONTACTS", yn(granted(Manifest.permission.READ_CONTACTS)))
        addDiag(card, "READ_PHONE_STATE", yn(granted(Manifest.permission.READ_PHONE_STATE)))
        addDiag(card, "POST_NOTIFICATIONS",
            yn(android.os.Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)))

        addDivider(card)

        // Telephony store counts
        val sentCount  = telephonyCount(Telephony.Sms.Sent.CONTENT_URI)
        val inboxCount = telephonyCount(Telephony.Sms.Inbox.CONTENT_URI)
        val mmsCount   = telephonyCount(Telephony.Mms.CONTENT_URI)
        addDiag(card, "SMS sent box", sentCount?.toString() ?: "n/a")
        addDiag(card, "SMS inbox", inboxCount?.toString() ?: "n/a")
        addDiag(card, "MMS messages", mmsCount?.toString() ?: "n/a")

        // Social notifications shown
        val social = NotificationStore.notifications.value.orEmpty()
        addDiag(card, "Social notifs stored", social.size.toString())
        val byPlatform = social.groupingBy { it.platform }.eachCount()
            .entries.sortedByDescending { it.value }
            .joinToString(", ") { "${it.key} ${it.value}" }
        if (byPlatform.isNotEmpty()) addDiag(card, "  by platform", byPlatform)

        addDivider(card)

        // SIMs
        val sims = SmsHelper.getSims(this)
        addDiag(card, "Active SIMs", sims.size.toString())
        sims.forEach { addDiag(card, "  SIM ${it.slotIndex + 1}", "${it.displayName}${it.number?.let { n -> " · $n" } ?: ""}") }

        // Encryption sessions
        addDivider(card)
        addDiag(card, "Encryption enabled",
            yn(com.nexlink.app.db.NotificationPrefs.isEncryptionEnabled(this)))
        addDiag(card, "Key exchange promo",
            yn(com.nexlink.app.db.NotificationPrefs.isKeyExchangePromoEnabled(this)))
    }

    private fun telephonyCount(uri: android.net.Uri): Int? = try {
        contentResolver.query(uri, arrayOf("_id"), null, null, null)?.use { it.count }
    } catch (_: Exception) { null }

    private fun addDiag(parent: LinearLayout, key: String, value: String, valueColor: Int = R.color.text) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4.px(), 0, 4.px())
        }
        row.addView(TextView(this).apply {
            text = key
            textSize = 12f
            setTextColor(col(R.color.muted))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f)
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setTextColor(col(valueColor))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        parent.addView(row)
    }

    private fun addDivider(parent: LinearLayout) {
        parent.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                .apply { topMargin = 6.px(); bottomMargin = 6.px() }
            setBackgroundColor(col(R.color.divider))
        })
    }

    private fun sectionHeader(text: String) = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTypeface(null, Typeface.BOLD)
        letterSpacing = 0.09f
        setTextColor(col(R.color.muted))
        setPadding(2.px(), 18.px(), 0, 8.px())
    }
}
