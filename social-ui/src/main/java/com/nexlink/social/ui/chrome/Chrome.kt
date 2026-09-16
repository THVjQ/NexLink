package com.nexlink.social.ui.chrome

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nexlink.social.ui.R

/**
 * The app's shared chrome — §14.
 *
 * Every screen in :social builds its views in Kotlin (no XML, no Compose), and
 * before this existed every screen also invented its own. The result was an app
 * where the inbox was a column of eight full-width buttons, the conversation
 * had no title bar at all, and no two screens agreed on a margin. Each screen
 * was individually defensible; together they did not read as one product.
 *
 * So the layout decisions live here once:
 *
 *  - **[page]** — a title bar with a back arrow and actions, over a scrolling
 *    column. Eleven screens were already `setContentView(ScrollView(column))`
 *    plus a `padForSystemBars` call, which is this minus the bar.
 *  - **[card] / [row]** — the grouped-list surface the inbox and the settings
 *    screens share.
 *  - **[bubble]**, **[avatar]**, **[badge]** — the parts that make a timeline
 *    look like a timeline.
 *
 * Insets are handled in [page] rather than by each caller (§14.10): the bar
 * takes the status bar, the scrolling content takes the gesture bar, and
 * anything floating is lifted clear of both. On the test handset a screen
 * without this puts its last row under the gesture bar, where it is not merely
 * ugly but **untappable** — the gesture eats the touch.
 *
 * Nothing here knows what a message is. It takes text and lambdas, which is
 * what keeps :social-ui free of the SDK (§11.6) and lets the same bar sit on a
 * screen about storage.
 */
class Chrome(private val a: Activity) {

    private val density = a.resources.displayMetrics.density
    fun dp(v: Int): Int = (v * density).toInt()
    fun colour(id: Int): Int = ContextCompat.getColor(a, id)

    /** One icon control in a title bar. */
    class Action(val icon: Icon.Kind, val label: String, val onClick: (View) -> Unit)

    // ── the page ────────────────────────────────────────────────────────────

    /**
     * A screen: title bar, then a scrolling column you add your content to.
     *
     * [content] is the column. [bar] is exposed so a screen can retitle itself
     * (a conversation swaps its subtitle for "typing…"), and [floating] is for
     * a button that hovers over the content rather than scrolling with it.
     */
    inner class Page(
        val root: FrameLayout,
        val bar: LinearLayout,
        val titleView: TextView,
        val subtitleView: TextView,
        val scroll: ScrollView,
        val content: LinearLayout,
    ) {
        private val floating = mutableListOf<Pair<View, Int>>()

        /** Put a control over the content, pinned to the bottom-right corner. */
        fun float(v: View, bottomMarginDp: Int = 20, endMarginDp: Int = 20) {
            // Keep whatever size the control already asked for. A drawn Icon
            // has no intrinsic size, so an ImageView left at WRAP_CONTENT
            // measures to its padding and the icon vanishes — a 36dp blue disc
            // with nothing on it, which looks like a rendering bug rather than
            // a layout one.
            val w = v.layoutParams?.width ?: WRAP
            val h = v.layoutParams?.height ?: WRAP
            root.addView(v, FrameLayout.LayoutParams(w, h).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = dp(bottomMarginDp)
                marginEnd = dp(endMarginDp)
            })
            floating += v to dp(bottomMarginDp)
        }

        fun subtitle(text: CharSequence?) {
            subtitleView.text = text ?: ""
            subtitleView.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        }

