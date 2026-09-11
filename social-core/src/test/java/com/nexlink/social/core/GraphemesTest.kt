package com.nexlink.social.core

import org.junit.Assert.*
import org.junit.Test

/** §14.4.3 — the cases that break naive truncation. */
class GraphemesTest {

    private val family = "👩‍👩‍👧" // 👩‍👩‍👧
    private val thumbsUpTone = "👍🏽"                   // 👍🏽
    private val flag = "🇦🇺"                           // 🇦🇺

    @Test fun `a ZWJ sequence is one grapheme, not five code points`() {
        assertEquals(1, Graphemes.count(family))
        assertTrue(Graphemes.isSingleGrapheme(family))
    }

    @Test fun `a skin tone modifier does not make two graphemes`() {
        assertEquals(1, Graphemes.count(thumbsUpTone))
        assertTrue(Graphemes.isSingleGrapheme(thumbsUpTone))
    }

    @Test fun `a regional indicator flag is one grapheme`() {
        assertEquals(1, Graphemes.count(flag))
    }

    /** The actual bug: String.take() cuts a surrogate pair in half. */
    @Test fun `truncation never splits an emoji`() {
        val text = "hi " + family.repeat(20)
        val cut = Graphemes.truncate(text, 8)
        // A split surrogate leaves an unpaired one behind — the broken box.
        assertFalse("truncation left an unpaired high surrogate",
            cut.isNotEmpty() && cut.last().isHighSurrogate())
        assertTrue(cut.length < text.length)
    }

    @Test fun `naive take() DOES split it — this is what we are avoiding`() {
        val text = "a" + family
        // "a" + first half of the first surrogate pair
        val naive = text.take(2)
        assertTrue("precondition: take() splits the pair", naive.last().isHighSurrogate())
        // and ours does not
        assertFalse(Graphemes.truncate(text, 2).last().isHighSurrogate())
    }

    @Test fun `short text is returned unchanged`() {
        assertEquals("hello", Graphemes.truncate("hello", 20))
    }
}
