package com.scanforge.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.scanforge.core.domain.imaging.EnhancementFilter
import com.scanforge.core.domain.model.AccentColor
import com.scanforge.core.domain.model.ColorMode
import com.scanforge.core.domain.model.ExportFormat
import com.scanforge.core.domain.model.ExportQuality
import com.scanforge.core.domain.model.ScanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Round-trips [ScanSettings] through a real Preferences DataStore (backed by a temp file). Runs on the
 * plain JVM — DataStore is created with an explicit `produceFile`, so no Android Context is needed.
 */
class ScanForgePreferencesDataSourceTest {

    @TempDir
    lateinit var tempDir: File

    // DataStore needs its own long-lived scope (a collector that never completes); keeping it off the
    // runTest scope avoids UncompletedCoroutinesError. Real IO dispatcher so writes actually flush.
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var source: ScanForgePreferencesDataSource

    @BeforeEach
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { File(tempDir, "settings_${System.nanoTime()}.preferences_pb") },
        )
        source = ScanForgePreferencesDataSource(dataStore)
    }

    @AfterEach
    fun tearDown() {
        storeScope.cancel()
    }

    @Test
    fun `defaults are returned when nothing has been written`() = runTest {
        val settings = source.settings.first()
        assertEquals(ScanSettings(), settings)
        assertFalse(source.onboardingComplete.first())
    }

    @Test
    fun `every settings field survives a write-read round trip`() = runTest {
        val target = ScanSettings(
            colorMode = ColorMode.BlackAndWhite,
            defaultExportFormat = ExportFormat.PlainText,
            defaultExportQuality = ExportQuality.High,
            defaultScanFilter = EnhancementFilter.MagicColor,
            autoOcr = false,
            ocrLanguages = listOf("Devanagari", "Latin"),
            darkTheme = null, // follow system
            accent = AccentColor.Teal,
            gridOverlayDefault = true,
            saveOriginal = false,
            trashRetentionDays = 90,
            syncEnabled = true,
        )

        source.update { target }

        val read = source.settings.first()
        assertEquals(target, read)
    }

    @Test
    fun `null darkTheme persists as follow-system`() = runTest {
        source.update { it.copy(darkTheme = null) }
        assertNull(source.settings.first().darkTheme)

        source.update { it.copy(darkTheme = false) }
        assertEquals(false, source.settings.first().darkTheme)
    }

    @Test
    fun `onboarding flag round-trips`() = runTest {
        source.setOnboardingComplete(true)
        assertTrue(source.onboardingComplete.first())
    }

    @Test
    fun `partial update keeps other values intact`() = runTest {
        source.update { it.copy(trashRetentionDays = 14) }
        source.update { it.copy(autoOcr = false) }

        val read = source.settings.first()
        assertEquals(14, read.trashRetentionDays)
        assertFalse(read.autoOcr)
        // Untouched field keeps its default.
        assertEquals(ScanSettings().accent, read.accent)
    }
}
