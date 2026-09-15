package com.nexlink.social

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nexlink.social.ui.chrome.Chrome
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexlink.social.core.session.MediaRetention
import com.nexlink.social.core.session.SocialSession
import com.nexlink.social.core.session.StoreUsage
import kotlinx.coroutines.launch
import java.util.Locale
import com.nexlink.social.ui.R as UiR

/**
 * Storage — §12.5.
 *
 * §12.5.3's argument for this screen existing: "users manage what they can see;
 * an opaque multi-gigabyte total produces uninstalls rather than pruning". So
 * the numbers are broken out rather than summed, and each one says whether it
 * can be cleared and what clearing costs.
 *
 * The line this screen must not cross is §12.5.2's: **"clear cache" frees space,
 * it does not delete conversations.** Users have learned to tap it without
 * reading. So nothing on this screen deletes a message, the crypto store is
 * shown as explicitly un-clearable (§12.5.1 — Megolm keys are not disposable
 * and their loss is permanent), and the one destructive-sounding button says
 * exactly what it does before doing it.
 */
class StorageActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private var usage: StoreUsage? = null
    private var status: String? = "Measuring…"
    private var retention: MediaRetention = MediaRetention.DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // §14 — the same title bar as every other screen, from :social-ui's
        // Chrome. It also takes the system-bar insets that padForSystemBars
        // used to take here, so a screen's last row still clears the gesture
        // bar (§14.10) — that is the one thing this replacement must not lose.
        val page = Chrome(this).page("Storage", onBack = { finish() },
            horizontalPaddingDp = 20)
        root = page.content
        setContentView(page.root)
        retention = StoragePrefs.retention(this)
        render()
        refresh()
    }

    private fun session(): SocialSession? = SessionProvider.manager(this).current()

    /**
     * The app's true on-disk footprint, measured by walking its own data
     * directory — **not** the sum of the four store sizes above.
     *
     * The two genuinely differ, and the difference is not rounding. Measured on
     * 2026-09-12 with a freshly signed-in account: the four stores summed to
     * **444 KB** while the data directory held **590,894 bytes (577 KB)** — a
     * 30% understatement. `getStoreSizes` reports the size of each main
     * `.sqlite3` file and counts neither the `-wal` nor the `-shm` beside it,
     * and the crypto store's WAL alone was 115 KB against a 184 KB store.
     *
     * Settings say "Storage" and Android's own app-info screen shows the
     * directory total, so this screen has to agree with that number or it is
     * the one telling the lie.
     */
    private val diskBytes: Long
        // dataDir contains files/, cache/, shared_prefs/ and the rest, so one
        // walk covers everything the app owns and nothing is double-counted.
        get() = dataDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    private fun refresh() {
        lifecycleScope.launch {
            val s = session() ?: run { status = "Not signed in"; render(); return@launch }
            s.storeSizes()
                .onSuccess { usage = it; status = null }
                .onFailure { status = "Couldn't measure storage: " + (it.message ?: "unknown") }
            render()
        }
    }

    private fun render() {
        root.removeAllViews()

        val u = usage
        if (u == null) {
            root.addView(body(status ?: "Measuring…"))
            return
        }

        root.addView(body("NexLink Social is using ${bytes(diskBytes)} on this phone."))
        root.addView(gap())

        // Largest first — §12.5.3. The user is here to find what is big, and
        // ordering by size is the whole of that job.
        listOf(
            Row("Photos, videos and files", u.mediaBytes,
                "Downloaded attachments. Cleared safely — they download again when you open them."),
            Row("Message cache", u.eventCacheBytes,
                "A local copy of your conversations. Cleared safely — it re-syncs from the server."),
            Row("Account data", u.stateBytes,
                "Room names, members and settings. Small, and re-syncs."),
            Row("Encryption keys", u.cryptoBytes,
                "Never cleared. These are what let you read your own history — " +
                    "deleting them would lose it permanently, and nothing can bring it back.",
                clearable = false)
        ).sortedWith(compareByDescending<Row> { it.clearable }.thenByDescending { it.bytes })
            .forEach { root.addView(usageRow(it)) }

        root.addView(gap())
        root.addView(sectionTitle("Media cache limit"))
        root.addView(body(
            "When downloaded photos and videos go over this, the oldest are removed " +
                "first. Your messages are never affected."))
        root.addView(retentionChoices())

        root.addView(gap())
        root.addView(Button(this).apply {
            text = "Clear cache"
            minHeight = dp(48)   // §14.10
            setOnClickListener { confirmClear(u) }
        })
        root.addView(body(
            "Empties downloaded media and the local message cache. " +
                "**Your messages and your encryption keys are not deleted.** " +
                "Conversations re-download the next time you open them."))
    }

    private class Row(
        val label: String,
        val bytes: Long,
        val detail: String,
        val clearable: Boolean = true
    )

    private fun usageRow(r: Row): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        addView(LinearLayout(this@StorageActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@StorageActivity).apply {
                text = r.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@StorageActivity, UiR.color.social_text))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(TextView(this@StorageActivity).apply {
                text = bytes(r.bytes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(ContextCompat.getColor(this@StorageActivity, UiR.color.social_text2))
                gravity = Gravity.END
            })
        })
        addView(body(r.detail))
    }

    /**
     * §12.5.2's four choices, as radio-like buttons.
     *
     * "Unlimited" is genuinely offered. Hiding it does not stop the store
     * growing — it only stops the user understanding why it did.
     */
    private fun retentionChoices(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        MediaRetention.CHOICES.forEach { (label, policy) ->
            val selected = policy.maxCacheBytes == retention.maxCacheBytes
            addView(TextView(this@StorageActivity).apply {
                text = (if (selected) "●  " else "○  ") + label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(ContextCompat.getColor(this@StorageActivity,
                    if (selected) UiR.color.social_accent else UiR.color.social_text))
                // §14.10 — the whole row is the target, not the glyph.
                minHeight = dp(48)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                contentDescription =
                    "Media cache limit $label" + if (selected) ", selected" else ""
                setOnClickListener { choose(policy) }
            })
        }
    }

    private fun choose(policy: MediaRetention) {
        retention = policy
        StoragePrefs.setRetention(this, policy)
        render()
        lifecycleScope.launch {
            session()?.applyMediaRetention(policy)?.onFailure {
                Toast.makeText(this@StorageActivity,
                    "Saved, but the limit could not be applied yet", Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
    }

    /**
     * §12.5.2 — "Anything that deletes messages must say so unambiguously and
     * confirm." This deletes no messages, so the confirmation's job is the
     * opposite: to say so, plainly, before the user talks themselves out of it.
     */
    private fun confirmClear(u: StoreUsage) {
        AlertDialog.Builder(this)
            .setTitle("Clear cache?")
            .setMessage(
                "This empties the message cache and downloaded attachments " +
                    "(${bytes(u.clearableBytes)} of content).\n\n" +
                    "The number above may not drop straight away — the space is " +
                    "reused for new messages rather than handed back to Android.\n\n" +
                    "Your messages are not deleted and your encryption keys are not " +
                    "touched. Conversations and attachments download again when you " +
                    "open them, which uses data."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear cache") { _, _ ->
                lifecycleScope.launch {
                    status = "Clearing…"; usage = null; render()
                    session()?.clearCaches()?.onFailure {
                        status = "Couldn't clear: " + (it.message ?: "unknown")
                    }
                    refresh()
                }
            }
            .show()
    }

    // ---- formatting ---------------------------------------------------------

    /**
     * Binary units, with real numbers all the way down.
     *
     * The first version collapsed everything under a megabyte to "under 1 MB",
     * which on a fresh account rendered five identical rows and read as a
     * broken screen rather than a small one. Precision that is *noise* is worth
     * hiding; precision that is the only thing distinguishing four rows is not.
     * So kilobytes are shown as kilobytes.
     *
     * Decimals are truncated, never rounded up: showing "1.0 GB" for 1023 MB
     * invites the user to clear it and be disappointed by what they get back.
     */
    private fun bytes(n: Long): String {
        val k = 1024.0
        return when {
            n <= 0 -> "empty"
            n < k -> "$n B"
            n < k * k -> String.format(Locale.US, "%d KB", (n / k).toLong())
            n < k * k * k -> String.format(Locale.US, "%.1f MB", floor1(n / (k * k)))
            else -> String.format(Locale.US, "%.2f GB", floor2(n / (k * k * k)))
        }
    }

    private fun floor1(v: Double) = kotlin.math.floor(v * 10) / 10
    private fun floor2(v: Double) = kotlin.math.floor(v * 100) / 100

    private fun title(t: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setTypeface(null, Typeface.BOLD)
        setTextColor(ContextCompat.getColor(this@StorageActivity, UiR.color.social_text))
        setPadding(0, 0, 0, dp(12))
    }

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTypeface(null, Typeface.BOLD)
        setTextColor(ContextCompat.getColor(this@StorageActivity, UiR.color.social_text))
        setPadding(0, dp(8), 0, dp(6))
    }

    private fun body(t: String) = TextView(this).apply {
        // The copy uses **bold** markers for emphasis in one place; strip them
        // rather than render them, since this is a plain TextView.
        text = t.replace("**", "")
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(ContextCompat.getColor(this@StorageActivity, UiR.color.social_muted))
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun gap() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(-1, dp(16))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun intent(ctx: Context) = Intent(ctx, StorageActivity::class.java)
    }
}

/**
 * §12.5.2 — the chosen limit, remembered locally.
 *
 * Plain `SharedPreferences`, not encrypted: this is a cache size, not user
 * content. §12.4.5's rule is that *values* carrying content or identifiers are
 * encrypted, and a number of megabytes is neither. Encrypting it would add a
 * Keystore dependency to a screen that must work before a session exists.
 */
object StoragePrefs {
    private const val PREFS = "social_storage"
    private const val KEY_MAX = "media_max_bytes"
    /** Sentinel for "unlimited" — distinct from "never chosen". */
    private const val UNLIMITED = -1L

    fun retention(ctx: Context): MediaRetention {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(KEY_MAX)) return MediaRetention.DEFAULT
        val v = p.getLong(KEY_MAX, UNLIMITED)
        return MediaRetention(maxCacheBytes = if (v == UNLIMITED) null else v)
    }

    fun setRetention(ctx: Context, policy: MediaRetention) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_MAX, policy.maxCacheBytes ?: UNLIMITED)
            .apply()
    }
}
