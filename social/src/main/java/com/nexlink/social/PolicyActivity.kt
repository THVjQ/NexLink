package com.nexlink.social

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nexlink.social.ui.chrome.Chrome
import com.nexlink.social.ui.R as UiR

/**
 * The Terms of Service and the Privacy Policy, in the app — §4.7.
 *
 * **This replaces a stub that told the truth and helped nobody.** The
 * acceptance gate's "Read the Terms of Service" used to show a toast reading
 * *"document not yet written (§4.7, phase 5)"*, while the gate's own copy asked
 * the user to read both documents before accepting them. §9.6.4 records the
 * consequence: the open-the-document-first rule was dropped because it was
 * gating consent on opening something that did not exist.
 *
 * The documents are written and always were — `docs/social/legal/` — they were
 * simply never shipped. They now travel as build-time assets copied from that
 * directory, so the policy in the app and the policy in the repository cannot
 * disagree.
 *
 * Play requires a privacy policy to be reachable, and a store listing URL is
 * still needed separately; this is the in-app half.
 */
class PolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val which = intent.getStringExtra(EXTRA_DOC) ?: DOC_TERMS
        val chrome = Chrome(this)
        val page = chrome.page(
            if (which == DOC_PRIVACY) "Privacy Policy" else "Terms of Service",
            onBack = { finish() },
        )
        setContentView(page.root)

        val text = runCatching {
            assets.open("legal/$which").bufferedReader().use { it.readText() }
        }.getOrNull()

        if (text == null) {
            // Never silently show an empty page where a legal document belongs.
            page.content.addView(chrome.emptyState(
                "This document didn't load",
                "That is a fault in this build, not something you did. The current " +
                    "version is always at nexlink.thvjq.com.au."))
            return
        }
        Markdown(this, chrome).render(text).forEach { page.content.addView(it) }
    }

    companion object {
        private const val EXTRA_DOC = "doc"
        const val DOC_TERMS = "terms-of-service.md"
        const val DOC_PRIVACY = "privacy-policy.md"
        fun intent(c: Context, doc: String) =
            Intent(c, PolicyActivity::class.java).putExtra(EXTRA_DOC, doc)
    }
}

/**
 * Just enough Markdown to render a policy document faithfully.
 *
 * Deliberately not a library: these two documents use headings, paragraphs,
 * bullets, numbered lists, bold, inline code, block quotes, rules and tables,
 * and a 200-line renderer for that is smaller than the dependency and cannot
 * surprise us with its own styling.
 *
 * **Anything it does not understand is emitted as plain text rather than
 * dropped.** A converter that silently loses a clause is not one you can put in
 * front of someone as the terms they are agreeing to — the same rule
 * `tools/md-to-docx.py` states for the same reason.
 */
private class Markdown(private val a: PolicyActivity, private val chrome: Chrome) {

    fun render(src: String): List<View> {
        val out = mutableListOf<View>()
        val lines = src.replace("\r\n", "\n").split("\n")
        var i = 0
        val para = StringBuilder()

        fun flush() {
            if (para.isNotBlank()) out += body(para.toString().trim())
            para.setLength(0)
        }

        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trim()
            when {
                line.isEmpty() -> { flush(); i++ }

                line.startsWith("```") -> {
                    flush(); i++
                    val buf = StringBuilder()
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        buf.appendLine(lines[i]); i++
                    }
                    i++
                    out += code(buf.toString().trimEnd())
                }

                line.startsWith("|") -> {
                    // A table is rendered as its rows, one per line. Laying out
                    // real columns on a phone would mean horizontal scrolling
                    // through a legal document, which is worse than reading it
                    // as a list.
                    flush()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val cells = lines[i].trim().trim('|').split("|").map { it.trim() }
                        if (cells.none { it.isNotEmpty() && it.any { c -> c != '-' && c != ':' } }) {
                            i++; continue          // the |---|---| separator
                        }
                        out += body(cells.filter { it.isNotEmpty() }.joinToString(" — "))
                        i++
                    }
                }

                line == "---" || line == "***" || line == "___" -> { flush(); out += rule(); i++ }

