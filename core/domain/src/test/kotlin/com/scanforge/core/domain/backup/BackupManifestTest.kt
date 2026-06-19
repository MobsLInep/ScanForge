package com.scanforge.core.domain.backup

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupManifestTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `manifest survives a json round-trip`() {
        val original = BackupManifest(
            appVersion = "0.1.0",
            databaseVersion = 4,
            createdAtEpochMillis = 1_718_000_000_000L,
            documentCount = 12,
            imageCount = 30,
            encrypted = true,
        )

        val encoded = json.encodeToString(BackupManifest.serializer(), original)
        val decoded = json.decodeFromString(BackupManifest.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `format version defaults to the current schema`() {
        val manifest = BackupManifest(
            appVersion = "x",
            databaseVersion = 4,
            createdAtEpochMillis = 0,
            documentCount = 0,
            imageCount = 0,
            encrypted = false,
        )
        assertEquals(BackupManifest.CURRENT_FORMAT_VERSION, manifest.formatVersion)
    }

    @Test
    fun `unknown fields are ignored on decode for forward compatibility`() {
        val withExtra = """
            {"formatVersion":1,"appVersion":"9.9","databaseVersion":4,
             "createdAtEpochMillis":1,"documentCount":1,"imageCount":1,
             "encrypted":false,"futureField":"ignored"}
        """.trimIndent()

        val decoded = json.decodeFromString(BackupManifest.serializer(), withExtra)

        assertEquals("9.9", decoded.appVersion)
    }
}
