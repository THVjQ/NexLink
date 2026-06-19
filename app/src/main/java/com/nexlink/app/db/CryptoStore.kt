package com.nexlink.app.db

import android.content.Context
import android.provider.Settings
import android.util.Base64
import java.security.*
import java.security.spec.*
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages EC P-256 identity keys and per-contact AES-256-GCM sessions.
 *
 * Wire format (sent as plain SMS):
 *   Key exchange  : [NXKEY1:<base64(X.509 EC pubkey)>]
 *   Encrypted msg : [NXMSG1:<base64(12-byte IV)>:<base64(AES-GCM ciphertext+tag)>]
 *
 * The identity key is derived deterministically from ANDROID_ID so reinstalling the
 * app (with the same signing certificate) always restores the same key — no backup needed.
 */
object CryptoStore {

    private const val PREFS          = "nx_crypto_v1"
    private const val KEY_MY_PRIV    = "my_priv"
    private const val KEY_MY_PUB     = "my_pub"
    private const val PFX_SESSION    = "sess_"
    private const val PFX_SENT       = "sent_"

    // ── Identity key ──────────────────────────────────────────────────────────

    fun getPublicKeyBytes(ctx: Context): ByteArray {
        val p = prefs(ctx)
        val stored = p.getString(KEY_MY_PUB, null)
        if (stored != null) return b64dec(stored)
        return generateAndStore(ctx)
    }

    private fun generateAndStore(ctx: Context): ByteArray {
        // Derive deterministically from ANDROID_ID — same device + same signing cert
        // always produces the same identity key, surviving uninstall/reinstall cycles.
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val seed = MessageDigest.getInstance("SHA-256")
            .digest("NexLink-id-v1:$androidId".toByteArray(Charsets.UTF_8))

        @Suppress("GetInstance")
        val rng = SecureRandom.getInstance("SHA1PRNG").also { it.setSeed(seed) }
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), rng)
        val kp = kpg.generateKeyPair()

        prefs(ctx).edit()
            .putString(KEY_MY_PRIV, b64enc(kp.private.encoded))
            .putString(KEY_MY_PUB,  b64enc(kp.public.encoded))
            .apply()
        return kp.public.encoded
    }

    private fun myPrivateKey(ctx: Context): PrivateKey {
        val p = prefs(ctx)
        var privB64 = p.getString(KEY_MY_PRIV, null)
        if (privB64 == null) { generateAndStore(ctx); privB64 = p.getString(KEY_MY_PRIV, null)!! }
        return KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(b64dec(privB64)))
    }

    // ── Session management ────────────────────────────────────────────────────

    /** Derives and stores the shared AES key from the peer's public key bytes. */
    fun storePeerKey(ctx: Context, address: String, pubKeyBytes: ByteArray) {
        val theirPub  = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(pubKeyBytes))
        val sharedKey = ecdh(myPrivateKey(ctx), theirPub)
        prefs(ctx).edit().putString(PFX_SESSION + norm(address), b64enc(sharedKey)).apply()
    }

    /** Returns the 32-byte AES session key for this address, or null if no session. */
    fun getSessionKey(ctx: Context, address: String): ByteArray? =
        prefs(ctx).getString(PFX_SESSION + norm(address), null)?.let { b64dec(it) }

    fun hasSentKey(ctx: Context, address: String): Boolean =
        prefs(ctx).getBoolean(PFX_SENT + norm(address), false)

    fun markKeySent(ctx: Context, address: String) =
        prefs(ctx).edit().putBoolean(PFX_SENT + norm(address), true).apply()

    fun clearSentKey(ctx: Context, address: String) =
        prefs(ctx).edit().remove(PFX_SENT + norm(address)).apply()

    // ── Wire format ───────────────────────────────────────────────────────────

    fun buildKeyExchange(pubKeyBytes: ByteArray) = "[NXKEY1:${b64enc(pubKeyBytes)}]"

    fun parseKeyExchange(msg: String): ByteArray? {
        if (!isKeyExchange(msg)) return null
        val start = msg.indexOf("[NXKEY1:")
        val end   = msg.indexOf("]", start + 8)
        if (start < 0 || end < 0) return null
        return try { b64dec(msg.substring(start + 8, end)) }
        catch (_: Exception) { null }
    }

    fun isKeyExchange(msg: String) = msg.contains("[NXKEY1:")
    fun isEncrypted(msg: String)   = msg.startsWith("[NXMSG1:") && msg.endsWith("]")

    // ── Encrypt / Decrypt ─────────────────────────────────────────────────────

    fun encrypt(plaintext: String, key: ByteArray): String {
        val iv     = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "[NXMSG1:${b64enc(iv)}:${b64enc(ct)}]"
    }

    fun decrypt(msg: String, key: ByteArray): String? {
        if (!isEncrypted(msg)) return null
        val inner = msg.removePrefix("[NXMSG1:").removeSuffix("]")
        val colon = inner.indexOf(':')
        if (colon < 0) return null
        return try {
            val iv     = b64dec(inner.substring(0, colon))
            val ct     = b64dec(inner.substring(colon + 1))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun ecdh(priv: PrivateKey, pub: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv); ka.doPhase(pub, true)
        return MessageDigest.getInstance("SHA-256").digest(ka.generateSecret())
    }

    private fun norm(address: String) = MmsPduBuilder.normalizeToE164(address)

    private fun b64enc(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun b64dec(s: String)   = Base64.decode(s, Base64.NO_WRAP)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
