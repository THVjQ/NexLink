package com.nexlink.social.ui.common

import android.content.Context
import android.graphics.Typeface
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexlink.social.ui.R

/**
 * Small view builders, matching the programmatic style already used by
 * `BridgeSetupActivity` in `:app`. No XML layouts, because the onboarding
 * screens rebuild their hierarchy on every step change and layout inflation for
 * six near-identical screens is more code, not less.
 */
internal class Ui(private val ctx: Context) {

    private val density = ctx.resources.displayMetrics.density
    fun Int.px(): Int = (this * density).toInt()
    fun col(id: Int): Int = ContextCompat.getColor(ctx, id)

    fun column(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    fun title(text: CharSequence): TextView = TextView(ctx).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(col(R.color.social_text))
        setPadding(0, 8.px(), 0, 12.px())
    }

    /** Body copy. Accepts the `<b>` markup used in the gate strings. */
    fun body(text: CharSequence): TextView = TextView(ctx).apply {
        this.text = if (text is String && "<" in text)
            @Suppress("DEPRECATION") Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT)
        else text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(col(R.color.social_text2))
        setLineSpacing(4.px().toFloat(), 1f)
        // Only install the link handler when there is actually a link. Setting
        // it unconditionally makes plain paragraphs focusable and highlightable,
        // which on-device shows as a grey block behind body text when the user
        // taps near it.
        if (this.text is android.text.Spanned &&
            (this.text as android.text.Spanned)
                .getSpans(0, this.text.length, android.text.style.URLSpan::class.java)
                .isNotEmpty()
        ) {
            movementMethod = LinkMovementMethod.getInstance()
        }
        setPadding(0, 0, 0, 12.px())
    }

    fun caption(text: CharSequence): TextView = TextView(ctx).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(col(R.color.social_muted))
        setPadding(0, 4.px(), 0, 0)
    }

    fun error(text: CharSequence): TextView = TextView(ctx).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(col(R.color.social_danger))
        setPadding(0, 8.px(), 0, 0)
    }

    fun primaryButton(text: CharSequence, onClick: () -> Unit): Button = Button(ctx).apply {
        // The gate is the first screen anyone sees, so it must not look like
        // the platform default while the rest of the app does not. Same shape
        // and fill as Chrome's filled button (§14.12); duplicated rather than
        // imported because the onboarding package predates Chrome and there is
        // no dependency between them.
        this.text = text
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(col(R.color.social_on_accent_fill))
        minHeight = 48.px()
        setPadding(20.px(), 13.px(), 20.px(), 13.px())
        stateListAnimator = null
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 16.px().toFloat()
            setColor(col(R.color.social_accent_fill))
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = 16.px() }
    }

    fun textButton(text: CharSequence, onClick: () -> Unit): Button = Button(ctx).apply {
        this.text = text
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(col(R.color.social_accent))
        minHeight = 48.px()                      // §14.10
        stateListAnimator = null
        setBackgroundColor(0x00000000)
        setOnClickListener { onClick() }
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    fun checkbox(text: CharSequence, onToggle: (Boolean) -> Unit): CheckBox = CheckBox(ctx).apply {
        this.text = text
        // §9.6.1 — none pre-ticked.
        isChecked = false
        setTextColor(col(R.color.social_text))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        buttonTintList = android.content.res.ColorStateList.valueOf(col(R.color.social_accent))
        minHeight = 48.px()                      // §14.10
        setPadding(10.px(), 12.px(), 0, 12.px())
        setOnCheckedChangeListener { _, checked -> onToggle(checked) }
    }

    fun spacer(height: Int): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, height.px())
    }

    fun divider(): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 1)
        setBackgroundColor(col(R.color.social_divider))
    }

    companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
