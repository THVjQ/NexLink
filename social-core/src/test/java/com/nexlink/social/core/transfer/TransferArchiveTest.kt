package com.nexlink.social.core.transfer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** §7.5.2 — the encrypted export file. */
class TransferArchiveTest {

    @get:Rule val tmp = TemporaryFolder()

    private val pass = "correct horse battery staple".toCharArray()

    private fun store(): File = tmp.newFolder("store").apply {
        File(this, "matrix-sdk-crypto.sqlite3").writeBytes(ByteArray(4096) { it.toByte() })
        File(this, "nested").mkdirs()
        File(this, "nested/session.json").writeText("""{"user":"@a:example"}""")
    }

    @Test
    fun `round trips every file and its bytes`() {
        val src = store()
        val out = ByteArrayOutputStream()
        val written = TransferArchive.write(out, pass.copyOf(), mapOf("files" to src))
        assertEquals(2, written.files)

        val dest = tmp.newFolder("restored")
        val read = TransferArchive.read(
            ByteArrayInputStream(out.toByteArray()), pass.copyOf(), mapOf("files" to dest)
        )
        assertEquals(written.files, read.files)
        assertArrayEquals(
            File(src, "matrix-sdk-crypto.sqlite3").readBytes(),
            File(dest, "matrix-sdk-crypto.sqlite3").readBytes()
        )
        assertEquals(
            File(src, "nested/session.json").readText(),
            File(dest, "nested/session.json").readText()
        )
    }

    /**
     * The archive holds the crypto store. If it were readable without the
     * passphrase the whole feature would be a liability, so this asserts the
     * obvious thing explicitly rather than trusting that "we called a cipher".
     */
    @Test
    fun `plaintext does not appear in the archive`() {
        val src = store()
        val out = ByteArrayOutputStream()
        TransferArchive.write(out, pass.copyOf(), mapOf("files" to src))
        val bytes = out.toByteArray()
        assertTrue(
            "the account id must not be readable in the archive",
            !String(bytes, Charsets.ISO_8859_1).contains("@a:example")
        )
    }

    /** A wrong passphrase must fail loudly, not yield partial garbage. */
    @Test
    fun `a wrong passphrase throws`() {
        val out = ByteArrayOutputStream()
        TransferArchive.write(out, pass.copyOf(), mapOf("files" to store()))
        val dest = tmp.newFolder("restored")
        assertThrows(Exception::class.java) {
            TransferArchive.read(
                ByteArrayInputStream(out.toByteArray()),
                "not the passphrase at all".toCharArray(),
                mapOf("files" to dest)
            )
        }
    }

    /**
     * GCM's tag must reject a modified archive. Without this, a damaged file
     * could restore a store with a silently altered key and present as a working
     * account that cannot decrypt anything.
     */
    @Test
    fun `a tampered archive throws`() {
        val out = ByteArrayOutputStream()
        TransferArchive.write(out, pass.copyOf(), mapOf("files" to store()))
        val bytes = out.toByteArray()
        bytes[bytes.size - 40] = (bytes[bytes.size - 40] + 1).toByte()
        assertThrows(Exception::class.java) {
            TransferArchive.read(
                ByteArrayInputStream(bytes), pass.copyOf(),
                mapOf("files" to tmp.newFolder("restored"))
            )
        }
    }

    /**
     * The header is authenticated as AAD, so lowering the iteration count to
     * make a dictionary attack cheaper must invalidate the tag.
     */
    @Test
    fun `editing the iteration count in the header breaks the tag`() {
        val out = ByteArrayOutputStream()
        TransferArchive.write(out, pass.copyOf(), mapOf("files" to store()))
        val bytes = out.toByteArray()
        // Header: UTF "NLSOCIAL" (2 + 8), version (4), then iterations (4).
        val at = 2 + 8 + 4
        val lowered = 1_000
        bytes[at] = (lowered ushr 24).toByte()
        bytes[at + 1] = (lowered ushr 16).toByte()
        bytes[at + 2] = (lowered ushr 8).toByte()
        bytes[at + 3] = lowered.toByte()
        assertThrows(Exception::class.java) {
            TransferArchive.read(
                ByteArrayInputStream(bytes), pass.copyOf(),
                mapOf("files" to tmp.newFolder("restored"))
            )
        }
    }

