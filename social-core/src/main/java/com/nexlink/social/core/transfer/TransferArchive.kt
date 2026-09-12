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
    fun write(
        out: OutputStream,
        passphrase: CharArray,
        sources: Map<String, File>,
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
                    dir.walkTopDown().filter { it.isFile }.forEach { f ->
                        val rel = "$name/${f.relativeTo(dir).invariantPath()}"
                        onProgress(rel)
                        zip.putNextEntry(ZipEntry(rel))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        files++; bytes += f.length()
                    }
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
                    if (target != null && rest.isNotEmpty() && !e.isDirectory) {
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
