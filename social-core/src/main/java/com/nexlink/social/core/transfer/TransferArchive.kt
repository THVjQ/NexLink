package com.nexlink.social.core.transfer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * §7.5.2 — the encrypted export file.
 *
 * *"An encrypted archive, passphrase-protected, written to user-chosen
 * storage."* For archival, or for moving to a new phone when neither device can
 * be online at the same time (§7.5's third row).
 *
 * **This is not [com.nexlink.social.core.Export].** That one writes plaintext
 * JSON on purpose: it is §32.4's data-protection answer, it exists to be read
 * by the user and by other software, and its plaintext-ness is the feature.
 * This one is the opposite — it carries the local store, including the crypto
 * store, and is written to wherever the user keeps files. Confusing the two
 * would put message keys in a plaintext file in someone's Downloads folder.
 *
 * ## Construction
 *
 * AES-256-GCM over a zip, with the key derived from the passphrase by
 * PBKDF2-HMAC-SHA256.
 *
 * PBKDF2 rather than Argon2id, which would be the better choice: Argon2 is not
 * in the platform and adding a native KDF to `:social-core` is a dependency
 * decision of its own (§11's whole argument). [PBKDF2_ITERATIONS] is set to
 * OWASP's current floor for SHA-256, and the parameters are written into the
 * header so raising them later still reads old files.
 *
 * GCM authenticates as well as encrypts, so a corrupted or tampered archive
 * fails on the tag rather than yielding garbage that a restore might half-apply.
 * The header is fed in as AAD, which binds the salt and the iteration count to
 * the ciphertext: an attacker cannot lower the iteration count in the header to
 * make a dictionary attack cheaper without invalidating the tag.
 */
object TransferArchive {

    /** Magic + version, so a future format change is detectable, not silent. */
    private const val MAGIC = "NLSOCIAL"
    private const val VERSION = 1

    private const val PBKDF2_ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    data class Manifest(val files: Int, val bytes: Long)

    /**
     * Encrypt [sources] into [out].
     *
     * @param sources directories whose contents go into the archive, each under
     *   the name it is keyed by — so a restore knows where each came back to.
     */
    /**
     * Entries not on disk, written into the archive under these names.
     *
     * §7.5.2's credentials go here (`SessionStore.exportBundle`) rather than
     * being picked up from `shared_prefs/`, because that file is sealed by a
     * Keystore key that never leaves the device — see the note on
     * `SessionStore.exportBundle`.
     */
    const val BUNDLE_ENTRY = "keys/session.json"

    fun write(
        out: OutputStream,
        passphrase: CharArray,
        sources: Map<String, File>,
        extras: Map<String, ByteArray> = emptyMap(),
        /**
         * Files to leave out, by absolute path.
         *
         * Exists because the first build archived `cacheDir` while writing its
         * own output into `cacheDir`, so the archive contained a copy of itself
         * — visible in the entry list as `cache/nexlink-social-backup.nlsx`.
         */
        exclude: Set<String> = emptySet(),
        onProgress: (String) -> Unit = {}
    ): Manifest {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(rnd::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(rnd::nextBytes)

        val header = DataOutputStream(out)
        header.writeUTF(MAGIC)
        header.writeInt(VERSION)
        header.writeInt(PBKDF2_ITERATIONS)
        header.writeInt(salt.size); header.write(salt)
        header.writeInt(nonce.size); header.write(nonce)
        header.flush()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, derive(passphrase, salt, PBKDF2_ITERATIONS),
                 GCMParameterSpec(TAG_BITS, nonce))
            // Bind the header to the ciphertext — see the class note on AAD.
            updateAAD(aad(PBKDF2_ITERATIONS, salt, nonce))
        }

        var files = 0
        var bytes = 0L
        CipherOutputStream(out, cipher).use { enc ->
            ZipOutputStream(enc).use { zip ->
                sources.forEach { (name, dir) ->
                    if (!dir.exists()) return@forEach
                    dir.walkTopDown()
                        .filter { it.isFile && it.absolutePath !in exclude }
                        .forEach { f ->
                        val rel = "$name/${f.relativeTo(dir).invariantPath()}"
                        onProgress(rel)
                        zip.putNextEntry(ZipEntry(rel))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        files++; bytes += f.length()
                    }
                }
                extras.forEach { (name, content) ->
                    onProgress(name)
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                    files++; bytes += content.size
                }
            }
        }
        return Manifest(files, bytes)
    }

    /**
     * Decrypt [input] into [targets], keyed the same way [write] was given them.
     *
     * Throws on a wrong passphrase or a damaged file — GCM's tag check fails and
     * the exception is the honest answer. A caller must **not** treat a failure
     * as "restore what we got": a partial store is worse than none, because it
     * looks like a working account with silently missing keys.
     */
    fun read(
        input: InputStream,
        passphrase: CharArray,
        targets: Map<String, File>,
        /**
         * Called for an entry whose top-level name is not in [targets] — the
         * synthetic ones. Handed to the caller instead of being written to
         * disk, because §7.5.2's credentials belong in the Keystore-backed
         * prefs, not in a file on the filesystem.
         */
        onExtra: (String, ByteArray) -> Unit = { _, _ -> },
        onProgress: (String) -> Unit = {}
    ): Manifest {
        val header = DataInputStream(input)
        require(header.readUTF() == MAGIC) { "Not a NexLink Social archive." }
        val version = header.readInt()
        require(version <= VERSION) {
            "This archive was written by a newer version of the app."
        }
        val iterations = header.readInt()
        val salt = ByteArray(header.readInt()).also(header::readFully)
        val nonce = ByteArray(header.readInt()).also(header::readFully)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, derive(passphrase, salt, iterations),
                 GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad(iterations, salt, nonce))
        }

        var files = 0
        var bytes = 0L
        CipherInputStream(input, cipher).use { dec ->
            ZipInputStream(dec).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    val root = name.substringBefore('/')
                    val rest = name.substringAfter('/', "")
                    val target = targets[root]
                    if (target == null && !e.isDirectory) {
                        onExtra(name, zip.readBytes())
                    } else if (target != null && rest.isNotEmpty() && !e.isDirectory) {
                        val dest = File(target, rest)
                        // Zip-slip: an entry named "../../x" would otherwise
                        // write outside the target. The archive is one we wrote,
                        // but it arrives from user storage and may not be.
                        require(dest.canonicalPath.startsWith(target.canonicalPath + File.separator)) {
                            "Archive entry escapes its directory."
                        }
                        dest.parentFile?.mkdirs()
                        onProgress(name)
                        dest.outputStream().use { zip.copyTo(it) }
                        files++; bytes += dest.length()
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
        }
        return Manifest(files, bytes)
    }

    /**
     * §7.5.3 — [read], but nothing lands until the whole archive has decrypted.
     *
     * §7.5.3: *"a partial store is worse than none, because it looks like a
     * working account with silently missing keys."* This extracts into
     * [staging] and promotes into [targets] only once the whole archive has
     * decrypted, so a failure leaves the device exactly as it was.
     *
     * **A note on why, because the obvious reason turned out to be false.**
     * This was written believing [read] streams entries to disk and only
     * discovers a bad archive at the GCM tag. On the JVM it does not — SunJCE
     * buffers the ciphertext and releases nothing until the tag verifies, and
     * the test that was meant to prove otherwise proved that. Android uses
     * Conscrypt, not SunJCE, and whether *it* can leave a partial store is
     * unmeasured.
     *
     * The reason that does hold: staging makes the **whole restore** atomic,
     * not just the decryption. The caller checks §7.5.2's credential bundle
     * after extraction, and without staging a bundle-less archive would have
     * overwritten the store before that refusal could happen.
     *
     * @return the synthetic entries, keyed by name — §7.5.2's credential bundle
     *   among them. They are returned rather than written anywhere: they belong
     *   in the Keystore-backed store, which is the caller's business.
     */
    fun restore(
        input: InputStream,
        passphrase: CharArray,
        staging: File,
        targets: Map<String, File>,
        onProgress: (String) -> Unit = {}
    ): Pair<Manifest, Map<String, ByteArray>> {
        staging.deleteRecursively()
        val staged = targets.mapValues { (name, _) -> File(staging, name).apply { mkdirs() } }
        val extras = mutableMapOf<String, ByteArray>()
        val manifest = try {
            read(input, passphrase, staged, { n, b -> extras[n] = b }, onProgress)
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        }
        // Verified. Only now does anything move.
        for ((name, dest) in targets) staged.getValue(name).copyRecursively(dest, overwrite = true)
        staging.deleteRecursively()
        return manifest to extras
    }

    private fun derive(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
            return SecretKeySpec(k.encoded, "AES")
        } finally {
            // PBEKeySpec copies the passphrase internally; clear that copy. The
            // caller's array is the caller's to clear.
            spec.clearPassword()
        }
    }

    private fun aad(iterations: Int, salt: ByteArray, nonce: ByteArray): ByteArray =
        MAGIC.toByteArray() + VERSION.toByte() + iterations.toString().toByteArray() + salt + nonce

    /** Zip entries use '/' on every platform, whatever the host separator is. */
    private fun File.invariantPath(): String = path.replace(File.separatorChar, '/')
}