    /**
     * §7.5.2 — **the regression test for the defect that made the first build
     * useless.**
     *
     * It packed `filesDir` and `cacheDir` and looked complete, because every
     * SQLite store was present. But the store key lives in
     * `EncryptedSharedPreferences`, which is in `shared_prefs/` — a *sibling*
     * of `filesDir` — so the archive held stores nothing could open.
     *
     * The key now travels as a synthetic entry, encrypted by the passphrase
     * rather than by the Keystore, and comes back through `onExtra` rather than
     * being written to disk.
     */
    @Test
    fun `synthetic entries survive the round trip and are not written to disk`() {
        val src = store()
        val bundle = """{"v":1,"storeKey":"c3VwZXJzZWNyZXQ="}""".toByteArray()
        val out = ByteArrayOutputStream()
        TransferArchive.write(
            out, pass.copyOf(), mapOf("files" to src),
            extras = mapOf(TransferArchive.BUNDLE_ENTRY to bundle)
        )

        val dest = tmp.newFolder("restored")
        val extras = mutableMapOf<String, ByteArray>()
        TransferArchive.read(
            ByteArrayInputStream(out.toByteArray()), pass.copyOf(),
            mapOf("files" to dest),
            onExtra = { name, content -> extras[name] = content }
        )

        assertArrayEquals(bundle, extras[TransferArchive.BUNDLE_ENTRY])
        assertTrue(
            "credentials must not be written to the filesystem",
            !File(dest, "keys").exists()
        )
    }

    /**
     * §7.5.2 — the other defect in the first build: the output was written into
     * `cacheDir` while `cacheDir` was being archived, so the archive contained
     * a copy of itself. Visible in the shipped file as
     * `cache/nexlink-social-backup.nlsx`.
     */
    @Test
    fun `an excluded file is left out`() {
        val src = store()
        val itself = File(src, "backup.nlsx").apply { writeBytes(ByteArray(999)) }
        val out = ByteArrayOutputStream()
        val m = TransferArchive.write(
            out, pass.copyOf(), mapOf("files" to src),
            exclude = setOf(itself.absolutePath)
        )
        assertEquals("the excluded file must not be counted", 2, m.files)

        val dest = tmp.newFolder("restored")
        TransferArchive.read(
            ByteArrayInputStream(out.toByteArray()), pass.copyOf(), mapOf("files" to dest)
        )
        assertTrue(
            "the archive must not contain itself",
            !File(dest, "backup.nlsx").exists()
        )
    }

    /** Two archives of the same data must differ — fresh salt and nonce. */
    @Test
    fun `two archives of the same store are not identical`() {
        val src = store()
        val a = ByteArrayOutputStream().also {
            TransferArchive.write(it, pass.copyOf(), mapOf("files" to src))
        }.toByteArray()
        val b = ByteArrayOutputStream().also {
            TransferArchive.write(it, pass.copyOf(), mapOf("files" to src))
        }.toByteArray()
        assertNotEquals(
            "a fixed salt or nonce would make identical exports comparable",
            String(a, Charsets.ISO_8859_1), String(b, Charsets.ISO_8859_1)
        )
    }
    // ---- §7.5.3 restore: nothing lands until the whole archive decrypts ----

