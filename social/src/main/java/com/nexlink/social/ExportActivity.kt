package com.nexlink.social

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.nexlink.social.core.transfer.Passphrase
import com.nexlink.social.core.transfer.TransferArchive
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexlink.social.core.Export
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.nexlink.social.ui.R as UiR

/**
 * Export your messages — §12.7.2, §32.4.
 *
 * §32.4 is the reason this exists: the operator cannot produce message content
 * for a subject access request, because it has never been able to read it. The
 * honest answer to "send me my messages" is *"we cannot, and here is the button
 * that can"*. §1.2's portability commitment is the same button.
 */
class ExportActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private var status: String? = null
    private var pending: File? = null
    private var pendingBackup: File? = null

    /** §7.5.2 — the encrypted archive. Same "user chooses where" rule. */
    private val saveBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val src = pendingBackup
        if (uri == null || src == null) {
            status = "Backup cancelled."; src?.delete(); render()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        src.inputStream().use { it.copyTo(out) }
                    }
                    // Encrypted, but still not left lying in the cache.
                    src.delete()
                }
            }.onSuccess { status = "Backup saved." }
             .onFailure { status = "Couldn't save the backup: ${it.message}" }
            render()
        }
    }

    /** The user chooses where it lands; the app keeps no copy. */
    private val save = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val src = pending
        if (uri == null || src == null) { status = "Export cancelled."; render(); return@registerForActivityResult }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        src.inputStream().use { it.copyTo(out) }
                    }
                    // §12.6.1 — do not leave a plaintext copy behind in the cache.
                    src.delete()
                }
            }.onSuccess { status = "Saved." }.onFailure { status = "Couldn't save: ${it.message}" }
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        // §14.10 — see Insets.kt.
        root.padForSystemBars(dp(20), dp(24), dp(24))
        render()
    }

    private fun render() {
        root.removeAllViews()
        root.addView(text("Export your messages", 26f, UiR.color.social_text, bold = true))
        root.addView(text(
            "Saves every conversation this phone can read, as a JSON file you " +
            "choose the location for.\n\n" +
            "We can't do this for you from our side — your messages are " +
            "encrypted and the server has only ever held scrambled data. This " +
            "phone is the only place they can be read.\n\n" +
            "The file is not encrypted. Anyone who opens it can read your " +
            "messages, so put it somewhere you trust.",
            15f, UiR.color.social_text2))
        status?.let { root.addView(gap(12)); root.addView(text(it, 14f, UiR.color.social_accent)) }
        root.addView(Button(this).apply {
            text = "Export"
            isAllCaps = false
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) }
            setOnClickListener { runExport() }
        })

        // §7.5.2 — the other file, and a deliberately different one.
        root.addView(gap(24))
        root.addView(text("Backup file", 20f, UiR.color.social_text, bold = true))
        root.addView(text(
            "A second kind of file, for moving to a new phone or keeping a " +
            "backup. It holds this phone's whole encrypted store — including " +
            "the keys that decrypt your history — so it is locked with a " +
            "passphrase you choose.\n\n" +
            "Don't use your recovery key as the passphrase. If this file and " +
            "your recovery key ever end up in the same place, one of them " +
            "opens both.",
            15f, UiR.color.social_text2))
        root.addView(Button(this).apply {
            text = "Create a backup file"
            isAllCaps = false
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
            setOnClickListener { askPassphrase() }
        })
    }

    /**
     * §7.5.2 — collect the passphrase, and refuse a recovery key.
     *
     * The rule is enforced in `:social-core` by [Passphrase], not here, so that
     * it holds for any caller and is unit-testable. This screen only shows the
     * reason it gives.
     */
    private fun askPassphrase() {
        val pass = EditText(this).apply {
            hint = "Passphrase"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val again = EditText(this).apply {
            hint = "Passphrase again"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(pass); addView(again)
        }
        AlertDialog.Builder(this)
            .setTitle("Choose a passphrase")
            .setMessage(
                "There is no way to recover this file if you forget the " +
                "passphrase — it is not stored anywhere and we cannot reset it."
            )
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", null)
            .show()
            .also { dialog ->
                // Set the listener after show() so a rejected passphrase can
                // keep the dialog open instead of dismissing and losing what
                // the user typed.
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val p = pass.text.toString()
                    when (val v = Passphrase.check(p, again.text.toString())) {
                        is Passphrase.Verdict.Rejected -> {
                            pass.error = v.reason
                            status = v.reason
                            render()
                        }
                        Passphrase.Verdict.Ok -> {
                            dialog.dismiss()
                            runBackup(p.toCharArray())
                        }
                    }
                }
            }
    }

    /**
     * §7.5.2 — write the encrypted archive.
     *
     * `filesDir` holds the crypto store and the session; `cacheDir` holds the
     * event and media caches. Both go in: the caches are regenerable, but a
     * restore that has them is usable immediately instead of re-syncing
     * everything on a phone that may be on mobile data.
     */
    private fun runBackup(passphrase: CharArray) {
        status = "Packing…"; render()
        lifecycleScope.launch {
            val tmp = File(cacheDir, "nexlink-social-backup.nlsx")
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    tmp.outputStream().use { out ->
                        TransferArchive.write(
                            out, passphrase,
                            mapOf("files" to filesDir, "cache" to cacheDir),
                            onProgress = { }
                        )
                    }
                }
            }
            // The passphrase has done its work; do not leave it in memory for
            // the life of the activity.
            passphrase.fill('\u0000')
            r.onSuccess {
                pendingBackup = tmp
                status = "${it.files} files packed. Choose where to save."
                render()
                saveBackup.launch("nexlink-social-backup.nlsx")
            }.onFailure { status = "Couldn't create the backup: ${it.message}"; render() }
        }
    }

    private fun runExport() {
        status = "Collecting messages…"; render()
        lifecycleScope.launch {
            val s = SessionProvider.manager(this@ExportActivity).current() ?: return@launch
            val tmp = File(cacheDir, "nexlink-social-export.json")
            val r = withContext(Dispatchers.IO) {
                Export.exportAll(s, tmp) { room ->
                    lifecycleScope.launch { status = "Reading $room…"; render() }
                }
            }
            r.onSuccess {
                pending = tmp
                status = "${it.messages} messages from ${it.rooms} conversations. Choose where to save."
                render()
                save.launch("nexlink-social-export.json")
            }.onFailure { status = "Export failed: ${it.message}"; render() }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun gap(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(h))
    }
    private fun text(v: CharSequence, size: Float, c: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = v
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@ExportActivity, c))
            setLineSpacing(dp(4).toFloat(), 1f)
        }

    companion object {
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        fun intent(c: Context) = Intent(c, ExportActivity::class.java)
    }
}
