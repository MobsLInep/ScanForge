package com.scanforge.core.export.pdf

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Standard security handler (RC4-128, V2/R3) used for password-protected PDF export. */
class PdfEncryptorTest {

    private val fileId = ByteArray(16) { it.toByte() }

    @Test
    fun `encrypt dictionary advertises the standard handler at 128-bit`() {
        val dict = PdfEncryptor.create(PdfSecurity("open-sesame"), fileId).encryptDictionary()
        assertTrue(dict.contains("/Filter /Standard"), dict)
        assertTrue(dict.contains("/V 2"), dict)
        assertTrue(dict.contains("/R 3"), dict)
        assertTrue(dict.contains("/Length 128"), dict)
        assertTrue(dict.contains("/O <"), dict)
        assertTrue(dict.contains("/U <"), dict)
        assertTrue(dict.contains("/P "), dict)
    }

    @Test
    fun `encryption changes the bytes`() {
        val enc = PdfEncryptor.create(PdfSecurity("pw"), fileId)
        val data = "(Confidential) Tj".toByteArray(Charsets.ISO_8859_1)
        val cipher = enc.encrypt(objNum = 7, gen = 0, data = data)
        assertFalse(cipher.contentEquals(data), "ciphertext must differ from plaintext")
        assertEquals(data.size, cipher.size, "RC4 is a stream cipher: length is preserved")
    }

    @Test
    fun `RC4 is symmetric so re-applying the per-object key recovers the plaintext`() {
        val enc = PdfEncryptor.create(PdfSecurity("letmein"), fileId)
        val data = "(Reopen) Tj".toByteArray(Charsets.ISO_8859_1)
        val cipher = enc.encrypt(objNum = 12, gen = 0, data = data)
        val back = enc.encrypt(objNum = 12, gen = 0, data = cipher)
        assertArrayEquals(data, back)
    }

    @Test
    fun `different objects get different keystreams`() {
        val enc = PdfEncryptor.create(PdfSecurity("pw"), fileId)
        val data = ByteArray(32) { 0 }
        val a = enc.encrypt(1, 0, data)
        val b = enc.encrypt(2, 0, data)
        assertFalse(a.contentEquals(b), "per-object keys must differ between objects")
    }
}
