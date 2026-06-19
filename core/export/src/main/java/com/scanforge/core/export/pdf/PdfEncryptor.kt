package com.scanforge.core.export.pdf

import java.security.MessageDigest

/**
 * PDF standard security handler, revision 3 with a 128-bit RC4 key (V2/R3) — the most broadly
 * compatible password scheme implementable without a crypto-heavy dependency. Computes the `/O` and
 * `/U` entries and the file encryption key per the PDF spec's algorithms 3.2–3.5, and derives a
 * per-object key (algorithm 3.1) used to encrypt each string and stream.
 *
 * Implemented over [MessageDigest] (MD5) plus a tiny RC4; pure JVM so it is unit-testable.
 */
internal class PdfEncryptor private constructor(
    private val key: ByteArray,
    private val o: ByteArray,
    private val u: ByteArray,
    private val permissions: Int,
) {

    fun encryptDictionary(): String =
        "<< /Filter /Standard /V 2 /R 3 /Length 128 /P $permissions /O <${o.toHex()}> /U <${u.toHex()}> >>"

    /** RC4-encrypts (or, applied twice, decrypts) [data] with the per-object key for [objNum]/[gen]. */
    fun encrypt(objNum: Int, gen: Int, data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        md.update(key)
        md.update(byteArrayOf(objNum.toByte(), (objNum shr 8).toByte(), (objNum shr 16).toByte()))
        md.update(byteArrayOf(gen.toByte(), (gen shr 8).toByte()))
        val objectKey = md.digest().copyOf(minOf(key.size + 5, 16))
        return rc4(objectKey, data)
    }

    companion object {
        private const val KEY_LEN = 16

        // The 32-byte password padding string from the PDF specification.
        private val PAD = intArrayOf(
            0x28, 0xBF, 0x4E, 0x5E, 0x4E, 0x75, 0x8A, 0x41, 0x64, 0x00, 0x4E, 0x56, 0xFF, 0xFA, 0x01, 0x08,
            0x2E, 0x2E, 0x00, 0xB6, 0xD0, 0x68, 0x3E, 0x80, 0x2F, 0x0C, 0xA9, 0xFE, 0x64, 0x53, 0x69, 0x7A,
        ).map { it.toByte() }.toByteArray()

        /**
         * Builds the handler from [security] and the document's first `/ID` element [fileId]. The
         * default [permissions] (`-4`) grants all actions once the open password is supplied.
         */
        fun create(security: PdfSecurity, fileId: ByteArray, permissions: Int = -4): PdfEncryptor {
            val userPad = pad(security.userPassword)
            val ownerPad = pad(security.ownerPassword.ifEmpty { security.userPassword })
            val o = computeOwnerEntry(ownerPad, userPad)
            val key = computeFileKey(userPad, o, permissions, fileId)
            val u = computeUserEntry(key, fileId)
            return PdfEncryptor(key, o, u, permissions)
        }

        private fun pad(password: String): ByteArray {
            val raw = password.toByteArray(Charsets.ISO_8859_1)
            val out = ByteArray(32)
            val n = minOf(raw.size, 32)
            System.arraycopy(raw, 0, out, 0, n)
            if (n < 32) System.arraycopy(PAD, 0, out, n, 32 - n)
            return out
        }

        private fun computeOwnerEntry(ownerPad: ByteArray, userPad: ByteArray): ByteArray {
            var hash = md5(ownerPad)
            repeat(50) { hash = md5(hash.copyOf(KEY_LEN)) }
            val rc4Key = hash.copyOf(KEY_LEN)
            var data = userPad.copyOf()
            data = rc4(rc4Key, data)
            for (i in 1..19) data = rc4(xorKey(rc4Key, i), data)
            return data
        }

        private fun computeFileKey(userPad: ByteArray, o: ByteArray, p: Int, fileId: ByteArray): ByteArray {
            val md = MessageDigest.getInstance("MD5")
            md.update(userPad)
            md.update(o)
            md.update(byteArrayOf(p.toByte(), (p shr 8).toByte(), (p shr 16).toByte(), (p shr 24).toByte()))
            md.update(fileId)
            var hash = md.digest()
            repeat(50) { hash = md5(hash.copyOf(KEY_LEN)) }
            return hash.copyOf(KEY_LEN)
        }

        private fun computeUserEntry(key: ByteArray, fileId: ByteArray): ByteArray {
            val md = MessageDigest.getInstance("MD5")
            md.update(PAD)
            md.update(fileId)
            var data = md.digest()
            data = rc4(key, data)
            for (i in 1..19) data = rc4(xorKey(key, i), data)
            return data.copyOf(32)
        }

        private fun xorKey(key: ByteArray, x: Int): ByteArray =
            ByteArray(key.size) { (key[it].toInt() xor x).toByte() }

        private fun md5(data: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(data)

        private fun rc4(key: ByteArray, data: ByteArray): ByteArray {
            val s = IntArray(256) { it }
            var j = 0
            for (i in 0..255) {
                j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
                val t = s[i]; s[i] = s[j]; s[j] = t
            }
            val out = ByteArray(data.size)
            var a = 0; var b = 0
            for (k in data.indices) {
                a = (a + 1) and 0xFF
                b = (b + s[a]) and 0xFF
                val t = s[a]; s[a] = s[b]; s[b] = t
                val ks = s[(s[a] + s[b]) and 0xFF]
                out[k] = (data[k].toInt() xor ks).toByte()
            }
            return out
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
