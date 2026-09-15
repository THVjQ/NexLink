package com.nexlink.social.ui.chrome

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The parts of the chrome that can only be wrong at measure time.
 *
 * Every screen in this app builds its views in Kotlin, which means the usual
 * layout mistakes do not show up as compiler errors or as anything a reviewer
 * can see by reading. Two of them were made and found by hand during the
 * 2026-09-15 rebuild, and both are here so they cannot come back:
 *
 *  - a floating button whose `LayoutParams` were replaced with WRAP_CONTENT,
 *    which measures to nothing because a drawn [Icon] has no intrinsic size;
 *  - a bubble that quietly grew to the full width of the screen because one of
 *    its children asked for MATCH_PARENT inside a wrap-content parent.
 *
 * The emulator is unusable here (§34.10) and the handsets are not always
 * available, so these are asserted on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChromeLayoutTest {

    private fun activity(): Activity =
        Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun exactly(px: Int) = View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)

    @Test fun `a floating button has a real size`() {
        val a = activity()
        val chrome = Chrome(a)
        val page = chrome.page("Inbox")
        val fab = chrome.fab(Icon.Kind.NEW_CHAT, "New conversation") {}
        page.float(fab)

        page.root.measure(exactly(1080), exactly(1920))
        page.root.layout(0, 0, 1080, 1920)

        // The bug this replaces produced a 36dp disc with no icon on it,
        // because WRAP_CONTENT plus a drawable with no intrinsic size is
        // padding and nothing else.
        assertEquals(chrome.dp(58), fab.measuredWidth)
        assertEquals(chrome.dp(58), fab.measuredHeight)
    }

    @Test fun `an icon control meets the 48dp minimum target`() {
        val a = activity()
        val chrome = Chrome(a)
        val button = chrome.iconButton(Icon.Kind.MORE, "More") {}
        val host = LinearLayout(a).apply { addView(button) }

        host.measure(exactly(1080), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        host.layout(0, 0, 1080, host.measuredHeight)

        // §14.10. A drawn glyph is 24dp; the target around it is not.
        assertTrue(
            "icon target was ${button.measuredWidth}px, want >= ${chrome.dp(48)}px",
            button.measuredWidth >= chrome.dp(48) && button.measuredHeight >= chrome.dp(48),
        )
    }

    @Test fun `a long message stops at the bubble width cap`() {
        val a = activity()
        val chrome = Chrome(a)
        val cap = 500
        val bubble = chrome.bubble(mine = true, maxWidthPx = cap)
        bubble.addView(TextView(a).apply {
            text = "a ".repeat(400)
        })
        val row = LinearLayout(a).apply {
            gravity = Gravity.END
            addView(bubble)
        }

        row.measure(exactly(1000), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        row.layout(0, 0, 1000, row.measuredHeight)

        assertTrue(
            "bubble measured ${bubble.measuredWidth}px, cap is ${cap}px",
            bubble.measuredWidth <= cap,
        )
        // And it is over on the speaker's side, which is the whole point of the
        // ragged edge: a bubble that reaches both margins reads as a paragraph.
        assertTrue("bubble was not aligned to the end", bubble.left > 0)
    }

    @Test fun `a short message does not stretch to the cap`() {
        val a = activity()
        val chrome = Chrome(a)
        val bubble = chrome.bubble(mine = false, maxWidthPx = 500)
        bubble.addView(TextView(a).apply { text = "ok" })
        val row = LinearLayout(a).apply { addView(bubble) }

        row.measure(exactly(1000), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))

        // The weighted-child workaround for "maxWidth" would have forced every
        // bubble to the cap, including a one-word reply. That is why Bounded
        // exists instead.
        assertTrue("a two-character bubble measured ${bubble.measuredWidth}px",
            bubble.measuredWidth < 500)
    }

    @Test fun `an empty state fills the width so its centring means something`() {
        val a = activity()
        val empty = Chrome(a).emptyState("No conversations yet", "Tap the pencil to start one.")
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, empty.layoutParams.width)
    }

    @Test fun `an avatar takes a whole code point and is stable per name`() {
        val a = activity()
        val chrome = Chrome(a)
        assertEquals("A", (chrome.avatar("alice") as TextView).text)
        assertEquals("@", (chrome.avatar("  @@bob") as TextView).text.toString().take(1))
        // An astral-plane first character must not be split into half a
        // surrogate pair, which renders as a broken box.
        val emoji = (chrome.avatar("🌻 sunflower") as TextView).text.toString()
        assertEquals(2, emoji.length)

        val one = (chrome.avatar("Charlie").background as android.graphics.drawable.GradientDrawable)
        val two = (chrome.avatar("Charlie").background as android.graphics.drawable.GradientDrawable)
        assertEquals(one.color?.defaultColor, two.color?.defaultColor)
    }

    @Test fun `a large unread count does not widen the row`() {
        val a = activity()
        assertEquals("99+", Chrome(a).badge(1234).text)
        assertEquals("7", Chrome(a).badge(7).text)
    }

    @Test fun `the title bar carries the title and hides an absent subtitle`() {
        val a = activity()
        val bar = Chrome(a).titleBar("Storage", subtitle = null)
        assertEquals("Storage", bar.titleView.text)
        assertEquals(View.GONE, bar.subtitleView.visibility)
        bar.subtitle("2 people")
        assertEquals(View.VISIBLE, bar.subtitleView.visibility)
    }
}
