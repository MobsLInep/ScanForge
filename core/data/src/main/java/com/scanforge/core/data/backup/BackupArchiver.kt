package com.scanforge.core.data.backup

import com.scanforge.core.domain.backup.BackupManifest
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A named blob to place into / read out of a backup archive. */
class BackupEntry(val name: String, val bytes: ByteArray)

/** Raised when the archive is not a recognisable ScanForge backup (no manifest). */
class InvalidBackupException(message: String) : Exception(message)

/** Raised when an encrypted archive is opened with the wrong password. */
class WrongBackupPasswordException : Exception("Incorrect backup password")

/**
 * Reads/writes the ScanForge backup container: a ZIP carrying a clear-text [BackupManifest] plus,
 * optionally, an encryption salt and AES/GCM-encrypted payload entries (DB + page images).
 *
 * Deliberately I/O-only over [InputStream]/[OutputStream] and `ByteArray` entries — **no Android** —
 * so the full create→restore round-trip is unit-testable on the JVM. The Android layer just supplies
 * the streams (SAF) and file bytes.
 */
class BackupArchiver(private val json: Json = Json { ignoreUnknownKeys = true }) {

    /**
     * Streams [entries] into [output] as a ZIP. [manifest] is written first in clear text; when
     * [password] is non-blank a salt entry follows and every payload entry is AES/GCM encrypted.
     */
    fun write(
        output: OutputStream,
        manifest: BackupManifest,
        entries: Sequence<BackupEntry>,
        password: String?,
    ) {
        val encrypting = !password.isNullOrBlank()
        val salt = if (encrypting) BackupCrypto.randomSalt() else null
        val key = if (encrypting) BackupCrypto.deriveKey(password!!, salt!!) else null

        ZipOutputStream(output).use { zip ->
            zip.putEntry(BackupManifest.ENTRY_NAME, json.encodeToString(BackupManifest.serializer(), manifest))
            if (encrypting) zip.putEntry(SALT_ENTRY, salt!!)
            entries.forEach { entry ->
                val payload = if (key != null) BackupCrypto.encrypt(key, entry.bytes) else entry.bytes
                zip.putEntry(PAYLOAD_PREFIX + entry.name, payload)
            }
        }
        output.flush()
    }

    /** Reads only the manifest, without decrypting payloads (no password required). */
    fun readManifest(input: InputStream): BackupManifest {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == BackupManifest.ENTRY_NAME) {
                    return json.decodeFromString(BackupManifest.serializer(), zip.readBytes().decodeToString())
                }
                entry = zip.nextEntry
            }
        }
        throw InvalidBackupException("No manifest in archive")
    }

    /**
     * Streams the archive, invoking [onEntry] for each decrypted payload entry (name without the
     * internal prefix). Returns the manifest. Throws [WrongBackupPasswordException] on bad password
     * and [InvalidBackupException] when the manifest is missing.
     */
    fun read(
        input: InputStream,
        password: String?,
        onEntry: (name: String, bytes: ByteArray) -> Unit,
    ): BackupManifest {
        var manifest: BackupManifest? = null
        var salt: ByteArray? = null
        var key: javax.crypto.SecretKey? = null

        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val bytes = zip.readBytes()
                when {
                    name == BackupManifest.ENTRY_NAME ->
                        manifest = json.decodeFromString(BackupManifest.serializer(), bytes.decodeToString())

                    name == SALT_ENTRY -> {
                        salt = bytes
                        if (!password.isNullOrBlank()) key = BackupCrypto.deriveKey(password, bytes)
                    }

                    name.startsWith(PAYLOAD_PREFIX) -> {
                        val payloadName = name.removePrefix(PAYLOAD_PREFIX)
                        val plain = if (salt != null) {
                            val k = key ?: throw WrongBackupPasswordException()
                            try {
                                BackupCrypto.decrypt(k, bytes)
                            } catch (e: javax.crypto.AEADBadTagException) {
                                throw WrongBackupPasswordException()
                            }
                        } else {
                            bytes
                        }
                        onEntry(payloadName, plain)
                    }
                }
                entry = zip.nextEntry
            }
        }
        return manifest ?: throw InvalidBackupException("No manifest in archive")
    }

    private fun ZipOutputStream.putEntry(name: String, text: String) = putEntry(name, text.encodeToByteArray())

    private fun ZipOutputStream.putEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    companion object {
        private const val SALT_ENTRY = "salt.bin"
        private const val PAYLOAD_PREFIX = "payload/"
    }
}
