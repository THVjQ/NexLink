package com.nexlink.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareIntakeTest {

    @Test fun `nothing shared gives nothing to send`() {
        assertNull(ShareIntake.messageFor(null, null))
    }

    @Test fun `whitespace is the same as absent`() {
        assertNull(ShareIntake.messageFor("   ", "\n\t "))
    }

    @Test fun `plain text passes through trimmed`() {
        assertEquals("hello", ShareIntake.messageFor("  hello  ", null))
    }

    @Test fun `subject alone is used when there is no text`() {
        assertEquals("A title", ShareIntake.messageFor(null, "A title"))
    }

    /** The browser case: title in the subject, URL in the text. Keep both. */
    @Test fun `web page share keeps title and url`() {
        assertEquals(
            "Some Article\nhttps://example.com/a",
            ShareIntake.messageFor("https://example.com/a", "Some Article"),
        )
    }

    /** Many apps already put the title in the text. Do not say it twice. */
    @Test fun `title already inside the text is not repeated`() {
        assertEquals(
            "Some Article https://example.com/a",
            ShareIntake.messageFor("Some Article https://example.com/a", "Some Article"),
        )
    }
}
