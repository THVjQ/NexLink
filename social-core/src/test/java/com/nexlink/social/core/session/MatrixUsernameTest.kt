package com.nexlink.social.core.session

import org.junit.Assert.assertEquals
import org.junit.Test

class MatrixUsernameTest {

    @Test fun `a bare localpart is unchanged`() {
        assertEquals("thvjq", MatrixUsername.normalise("thvjq"))
    }

    /** The exact string that failed on 2026-09-16. */
    @Test fun `a full address becomes its localpart`() {
        assertEquals("thvjq", MatrixUsername.normalise("@thvjq:nexlink.thvjq.com.au"))
    }

    @Test fun `a leading at sign alone is dropped`() {
        assertEquals("thvjq", MatrixUsername.normalise("@thvjq"))
    }

    /**
     * The display name is `THVjQ` and the localpart can never be, so the string
     * someone copies off their own profile is frequently uppercase.
     */
    @Test fun `capitals are folded`() {
        assertEquals("thvjq", MatrixUsername.normalise("THVjQ"))
        assertEquals("thvjq", MatrixUsername.normalise("@THVjQ:NexLink.thvjq.com.au"))
    }

    @Test fun `whitespace from a paste is removed`() {
        assertEquals("thvjq", MatrixUsername.normalise("  @thvjq:nexlink.thvjq.com.au  "))
        assertEquals("thvjq", MatrixUsername.normalise("@ thvjq :x"))
    }

    @Test fun `it does not invent a name from nothing`() {
        assertEquals("", MatrixUsername.normalise(""))
        assertEquals("", MatrixUsername.normalise("   "))
        assertEquals("", MatrixUsername.normalise("@"))
    }
}