    @Test
    fun `restore promotes the archive and hands back the synthetic entries`() {
        val src = store()
        val out = ByteArrayOutputStream()
        TransferArchive.write(
            out, pass.copyOf(), mapOf("files" to src),
            extras = mapOf(TransferArchive.BUNDLE_ENTRY to """{"v":1}""".toByteArray())
        )

        val dest = tmp.newFolder("target")
        val (manifest, extras) = TransferArchive.restore(
            ByteArrayInputStream(out.toByteArray()), pass.copyOf(),
            staging = tmp.newFolder("staging-ok"), targets = mapOf("files" to dest)
        )

        assertEquals(2, manifest.files)
        assertArrayEquals(
            File(src, "matrix-sdk-crypto.sqlite3").readBytes(),
            File(dest, "matrix-sdk-crypto.sqlite3").readBytes()
        )
        // §7.5.2's credentials are returned, never written to the filesystem.
        assertEquals("""{"v":1}""", String(extras.getValue(TransferArchive.BUNDLE_ENTRY)))
        assertTrue(File(dest, "nested/session.json").exists())
    }

    /**
     * The reason [TransferArchive.restore] exists at all.
     *
     * §7.5.3: *"a partial store is worse than none, because it looks like a
     * working account with silently missing keys."* A wrong passphrase fails at
     * the GCM tag, which is checked **after** the plaintext has streamed — so
     * the naive path has already written files by the time it throws.
     */
    @Test
    fun `a wrong passphrase leaves the target untouched`() {
        val src = store()
        val out = ByteArrayOutputStream()
        TransferArchive.write(out, pass.copyOf(), mapOf("files" to src))

        val dest = tmp.newFolder("target-untouched")
        File(dest, "pre-existing.txt").writeText("still here")

        val staging = tmp.newFolder("staging-bad")
        assertThrows(Exception::class.java) {
            TransferArchive.restore(
                ByteArrayInputStream(out.toByteArray()), "wrong".toCharArray(),
                staging = staging, targets = mapOf("files" to dest)
            )
        }

        assertEquals(
            "the restore must not leave anything behind in the target",
            listOf("pre-existing.txt"), dest.list()!!.sorted()
        )
        assertEquals("still here", File(dest, "pre-existing.txt").readText())
        assertTrue("staging must be cleaned up on failure", !staging.exists())
    }

    /**
     * **This test was written to prove the staging was load-bearing, and it
     * proved the opposite — so it now records what is actually true.**
     *
     * The claim was that `read()` streams entries to disk and only discovers a
     * bad archive at the GCM tag, leaving a partial store. On the JVM it does
     * not: SunJCE buffers the whole ciphertext and releases nothing until the
     * tag verifies, which is the entire point of authenticated encryption. Not
     * one byte reaches disk, at 8 KB or at 8 MB.
     *
     * That does **not** make [TransferArchive.restore]'s staging pointless, and
     * it does not make it verified either:
     *
     * - **Android does not use SunJCE.** Conscrypt is a different
     *   implementation with its own buffering behaviour, and this test cannot
     *   see it. Whether a partial store is possible on a real phone is
     *   unmeasured.
     * - Staging is what makes the *whole restore* atomic, not just the
     *   decryption: the credential bundle is checked after extraction, and
     *   without staging a bundle-less archive would already have overwritten
     *   the store before the refusal (§7.5.4's defect, from the other side).
     *
     * So the staging stays, on the second reason, and the first is written
     * down as unknown rather than claimed.
     */
    @Test
    fun `on the JVM, read alone writes nothing when the tag fails`() {
        // 8 MB, so this is about the provider's behaviour rather than a
        // fixture too small to stream.
        val src = tmp.newFolder("big-store")
        repeat(4) { n ->
            File(src, "chunk$n.sqlite3").writeBytes(ByteArray(2 * 1024 * 1024) { it.toByte() })
        }
        val out = ByteArrayOutputStream()
        TransferArchive.write(out, pass.copyOf(), mapOf("files" to src))
        val truncated = out.toByteArray().copyOf(out.size() - 64)

        val dest = tmp.newFolder("target-naive")
        assertThrows(Exception::class.java) {
            TransferArchive.read(
                ByteArrayInputStream(truncated), pass.copyOf(), mapOf("files" to dest)
            )
        }
        assertTrue(
            "if this ever fails, the provider started releasing unauthenticated " +
            "plaintext — which would make restore()'s staging load-bearing for " +
            "the reason it was first claimed to be",
            dest.walkTopDown().none { it.isFile }
        )
    }

