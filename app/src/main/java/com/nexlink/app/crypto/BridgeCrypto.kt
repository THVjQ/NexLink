package com.nexlink.app.crypto

// ─────────────────────────────────────────────────────────────────────────────
//  BridgeCrypto.kt  —  Computer Bridge TRANSPORT encryption
//
//  This is the bridge's own end-to-end scheme between the phone and the user's
//  browser client, relayed (as ciphertext only) through the user's self-hosted
//  server. It is COMPLETELY SEPARATE from NexLink's own NexLink↔NexLink E2E in
//  db/CryptoStore.kt and must never touch it.
//
//  Scheme (ECIES) — MUST stay byte-compatible with the server (JS) and browser
//  extension (WebCrypto). The canonical contract lives in the bridge repo's
//  PROTOCOL.md; this file implements v1:
//    Key agreement : ECDH, P-256 (secp256r1)   — available on all API 26+
//    Key derivation: HKDF-SHA256 (RFC 5869), zero salt, info "sms-bridge-v1"
//    Symmetric enc : AES-256-GCM, 12-byte IV, 16-byte tag, AAD = info
//
//  Wire envelope (JSON):
//    { "v":1, "epk":<b64 DER SPKI ephemeral pubkey>, "iv":<b64 12>, "tag":<b64 16>, "ct":<b64> }
//
//  See https://github.com/THVjQ/SMS-Brigde — PROTOCOL.md (v1). Bump the version
//  integer in lockstep across all three implementations if the scheme changes.
// ─────────────────────────────────────────────────────────────────────────────

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object BridgeCrypto {

    private const val CURVE       = "EC"
    private const val CURVE_NAME  = "secp256r1"   // P-256
    private const val KA_ALG      = "ECDH"
    private const val ENC_ALG     = "AES/GCM/NoPadding"
    private const val GCM_TAG     = 128            // bits
    private const val IV_LEN      = 12             // bytes
    private val INFO  = "sms-bridge-v1".toByteArray()
    private val SALT  = ByteArray(32)              // all-zeros, matches server

    // ── Key generation / import-export ─────────────────────────────────────────

    fun generateKeyPair(): KeyPair {
        val kg = KeyPairGenerator.getInstance(CURVE)
        kg.initialize(ECGenParameterSpec(CURVE_NAME), SecureRandom())
        return kg.generateKeyPair()
    }

    fun exportPublicKey(key: PublicKey): String =
        Base64.encodeToString(key.encoded, Base64.NO_WRAP)

    fun importPublicKey(b64: String): PublicKey {
        val der = Base64.decode(b64, Base64.NO_WRAP)
        return KeyFactory.getInstance(CURVE).generatePublic(X509EncodedKeySpec(der))
    }

    fun exportPrivateKey(key: PrivateKey): String =
        Base64.encodeToString(key.encoded, Base64.NO_WRAP)

    fun importPrivateKey(b64: String): PrivateKey {
        val der = Base64.decode(b64, Base64.NO_WRAP)
        return KeyFactory.getInstance(CURVE).generatePrivate(PKCS8EncodedKeySpec(der))
    }

    // ── Core ECIES ──────────────────────────────────────────────────────────────

    /** Encrypt [plaintext] to the owner of [recipientPublicKeyB64]. Returns the envelope JSON. */
    fun encrypt(plaintext: String, recipientPublicKeyB64: String): String {
        val recipientPub = importPublicKey(recipientPublicKeyB64)
        val ephemeral    = generateKeyPair()
        val aesKey       = deriveKey(ephemeral.private, recipientPub)

        val iv     = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ENC_ALG)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG, iv))
        cipher.updateAAD(INFO)
        val cipherWithTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val ct  = cipherWithTag.copyOf(cipherWithTag.size - 16)
        val tag = cipherWithTag.copyOfRange(cipherWithTag.size - 16, cipherWithTag.size)

        return JSONObject().apply {
            put("v", 1)
            put("epk", exportPublicKey(ephemeral.public))
            put("iv",  Base64.encodeToString(iv,  Base64.NO_WRAP))
            put("tag", Base64.encodeToString(tag, Base64.NO_WRAP))
            put("ct",  Base64.encodeToString(ct,  Base64.NO_WRAP))
        }.toString()
    }

    /** Decrypt an envelope with [recipientPrivateKeyB64]. Throws on auth failure / malformed input. */
    fun decrypt(envelopeJson: String, recipientPrivateKeyB64: String): String {
        val env = JSONObject(envelopeJson)
        require(env.getInt("v") == 1) { "Unknown envelope version: ${env.getInt("v")}" }

        val ephemeralPub  = importPublicKey(env.getString("epk"))
        val recipientPriv = importPrivateKey(recipientPrivateKeyB64)
        val aesKey        = deriveKey(recipientPriv, ephemeralPub)

        val iv  = Base64.decode(env.getString("iv"),  Base64.NO_WRAP)
        val tag = Base64.decode(env.getString("tag"), Base64.NO_WRAP)
        val ct  = Base64.decode(env.getString("ct"),  Base64.NO_WRAP)

        val cipher = Cipher.getInstance(ENC_ALG)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG, iv))
        cipher.updateAAD(INFO)
        return cipher.doFinal(ct + tag).toString(Charsets.UTF_8)
    }

    /** SHA-256 fingerprint of `phone_pubkey || client_pubkey`, first 8 bytes as hex groups (§14.5). */
    fun fingerprint(phonePubB64: String, clientPubB64: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(Base64.decode(phonePubB64, Base64.NO_WRAP))
        md.update(Base64.decode(clientPubB64, Base64.NO_WRAP))
        val digest = md.digest().copyOf(8)
        return digest.joinToString(" ") { "%02X".format(it) }
    }

    // ── ECDH + HKDF ──────────────────────────────────────────────────────────────

    private fun deriveKey(priv: PrivateKey, pub: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance(KA_ALG)
        ka.init(priv)
        ka.doPhase(pub, true)
        return hkdf(ka.generateSecret(), SALT, INFO, 32)
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.update(t); mac.update(info); mac.update(counter.toByte())
            t = mac.doFinal()
            val copy = minOf(t.size, length - offset)
            t.copyInto(result, offset, 0, copy)
            offset += copy; counter++
        }
        return result
    }
}
