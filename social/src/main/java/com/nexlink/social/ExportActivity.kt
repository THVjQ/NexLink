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
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) }
            setOnClickListener { runExport() }
        })
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