    /** The same for [TransferArchive.restore], which must hold regardless. */
    @Test
    fun `a truncated archive leaves the target untouched`() {
        val src = store()
        val out = ByteArrayOutputStream()
        TransferArchive.write(out, pass.copyOf(), mapOf("files" to src))
        val truncated = out.toByteArray().copyOf(out.size() - 64)

        val dest = tmp.newFolder("target-truncated")
        assertThrows(Exception::class.java) {
            TransferArchive.restore(
                ByteArrayInputStream(truncated), pass.copyOf(),
                staging = tmp.newFolder("staging-trunc"), targets = mapOf("files" to dest)
            )
        }
        assertEquals(0, dest.list()!!.size)
    }
}

/** §7.5.2 — "the UI must not allow reuse" of the recovery key. */
class PassphraseTest {

    /**
     * The real key generated on the test account, 2026-09-12. Kept verbatim
     * because the rule is about a *shape* and a made-up example could drift
     * from the shape the SDK actually emits.
     *
     * It belongs to a throwaway account on the development homeserver and
     * guards nothing.
     */
    private val realKey = "EsU3 G5nq 5QkF N9Br Svbn uz7a xUHE pzVy xetu wLAh WSTQ UzFf"

    @Test
    fun `the recovery key is refused, spaced or not`() {
        listOf(realKey, realKey.replace(" ", ""), realKey.replace(" ", "\n")).forEach {
            assertTrue("should reject: $it", Passphrase.looksLikeRecoveryKey(it))
            val v = Passphrase.check(it)
            assertTrue(v is Passphrase.Verdict.Rejected)
            assertTrue(
                "the message must say why, not just 'invalid'",
                (v as Passphrase.Verdict.Rejected).reason.contains("recovery key")
            )
        }
    }

    @Test
    fun `an ordinary long passphrase is accepted`() {
        assertEquals(Passphrase.Verdict.Ok, Passphrase.check("a whole sentence as a passphrase"))
    }

    @Test
    fun `short passphrases are refused`() {
        assertTrue(Passphrase.check("short") is Passphrase.Verdict.Rejected)
        assertTrue(Passphrase.check("a".repeat(Passphrase.MIN_LENGTH)) is Passphrase.Verdict.Ok)
    }

    @Test
    fun `a mismatched confirmation is refused`() {
        assertTrue(
            Passphrase.check("a good long passphrase", "a good long passphrasf")
                is Passphrase.Verdict.Rejected
        )
    }

    /**
     * The check is by shape, so it must not reject ordinary text that happens
     * to be long — a false positive here blocks a legitimate passphrase and the
     * user cannot tell why.
     */
    @Test
    fun `long ordinary text is not mistaken for a recovery key`() {
        listOf(
            "Es" + "a".repeat(46),                       // right length, not base58-varied but IS base58
            "a".repeat(48),                              // right length, no Es prefix
            "Es 0OIl " + "b".repeat(41)                  // has base58's excluded characters
        ).forEachIndexed { i, s ->
            if (i == 0) {
                // Honest: this one IS indistinguishable from a key by shape and
                // is rejected. Documented rather than hidden — the cost of a
                // shape check is that a 48-character base58 string starting
                // "Es" cannot be used as a passphrase. Nobody loses anything
                // real by picking a different one.
                assertTrue(Passphrase.looksLikeRecoveryKey(s))
            } else {
                assertTrue("must not reject: $s", !Passphrase.looksLikeRecoveryKey(s))
            }
        }
    }

}
