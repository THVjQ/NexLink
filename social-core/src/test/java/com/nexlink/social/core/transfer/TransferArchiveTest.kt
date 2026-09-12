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
