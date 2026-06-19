package com.scanforge.core.data.backup

import com.scanforge.core.domain.backup.BackupManifest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupRoundTripTest {

    private val archiver = BackupArchiver()

    private fun manifest(encrypted: Boolean) = BackupManifest(
        appVersion = "0.1.0",
        databaseVersion = 4,
        createdAtEpochMillis = 1_718_000_000_000L,
        documentCount = 2,
        imageCount = 2,
        encrypted = encrypted,
    )

    private val entries = listOf(
        BackupEntry("db/scanforge.db", byteArrayOf(1, 2, 3, 4, 5)),
        BackupEntry("pages/a.jpg", "the quick brown fox".encodeToByteArray()),
        BackupEntry("thumbnails/a.jpg", ByteArray(64) { it.toByte() }),
    )

    @Test
    fun `unencrypted archive round-trips every entry`() {
        val out = ByteArrayOutputStream()
        archiver.write(out, manifest(encrypted = false), entries.asSequence(), password = null)

        val recovered = LinkedHashMap<String, ByteArray>()
        val manifest = archiver.read(ByteArrayInputStream(out.toByteArray()), password = null) { name, bytes ->
            recovered[name] = bytes
        }

        assertEquals(2, manifest.documentCount)
        assertEquals(entries.size, recovered.size)
        entries.forEach { assertArrayEquals(it.bytes, recovered[it.name], "entry ${it.name}") }
    }

    @Test
    fun `encrypted archive round-trips with the correct password`() {
        val out = ByteArrayOutputStream()
        archiver.write(out, manifest(encrypted = true), entries.asSequence(), password = "correct horse")

        val recovered = LinkedHashMap<String, ByteArray>()
        archiver.read(ByteArrayInputStream(out.toByteArray()), password = "correct horse") { name, bytes ->
            recovered[name] = bytes
        }

        entries.forEach { assertArrayEquals(it.bytes, recovered[it.name], "entry ${it.name}") }
    }

    @Test
    fun `encrypted payload is not stored in clear text`() {
        val out = ByteArrayOutputStream()
        archiver.write(out, manifest(encrypted = true), entries.asSequence(), password = "pw")

        // The distinctive plaintext must not appear verbatim anywhere in the archive bytes.
        val haystack = out.toByteArray()
        val needle = "the quick brown fox".encodeToByteArray()
        assertTrue(haystack.toList().indexOfSublist(needle.toList()) == -1, "plaintext leaked into archive")
    }

    @Test
    fun `wrong password is rejected`() {
        val out = ByteArrayOutputStream()
        archiver.write(out, manifest(encrypted = true), entries.asSequence(), password = "right")

        assertThrows(WrongBackupPasswordException::class.java) {
            archiver.read(ByteArrayInputStream(out.toByteArray()), password = "wrong") { _, _ -> }
        }
    }

    @Test
    fun `manifest can be read without a password`() {
        val out = ByteArrayOutputStream()
        archiver.write(out, manifest(encrypted = true), entries.asSequence(), password = "secret")

        val read = archiver.readManifest(ByteArrayInputStream(out.toByteArray()))

        assertEquals(true, read.encrypted)
        assertEquals(2, read.documentCount)
    }

    @Test
    fun `missing manifest throws InvalidBackupException`() {
        // A zip with no manifest entry.
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("random.txt"))
            zip.write("nope".encodeToByteArray())
            zip.closeEntry()
        }

        assertThrows(InvalidBackupException::class.java) {
            archiver.readManifest(ByteArrayInputStream(out.toByteArray()))
        }
    }

    private fun <T> List<T>.indexOfSublist(sub: List<T>): Int {
        if (sub.isEmpty()) return 0
        outer@ for (i in 0..(size - sub.size)) {
            for (j in sub.indices) if (this[i + j] != sub[j]) continue@outer
            return i
        }
        return -1
    }
}