        internal fun applyInsets(left: Int, top: Int, right: Int, bottom: Int) {
            bar.setPadding(dp(4) + left, top, dp(4) + right, 0)
            content.setPadding(
                content.paddingLeft, content.paddingTop,
                content.paddingRight, dp(24) + bottom,
            )
            floating.forEach { (v, base) ->
                (v.layoutParams as? FrameLayout.LayoutParams)?.let {
                    it.bottomMargin = base + bottom
                    v.layoutParams = it
                }
            }
        }
    }

    /** The title bar on its own, for a screen that pins something to the bottom. */
    class Bar(val view: LinearLayout, val titleView: TextView, val subtitleView: TextView) {
        fun title(text: CharSequence) { titleView.text = text }
        fun subtitle(text: CharSequence?) {
            subtitleView.text = text ?: ""
            subtitleView.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    /**
     * Tell the system which way round to draw the clock and the gesture bar.
     *
     * The title bar is painted `social_surface`, and the status bar sits on top
     * of it — so in the light theme the platform was drawing white icons on
     * white and the clock, the battery and the signal bars simply **vanished**.
     * Caught on the handset; invisible in the dark theme, which is what the
     * phone this is developed on runs.
     *
     * Done here rather than in the theme XML because it then applies to every
     * screen that has a bar, including the conversation, which builds its own
     * layout around one rather than using [page].
     */
    private fun syncSystemBars() {
        val night = (a.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val window = a.window ?: return
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }

    fun titleBar(
        title: CharSequence,
        subtitle: CharSequence? = null,
        onBack: (() -> Unit)? = null,
        actions: List<Action> = emptyList(),
    ): Bar {
        syncSystemBars()
        val bar = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colour(R.color.social_surface))
            elevation = dp(2).toFloat()
        }
        val titles = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        val titleView = TextView(a).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colour(R.color.social_text))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val subtitleView = TextView(a).apply {
            text = subtitle ?: ""
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(colour(R.color.social_muted))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            visibility = if (subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        titles.addView(titleView)
        titles.addView(subtitleView)

        if (onBack != null) bar.addView(iconButton(Icon.Kind.BACK, "Back") { onBack() })
        else bar.addView(spacerWidth(dp(8)))
        bar.addView(titles)
        actions.forEach { act ->
            bar.addView(iconButton(act.icon, act.label) { v -> act.onClick(v) })
        }
        return Bar(bar, titleView, subtitleView)
    }

    fun page(
        title: CharSequence,
        subtitle: CharSequence? = null,
        onBack: (() -> Unit)? = null,
        actions: List<Action> = emptyList(),
        horizontalPaddingDp: Int = 0,
    ): Page {
        val titleBar = titleBar(title, subtitle, onBack, actions)
        val bar = titleBar.view
        val titleView = titleBar.titleView
        val subtitleView = titleBar.subtitleView

        val content = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(horizontalPaddingDp), dp(12), dp(horizontalPaddingDp), dp(24))
        }
        // Every screen builds its content by `removeAllViews()` then `addView`,
        // so styling can hang off the moment a child arrives rather than on
        // eleven screens each remembering to call [polish] at the end of their
        // own render. A screen written tomorrow gets it too, which is the
        // difference between a convention and a mechanism.
        content.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                child?.let { polish(it) }
            }
            override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        })
        val scroll = ScrollView(a).apply {
            isFillViewport = true
            addView(content)
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        val column = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colour(R.color.social_bg))
            addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(scroll)
        }
        val root = FrameLayout(a).apply {
            addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        val p = Page(root, bar, titleView, subtitleView, scroll, content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            p.applyInsets(b.left, b.top, b.right, b.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        return p
    }

    // ── controls ────────────────────────────────────────────────────────────

    /**
     * A 48dp icon target. The size is not negotiable (§14.10): a 24dp glyph is
     * a 24dp target unless something says otherwise, and that is half the
     * minimum.
     */
    fun iconButton(
        kind: Icon.Kind,
        label: String,
        tintColour: Int = colour(R.color.social_text),
        onClick: (View) -> Unit,
    ): ImageView = ImageView(a).apply {
        setImageDrawable(Icon(kind, tintColour))
        contentDescription = label
        val pad = dp(12)
        setPadding(pad, pad, pad, pad)
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        background = ripple(null, circular = true)
        setOnClickListener { onClick(it) }
    }

    /** The primary action of a screen: filled, full width. */
    fun filledButton(label: CharSequence, onClick: () -> Unit): TextView = TextView(a).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(colour(R.color.social_on_accent_fill))
        minHeight = dp(50)
        setPadding(dp(20), dp(13), dp(20), dp(13))
        background = ripple(accentFill(16f))
        isClickable = true
        setOnClickListener { onClick() }
    }

    /** The secondary action beside it: outlined, same height, quieter. */
    fun quietButton(label: CharSequence, onClick: () -> Unit): TextView = TextView(a).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(colour(R.color.social_accent))
        minHeight = dp(50)
        setPadding(dp(20), dp(13), dp(20), dp(13))
        background = ripple(GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(0x00000000)
            setStroke(dp(1), colour(R.color.social_divider))
        })
        isClickable = true
        setOnClickListener { onClick() }
    }

    /** The round button that hovers over a list. */
    fun fab(kind: Icon.Kind, label: String, onClick: () -> Unit): View = ImageView(a).apply {
        setImageDrawable(Icon(kind, colour(R.color.social_on_accent_fill)))
        contentDescription = label
        val pad = dp(18)
        setPadding(pad, pad, pad, pad)
        background = ripple(
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colour(R.color.social_accent_fill))
            },
            circular = true,
        )
        elevation = dp(6).toFloat()
        setOnClickListener { onClick() }
        layoutParams = ViewGroup.LayoutParams(dp(58), dp(58))
    }

    /** An overflow menu. Kept here so every screen's looks the same. */
    fun menu(anchor: View, items: List<Pair<String, () -> Unit>>) {
        val m = androidx.appcompat.widget.PopupMenu(a, anchor, Gravity.END)
        items.forEachIndexed { i, (label, _) -> m.menu.add(0, i, i, label) }
        m.setOnMenuItemClickListener { item ->
            items.getOrNull(item.itemId)?.second?.invoke()
            true
        }
        m.show()
    }

    // ── surfaces ────────────────────────────────────────────────────────────

    /** The grouped-list surface: a rounded white block with a side margin. */
    fun card(marginHorizontalDp: Int = 12, marginTopDp: Int = 0): LinearLayout =
        LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(colour(R.color.social_surface), 20f)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                marginStart = dp(marginHorizontalDp)
                marginEnd = dp(marginHorizontalDp)
                topMargin = dp(marginTopDp)
            }
        }

    /** A tappable row inside a [card]. */
    fun row(onClick: (() -> Unit)? = null, onLongClick: (() -> Boolean)? = null): LinearLayout =
        LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            minimumHeight = dp(64)
            background = ripple(null)
            if (onClick != null) { isClickable = true; setOnClickListener { onClick() } }
            if (onLongClick != null) {
                isLongClickable = true
                setOnLongClickListener { onLongClick() }
            }
        }

    /** The hairline between rows, inset past the avatar so the list reads as a group. */
    fun rowDivider(insetStartDp: Int = 70): View = View(a).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, maxOf(1, dp(1) / 2)).apply {
            marginStart = dp(insetStartDp)
        }
        setBackgroundColor(colour(R.color.social_divider))
    }

    fun sectionHeader(text: CharSequence): TextView = TextView(a).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(colour(R.color.social_muted))
        letterSpacing = 0.06f
        setPadding(dp(26), dp(16), dp(26), dp(7))
    }

    /**
     * The standing-warning card (§8.5.2 backup, §12.4.4 screen lock).
     *
     * These used to be a red paragraph followed by a full-width button, twice
     * over, at the top of the inbox — which pushed the conversations, the thing
     * the screen is for, below the fold. A tinted card with the action inside it
     * says the same thing in a fifth of the height.
     */
    fun banner(
        headline: CharSequence,
        body: CharSequence,
        actionLabel: CharSequence,
        onAction: () -> Unit,
    ): View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(colour(R.color.social_banner_bg), 18f)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
            marginStart = dp(12); marginEnd = dp(12); bottomMargin = dp(10)
        }
        addView(TextView(a).apply {
            text = headline
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colour(R.color.social_danger))
        })
        addView(TextView(a).apply {
            text = body
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(colour(R.color.social_text))
            setPadding(0, dp(3), 0, dp(9))
        })
        addView(TextView(a).apply {
            text = actionLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colour(R.color.social_on_accent_fill))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            minHeight = dp(44)
            background = ripple(accentFill(13f))
            isClickable = true
            setOnClickListener { onAction() }
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
        })
    }

    /** What a screen shows instead of nothing at all. */
    fun emptyState(headline: CharSequence, body: CharSequence): View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(40), dp(72), dp(40), dp(40))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        addView(TextView(a).apply {
            text = headline
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colour(R.color.social_text))
        })
        addView(TextView(a).apply {
            text = body
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(colour(R.color.social_muted))
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, dp(8), 0, 0)
        })
    }

    /**
     * A headline figure on its own card — the one number a screen exists to
     * report, at a size that says so.
     *
     * Settings screens in this app were paragraphs of prose with the figures
     * buried in them, which is the wrong shape: someone opening "Storage" wants
     * the total before they want the explanation.
     */
    fun hero(value: CharSequence, label: CharSequence): View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(colour(R.color.social_surface), 20f)
        setPadding(dp(18), dp(18), dp(18), dp(18))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
            marginStart = dp(12); marginEnd = dp(12); bottomMargin = dp(6)
        }
        addView(TextView(a).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colour(R.color.social_text))
        })
        addView(TextView(a).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(colour(R.color.social_muted))
            setPadding(0, dp(2), 0, 0)
        })
    }

    /**
     * A proportional bar. Segments are (fraction, colour) and are drawn in
     * order; anything left over is the track.
     *
     * Rounded as one piece rather than per segment, so it reads as a single
     * quantity divided up rather than a row of separate chips.
     */
    fun bar(segments: List<Pair<Float, Int>>, heightDp: Int = 10): View = object : View(a) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val clip = android.graphics.Path()
        override fun onDraw(canvas: android.graphics.Canvas) {
            val r = height / 2f
            clip.reset()
            clip.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r,
                android.graphics.Path.Direction.CW)
            canvas.clipPath(clip)
            paint.color = colour(R.color.social_surface3)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            var x = 0f
            for ((fraction, c) in segments) {
                val w = width * fraction.coerceIn(0f, 1f)
                paint.color = c
                canvas.drawRect(x, 0f, x + w, height.toFloat(), paint)
                x += w
            }
        }
    }.apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(heightDp)).apply {
            topMargin = dp(12)
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /**
     * A row inside a [card]: a bold label, a value on the right, and an
     * optional explanatory line under both.
     */
    fun infoRow(
        label: CharSequence,
        value: CharSequence? = null,
        detail: CharSequence? = null,
        swatch: Int? = null,
        onClick: (() -> Unit)? = null,
    ): View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(13), dp(16), dp(13))
        if (onClick != null) {
            isClickable = true
            background = ripple(null)
            setOnClickListener { onClick() }
        }
        addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            swatch?.let {
                addView(View(a).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(it)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                        marginEnd = dp(9)
                    }
                })
            }
            addView(TextView(a).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(colour(R.color.social_text))
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            value?.let {
                addView(TextView(a).apply {
                    text = it
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(colour(R.color.social_text2))
                    // Figures in a column must line up, or the eye cannot
                    // compare them, which is the only reason they are listed.
                    typeface = android.graphics.Typeface.MONOSPACE
                })
            }
        })
        detail?.let {
            addView(TextView(a).apply {
                text = it
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setTextColor(colour(R.color.social_muted))
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(0, dp(3), 0, 0)
            })
        }
    }

    /**
     * One option in a set. A filled dot rather than a platform RadioButton,
     * because the platform one cannot be tinted to the palette without a theme
     * overlay and comes with its own padding rules.
     */
    fun choiceRow(label: CharSequence, selected: Boolean, onClick: () -> Unit): View =
        LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            minimumHeight = dp(52)                 // §14.10
            isClickable = true
            background = ripple(null)
            setOnClickListener { onClick() }
            contentDescription = "$label" + if (selected) ", selected" else ""
            addView(View(a).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (selected) setColor(colour(R.color.social_accent_fill))
                    else {
                        setColor(0x00000000)
                        setStroke(dp(2), colour(R.color.social_divider))
                    }
                }
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                    marginEnd = dp(14)
                }
            })
            addView(TextView(a).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(colour(
                    if (selected) R.color.social_text else R.color.social_text2))
                if (selected) setTypeface(typeface, Typeface.BOLD)
            })
        }

    /** Explanatory copy between cards, indented to the card's text column. */
    fun note(text: CharSequence): TextView = TextView(a).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        setTextColor(colour(R.color.social_muted))
        setLineSpacing(dp(3).toFloat(), 1f)
        setPadding(dp(28), dp(8), dp(28), dp(4))
    }

    // ── identity ────────────────────────────────────────────────────────────

    /**
     * A circular initial.
     *
     * The colour is a hash of the name, so one person is one colour everywhere
     * — that consistency is most of what makes an initial read as an identity
     * rather than a letter. All five tints carry white above 4.5:1 (§14.10),
     * checked by tools/check-contrast.py.
     *
     * The initial is taken by *code point*, never `name[0]`: a name beginning
     * with an emoji or an astral-plane character would otherwise render as half
     * a surrogate pair, which is a broken box.
     */
    fun avatar(name: String, sizeDp: Int = 46): View = TextView(a).apply {
        val trimmed = name.trim().removePrefix("@")
        text = if (trimmed.isEmpty()) "?" else {
            val cp = trimmed.codePointAt(0)
            String(Character.toChars(cp)).uppercase()
        }
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp * 0.42f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(colour(R.color.social_on_avatar))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(avatarColour(trimmed))
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun avatarColour(name: String): Int {
        val tints = intArrayOf(
            R.color.social_avatar1, R.color.social_avatar2, R.color.social_avatar3,
            R.color.social_avatar4, R.color.social_avatar5,
        )
        // Sum of code points, not hashCode(): String.hashCode is stable across
        // runs, but tying a person's colour to a JDK implementation detail is a
        // promise this cannot actually keep.
        var h = 0
        name.forEach { h = (h + it.code) % 100_000 }
        return colour(tints[h % tints.size])
    }

    /** The unread count. A pill, not a bare number, so it reads as a count. */
    fun badge(count: Int): TextView = TextView(a).apply {
        text = if (count > 99) "99+" else count.toString()
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(colour(R.color.social_on_accent_fill))
        minWidth = dp(22)
        minHeight = dp(22)
        setPadding(dp(7), dp(2), dp(7), dp(2))
        background = rounded(colour(R.color.social_accent_fill), 11f)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    // ── the timeline ────────────────────────────────────────────────────────

    /**
     * A message bubble.
     *
     * The corner nearest the speaker is squared off (4dp against 18dp), which
     * is the cheapest way to say "this side is me" without a tail — and it
     * still works for someone who cannot tell the two fills apart, which a
     * colour difference alone does not.
     */
    fun bubble(mine: Boolean, maxWidthPx: Int = Int.MAX_VALUE): LinearLayout = Bounded(a).apply {
        limit = maxWidthPx
        // WRAP, explicitly. **A vertical LinearLayout hands its children
        // MATCH_PARENT width by default** — `generateDefaultLayoutParams` for
        // VERTICAL returns (MATCH_PARENT, WRAP_CONTENT), not (WRAP, WRAP) as
        // the horizontal case does. A bubble added without params therefore
        // filled its row and every message came out exactly at the width cap,
        // short ones included, which erases the ragged edge the whole
        // left/right reading depends on. Found on the handset; the JVM test
        // missed it because its stand-in parent was horizontal.
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
        orientation = LinearLayout.VERTICAL
        val big = dp(20).toFloat()
        val small = dp(6).toFloat()
        val radii = if (mine)
            floatArrayOf(big, big, big, big, small, small, big, big)
        else
            floatArrayOf(big, big, big, big, big, big, small, small)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(colour(
                if (mine) R.color.social_accent_fill else R.color.social_bubble_theirs
            ))
        }
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    /** The centred "Today" / "14 Sep" marker between two days of messages. */
    fun dayMarker(label: CharSequence): View = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, dp(14), 0, dp(10))
        addView(TextView(a).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(colour(R.color.social_muted))
            setPadding(dp(12), dp(4), dp(12), dp(5))
            background = rounded(colour(R.color.social_surface3), 11f)
        })
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    // ── primitives ──────────────────────────────────────────────────────────

    /**
     * The fill for a primary control: the accent, with a gentle vertical
     * gradient into a darker version of itself.
     *
     * Both ends are the same hue and the *light* end is the one
     * `tools/check-contrast.py` measures, so white on it stays at the verified
     * ratio — a gradient that brightens toward a second colour would quietly
     * take the button below AA at one end and nothing would catch it.
     */
    fun accentFill(radiusDp: Float = 16f): GradientDrawable {
        val top = colour(R.color.social_accent_fill)
        val bottom = darken(top, 0.86f)
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
            .apply { cornerRadius = radiusDp * density }
    }

    private fun darken(c: Int, factor: Float): Int {
        fun ch(shift: Int) = ((c shr shift and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * Restyle a screen that was built from bare platform widgets.
     *
     * Eleven screens predate this file and each assembled its own `Button`s and
     * `EditText`s, which meant every one of them rendered in whatever the
     * Material default happened to be: square-ish, all-caps-ish, and grey. They
     * could each be rewritten — but the honest reading is that this is a
     * *presentation* concern, and one walk of the finished hierarchy fixes all
     * of them at once and keeps fixing the next screen someone writes.
     *
     * Call it after the screen has added its views. It is idempotent.
     *
     * A button tagged [PRIMARY] gets the filled accent; every other button gets
     * the tonal treatment, which is the right default — a screen where three
     * buttons all shout has no primary action at all.
     */
    fun polish(root: View) {
        when (root) {
            is android.widget.EditText -> styleField(root)
            is android.widget.CheckBox -> styleCheckbox(root)
            // Order matters: CheckBox and EditText are both Buttons/TextViews
            // further up the hierarchy, so they are matched first.
            is android.widget.Button -> styleButton(root, root.tag == PRIMARY)
            is ViewGroup -> for (i in 0 until root.childCount) polish(root.getChildAt(i))
            else -> Unit
        }
    }

    fun styleButton(b: android.widget.Button, primary: Boolean) {
        b.isAllCaps = false
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        b.setTypeface(b.typeface, Typeface.BOLD)
        b.minHeight = dp(48)                       // §14.10
        b.minimumHeight = dp(48)
        b.setPadding(dp(20), dp(12), dp(20), dp(12))
        b.stateListAnimator = null                 // kills the Material lift
        if (primary) {
            b.setTextColor(colour(R.color.social_on_accent_fill))
            b.background = ripple(accentFill(16f))
            b.elevation = dp(1).toFloat()
        } else {
            b.setTextColor(colour(R.color.social_accent))
            b.background = ripple(rounded(colour(R.color.social_surface2), 16f))
            b.elevation = 0f
        }
        (b.layoutParams as? LinearLayout.LayoutParams)?.let {
            if (it.topMargin == 0) it.topMargin = dp(8)
        }
    }

    fun styleField(e: android.widget.EditText) {
        e.background = rounded(colour(R.color.social_surface2), 16f)
        e.setPadding(dp(16), dp(13), dp(16), dp(13))
        e.minHeight = dp(48)                       // §14.10
        e.minimumHeight = dp(48)
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        e.setTextColor(colour(R.color.social_text))
        e.setHintTextColor(colour(R.color.social_muted))
        (e.layoutParams as? LinearLayout.LayoutParams)?.let {
            if (it.topMargin == 0) it.topMargin = dp(8)
        }
    }

    fun styleCheckbox(c: android.widget.CheckBox) {
        c.buttonTintList = ColorStateList.valueOf(colour(R.color.social_accent))
        c.setTextColor(colour(R.color.social_text))
        c.setPadding(dp(10), dp(12), 0, dp(12))
        c.minHeight = dp(48)                       // §14.10
    }

    fun rounded(fillColour: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * density
        setColor(fillColour)
    }

    /**
     * Touch feedback. A control with none feels broken on Android in a way
     * people notice without being able to name, and every one of these screens
     * was built from bare `View`s that have none by default.
     */
    fun ripple(content: Drawable?, circular: Boolean = false): Drawable {
        val tint = ColorStateList.valueOf(
            (0x33 shl 24) or (colour(R.color.social_text) and 0xFFFFFF)
        )
        val mask = if (circular)
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(-0x1) }
        else
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = if (content is GradientDrawable) content.cornerRadius else 0f
                setColor(-0x1)
            }
        return RippleDrawable(tint, content, mask)
    }

    /**
     * A column that refuses to grow past a width.
     *
     * A bubble that stretches to the full screen is not a bubble — the eye
     * reads "left column / right column" off the *ragged edge*, and a paragraph
     * that reaches both margins destroys it. `LinearLayout` has no `maxWidth`,
     * and the usual workaround (a weighted child) forces every bubble to that
     * width including a one-word reply, which is worse. Fourteen lines of
     * `onMeasure` is the honest fix.
     */
    private class Bounded(ctx: android.content.Context) : LinearLayout(ctx) {
        var limit: Int = Int.MAX_VALUE

        // Same trap one level down: a child added to this bubble would default
        // to MATCH_PARENT and drag the bubble back out to the cap, which is
        // the circular version of the bug above and much harder to see.
        override fun generateDefaultLayoutParams(): LayoutParams =
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val mode = MeasureSpec.getMode(widthSpec)
            val size = MeasureSpec.getSize(widthSpec)
            val capped = if (mode == MeasureSpec.UNSPECIFIED) limit else minOf(size, limit)
            val newMode = if (mode == MeasureSpec.UNSPECIFIED) MeasureSpec.AT_MOST else mode
            super.onMeasure(MeasureSpec.makeMeasureSpec(capped, newMode), heightSpec)
        }
    }

    /** The width the screen gives a bubble: most of it, but never all of it. */
    fun bubbleMaxWidth(): Int =
        (a.resources.displayMetrics.widthPixels * 0.76f).toInt()

    fun spacer(heightDp: Int): View = View(a).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(heightDp))
    }

    private fun spacerWidth(px: Int): View = View(a).apply {
        layoutParams = LinearLayout.LayoutParams(px, 1)
    }

    /**
     * §14.1 — an inbox that shows a full timestamp on every row is unreadable,
     * and one that shows "3d" on a message from March is useless. This is the
     * usual compromise, and it lives here so the inbox and the timeline agree.
     */
    fun relativeTime(ts: Long): String {
        val mins = (System.currentTimeMillis() - ts) / 60_000
        return when {
            mins < 1 -> "now"
            mins < 60 -> "${mins}m"
            mins < 60 * 24 -> "${mins / 60}h"
            mins < 60 * 24 * 7 -> "${mins / (60 * 24)}d"
            else -> android.text.format.DateFormat.getDateFormat(a)
                .format(java.util.Date(ts))
        }
    }

    /** The clock time under a message. Respects the 12/24-hour system setting. */
    fun clockTime(ts: Long): String =
        android.text.format.DateFormat.getTimeFormat(a).format(java.util.Date(ts))

    companion object {
        /** Tag a button with this to have [polish] give it the filled accent. */
        const val PRIMARY = "chrome:primary"
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
