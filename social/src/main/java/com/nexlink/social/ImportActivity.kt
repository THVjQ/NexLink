package com.nexlink.social

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT as MATCH
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT as WRAP
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.nexlink.social.ui.chrome.Chrome
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexlink.social.core.SessionStore
import com.nexlink.social.core.transfer.TransferArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.nexlink.social.ui.R as UiR

/**
 * §7.5.2 — put an archive back.
 *
 * ## Why this exists as its own screen
 *
 * The archive has been writable since 2026-09-12 and §7.5.3 recorded honestly
 * that *"the half that is not built"* was the way back in. `TransferArchive.read`
 * and `SessionStore.importBundle` were both written and both tested — and
 * nothing in the app called either of them, so the feature was a file format
 * with no product attached. A backup that cannot be restored is not a backup;
 * it is a file that makes people feel safe.
 *
 * ## Two rules this screen enforces, and the reasoning for each
 *
 * **It is only reachable when signed out.** Restoring over a live session would
 * replace the store key and the credentials under a running client — §7.4.4's
 * failure mode, an account that looks signed in and can decrypt nothing.
 * Signing out first is not an inconvenience; it is what makes the restore
 * well-defined.
 *
 * **Nothing is moved into place until everything decrypted.** §7.5.3: *"a
 * partial store is worse than none, because it looks like a working account
 * with silently missing keys."* Extraction goes to a staging directory and only
 * a complete archive — one that decrypted *and* carried §7.5.2's credentials —
 * is promoted. A failure leaves the device exactly as it was.
 *
 * `TransferArchive.restore` carries the note on what that does and does not
 * protect against; the short version is that the reason it was first written
 * for turned out not to hold on the JVM, and the reason it stays is that the
 * credential check happens after extraction.
 */
class ImportActivity : AppCompatActivity() {

    private var picked: Uri? = null
    private var status: String = ""
    private lateinit var statusView: TextView
    private lateinit var go: Button

    private val pick = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        picked = uri
        say(if (uri == null) "No file chosen." else "Backup selected. Enter its passphrase.")
        go.isEnabled = uri != null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // See the class note: a restore over a live session is not a restore.
        if (SessionProvider.manager(this).hasStoredSession) {
            toastAndFinish(
                "Sign out first. Restoring a backup replaces this device's keys, " +
                "and doing that under a signed-in account leaves it unable to read anything."
            )
            return
        }

        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        // §14 — the same title bar as every other screen, from :social-ui's
        // Chrome. It also takes the system-bar insets that padForSystemBars
        // used to take here, so a screen's last row still clears the gesture
        // bar (§14.10) — that is the one thing this replacement must not lose.
        val page = Chrome(this).page("Restore from a backup", onBack = { finish() },
            horizontalPaddingDp = 20)
        val root = page.content
        setContentView(page.root)

        fun label(t: String, size: Float, bold: Boolean = false, c: Int = UiR.color.social_text) =
            TextView(this).apply {
                text = t
                setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@ImportActivity, c))
            }

        root.addView(label(
            "Choose the .nlsx file you saved from your old phone, then enter the " +
            "passphrase you set when you made it. Without that passphrase the file " +
            "cannot be opened — not by us either.",
            14f, c = UiR.color.social_muted
        ))
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(16)) })

        val choose = Button(this).apply {
            text = "Choose backup file"; isAllCaps = false
        }
        root.addView(choose)

        val pass = EditText(this).apply {
            hint = "Passphrase"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(pass)

        statusView = label("", 14f, c = UiR.color.social_muted)
        root.addView(statusView)

        go = Button(this).apply {
            text = "Restore"; isAllCaps = false; isEnabled = false
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) }
        }
        root.addView(go)

        // The archive has no registered media type, so filter by nothing and
        // let the user find it. Filtering on a type nothing reports hides the
        // file the user is looking straight at.
        choose.setOnClickListener { pick.launch(arrayOf("*/*")) }

        go.setOnClickListener {
            val uri = picked ?: return@setOnClickListener
            val p = pass.text.toString()
            if (p.isEmpty()) { say("Enter the passphrase."); return@setOnClickListener }
            go.isEnabled = false
            restore(uri, p.toCharArray())
        }
    }

    private fun restore(uri: Uri, passphrase: CharArray) {
        say("Opening…")
        lifecycleScope.launch {
            val staging = File(cacheDir, "restore-staging")
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val extras = contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Could not open that file." }
                        // Staging and promotion live in :social-core so they can
                        // be tested without a device — see TransferArchiveTest.
                        TransferArchive.restore(
                            input, passphrase,
                            staging = staging,
                            targets = mapOf("files" to filesDir, "cache" to cacheDir)
                        ).second
                    }

                    // §7.5.2 — the store key is what makes the SQLite files
                    // readable. An archive without it holds stores nothing can
                    // open, which is exactly the defect §7.5.4 caught in the
                    // first build of the *writer*. Refuse rather than restore
                    // a half-account.
                    val creds = extras[TransferArchive.BUNDLE_ENTRY]
                        ?: error("This backup has no credentials in it, so the restored " +
                                 "messages could not be read. It was made by a version of " +
                                 "the app with a known fault; make a new one if you can.")
                    SessionStore(this@ImportActivity).importBundle(creds)
                }
            }
            passphrase.fill('\u0000')
            staging.deleteRecursively()

            r.onSuccess {
                say("Restored. Opening your messages…")
                startActivity(
                    Intent(this@ImportActivity, HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            }.onFailure {
                // A wrong passphrase and a damaged file are the same GCM tag
                // failure, and saying so is more useful than a stack trace.
                say(
                    "That didn't work: ${it.message ?: "the file could not be opened"}\n\n" +
                    "If the passphrase is right, the file may be damaged or incomplete."
                )
                go.isEnabled = true
            }
        }
    }

    private fun say(s: String) { status = s; statusView.text = s }

    private fun toastAndFinish(m: String) {
        android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        fun intent(c: Context) = Intent(c, ImportActivity::class.java)
    }
}
