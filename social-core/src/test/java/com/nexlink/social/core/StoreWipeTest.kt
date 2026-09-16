package com.nexlink.social.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StoreWipeTest {

    private fun store(root: File, name: String = "matrix"): File =
        File(root, name).apply {
            mkdirs()
            File(this, "matrix-sdk-crypto.sqlite3").writeText("crypto")
            File(this, "nested").apply { mkdirs() }
                .let { File(it, "state.sqlite3").writeText("state") }
        }

    @Test fun `an ordinary store is removed`() {
        val root = Files.createTempDirectory("wipe").toFile()
        val dir = store(root)
        assertTrue(StoreWipe.wipe(listOf(dir)))
        assertFalse(dir.exists())
    }

    @Test fun `a missing store is not an error`() {
        val root = Files.createTempDirectory("wipe").toFile()
        assertTrue(StoreWipe.wipe(listOf(File(root, "matrix"))))
    }

    /**
     * The case that caused the bug. A file inside the store cannot be removed,
     * so the delete is partial — and the old behaviour left the store sitting
     * at the live path, where the next sign-in opened it and failed with
     * `MismatchedAccount`.
     *
     * The store must be gone **from the path a new session will use**, even
     * though its bytes survive somewhere.
     */
    @Test fun `a store that cannot be deleted is still moved out of the way`() {
        val root = Files.createTempDirectory("wipe").toFile()
        val dir = store(root)
        val locked = File(dir, "nested")
        try {
            // Removing a child needs write permission on its directory, so this
            // makes deleteRecursively fail part-way exactly as a held-open
            // store does.
            check(locked.setWritable(false, false)) { "could not make the directory read-only" }

            StoreWipe.wipe(listOf(dir))

            assertFalse("the store is still at the live path", dir.exists())
            assertTrue("nothing was set aside",
                root.listFiles()!!.any { it.name.contains(StoreWipe.DISCARD_SUFFIX) })
        } finally {
            locked.setWritable(true, false)
        }
    }

    @Test fun `the sweep clears what an earlier wipe could not`() {
        val root = Files.createTempDirectory("wipe").toFile()
        val leftover = File(root, "matrix${StoreWipe.DISCARD_SUFFIX}123").apply { mkdirs() }
        File(leftover, "crypto.sqlite3").writeText("x")

        StoreWipe.sweep(listOf(root))

        assertFalse(leftover.exists())
    }

    @Test fun `the sweep leaves the live store alone`() {
        val root = Files.createTempDirectory("wipe").toFile()
        val live = store(root)
        StoreWipe.sweep(listOf(root))
        assertTrue(live.exists())
    }
}