                line.startsWith("#") -> {
                    flush()
                    val level = line.takeWhile { it == '#' }.length
                    out += heading(line.drop(level).trim(), level)
                    i++
                }

                line.startsWith("> ") -> { flush(); out += quote(line.removePrefix("> ")); i++ }

                line.startsWith("- ") || line.startsWith("* ") ->
                    { flush(); out += bullet("•  " + line.drop(2)); i++ }

                Regex("^\\d+[.)] ").containsMatchIn(line) ->
                    { flush(); out += bullet(line); i++ }

                else -> { para.append(line).append(' '); i++ }
            }
        }
        flush()
        out += chrome.spacer(24)
        return out
    }

    /**
     * `**bold**`, `*italic*` and `` `code` ``, applied as spans so the text
     * stays selectable.
     *
     * Italics are here because leaving them out did not mean "no italics" — it
     * meant the asterisks were **printed**, and the first paragraph of the
     * shipped privacy policy opened with a literal `*A plain-language summary…*`.
     * Seen on the handset; invisible in the source, where the markup is correct.
     */
    private fun inline(s: String): CharSequence {
        val sb = SpannableStringBuilder()
        var i = 0
        while (i < s.length) {
            val bold = s.indexOf("**", i)
            val tick = s.indexOf('`', i)
            // A single asterisk that is not the start of a bold run.
            var ital = -1
            var k = i
            while (k < s.length) {
                val at = s.indexOf('*', k)
                if (at < 0) break
                if (at != bold) { ital = at; break }
                k = at + 2
            }
            val next = listOf(bold, tick, ital).filter { it >= 0 }.minOrNull() ?: -1
            if (next < 0) { sb.append(s.substring(i)); break }
            sb.append(s.substring(i, next))
            if (next == ital && next != bold) {
                val end = s.indexOf('*', next + 1)
                if (end < 0) { sb.append(s.substring(next)); break }
                val start = sb.length
                sb.append(s.substring(next + 1, end))
                sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i = end + 1
            } else if (next == bold) {
                val end = s.indexOf("**", next + 2)
                if (end < 0) { sb.append(s.substring(next)); break }
                val start = sb.length
                sb.append(s.substring(next + 2, end))
                sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i = end + 2
            } else {
                val end = s.indexOf('`', next + 1)
                if (end < 0) { sb.append(s.substring(next)); break }
                val start = sb.length
                sb.append(s.substring(next + 1, end))
                sb.setSpan(TypefaceSpan("monospace"), start, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i = end + 1
            }
        }
        return sb
    }

    private fun col(id: Int) = ContextCompat.getColor(a, id)
    private fun dp(v: Int) = (v * a.resources.displayMetrics.density).toInt()

    private fun make(
        text: CharSequence, size: Float, colour: Int,
        bold: Boolean = false, top: Int = 0, start: Int = 20,
    ) = TextView(a).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(colour)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(4).toFloat(), 1f)
        setPadding(dp(start), dp(top), dp(20), dp(3))
        setTextIsSelectable(true)     // people copy clauses out of these
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    private fun heading(t: String, level: Int) = make(
        inline(t),
        when (level) { 1 -> 23f; 2 -> 19f; 3 -> 16.5f; else -> 15f },
        col(UiR.color.social_text), bold = true, top = if (level <= 2) 20 else 14)

    private fun body(t: String) = make(inline(t), 15f, col(UiR.color.social_text2), top = 4)
    private fun bullet(t: String) = make(inline(t), 15f, col(UiR.color.social_text2), start = 28)
    private fun quote(t: String) = make(inline(t), 15f, col(UiR.color.social_muted), start = 30)
        .apply { setTypeface(typeface, Typeface.ITALIC) }

    private fun code(t: String) = make(t, 13f, col(UiR.color.social_text2), start = 24)
        .apply { typeface = Typeface.MONOSPACE }

    private fun rule() = View(a).apply {
        setBackgroundColor(col(UiR.color.social_divider))
        layoutParams = LinearLayout.LayoutParams(MATCH, maxOf(1, dp(1) / 2)).apply {
            topMargin = dp(14); bottomMargin = dp(6)
            marginStart = dp(20); marginEnd = dp(20)
        }
    }

    private companion object {
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
