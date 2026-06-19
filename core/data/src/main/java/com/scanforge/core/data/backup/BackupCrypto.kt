package com.scanforge.core.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based authenticated encryption for backup archives. Uses PBKDF2 (HMAC-SHA256) to derive a
 * 256-bit key from the user's password + a per-archive salt, then AES/GCM for each payload blob.
 *
 * Pure `javax.crypto`, so the round-trip is unit-testable on the plain JVM without Android. A wrong
 * password fails GCM tag verification, surfacing as [javax.crypto.AEADBadTagException].
 */
object BackupCrypto {
    const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val random = SecureRandom()

    fun randomSalt(): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance(KDF)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /** Encrypts [plain], returning `IV (12 bytes) || ciphertext+tag`. */
    fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain)
        return iv + ct
    }

    /** Reverses [encrypt]; throws [javax.crypto.AEADBadTagException] on a wrong key/corruption. */
    fun decrypt(key: SecretKey, blob: ByteArray): ByteArray {
        require(blob.size > IV_BYTES) { "ciphertext too short" }
        val iv = blob.copyOfRange(0, IV_BYTES)
        val ct = blob.copyOfRange(IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }
}
