package com.scanforge.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.scanforge.core.domain.imaging.EnhancementFilter
import com.scanforge.core.domain.model.AccentColor
import com.scanforge.core.domain.model.ColorMode
import com.scanforge.core.domain.model.ExportFormat
import com.scanforge.core.domain.model.ExportQuality
import com.scanforge.core.domain.model.ScanSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads/writes [ScanSettings] over a Preferences DataStore. Unknown/missing values fall back to the
 * [ScanSettings] defaults, so the app always has a valid configuration.
 */
@Singleton
class ScanForgePreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<ScanSettings> = dataStore.data.map { prefs -> prefs.toScanSettings() }

    /** First-launch onboarding completion, stored separately from scan preferences. */
    val onboardingComplete: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[Keys.ONBOARDING_COMPLETE] ?: false }

    suspend fun update(transform: (ScanSettings) -> ScanSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toScanSettings())
            prefs[Keys.COLOR_MODE] = updated.colorMode.name
            prefs[Keys.EXPORT_FORMAT] = updated.defaultExportFormat.name
            prefs[Keys.EXPORT_QUALITY] = updated.defaultExportQuality.name
            prefs[Keys.SCAN_FILTER] = updated.defaultScanFilter.name
            prefs[Keys.AUTO_OCR] = updated.autoOcr
            prefs[Keys.OCR_LANGUAGES] = updated.ocrLanguages.joinToString(LANGUAGE_SEPARATOR)
            prefs[Keys.DARK_THEME] = updated.darkTheme.toStoredValue()
            prefs[Keys.ACCENT] = updated.accent.name
            prefs[Keys.GRID_OVERLAY] = updated.gridOverlayDefault
            prefs[Keys.SAVE_ORIGINAL] = updated.saveOriginal
            prefs[Keys.TRASH_RETENTION_DAYS] = updated.trashRetentionDays
            prefs[Keys.SYNC_ENABLED] = updated.syncEnabled
            prefs[Keys.ANALYTICS_ENABLED] = updated.analyticsEnabled
            prefs[Keys.CRASH_REPORTING_ENABLED] = updated.crashReportingEnabled
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETE] = complete }
    }

    private fun Preferences.toScanSettings(): ScanSettings {
        val defaults = ScanSettings()
        return ScanSettings(
            colorMode = this[Keys.COLOR_MODE]?.let { runCatching { ColorMode.valueOf(it) }.getOrNull() }
                ?: defaults.colorMode,
            defaultExportFormat = this[Keys.EXPORT_FORMAT]?.let { runCatching { ExportFormat.valueOf(it) }.getOrNull() }
                ?: defaults.defaultExportFormat,
            defaultExportQuality = this[Keys.EXPORT_QUALITY]?.let { runCatching { ExportQuality.valueOf(it) }.getOrNull() }
                ?: defaults.defaultExportQuality,
            defaultScanFilter = this[Keys.SCAN_FILTER]?.let { runCatching { EnhancementFilter.valueOf(it) }.getOrNull() }
                ?: defaults.defaultScanFilter,
            autoOcr = this[Keys.AUTO_OCR] ?: defaults.autoOcr,
            ocrLanguages = this[Keys.OCR_LANGUAGES]
                ?.split(LANGUAGE_SEPARATOR)
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?: defaults.ocrLanguages,
            darkTheme = this[Keys.DARK_THEME].toDarkTheme(defaults.darkTheme),
            accent = this[Keys.ACCENT]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() }
                ?: defaults.accent,
            gridOverlayDefault = this[Keys.GRID_OVERLAY] ?: defaults.gridOverlayDefault,
            saveOriginal = this[Keys.SAVE_ORIGINAL] ?: defaults.saveOriginal,
            trashRetentionDays = this[Keys.TRASH_RETENTION_DAYS] ?: defaults.trashRetentionDays,
            syncEnabled = this[Keys.SYNC_ENABLED] ?: defaults.syncEnabled,
            analyticsEnabled = this[Keys.ANALYTICS_ENABLED] ?: defaults.analyticsEnabled,
            crashReportingEnabled = this[Keys.CRASH_REPORTING_ENABLED] ?: defaults.crashReportingEnabled,
        )
    }

    private object Keys {
        val COLOR_MODE = stringPreferencesKey("color_mode")
        val EXPORT_FORMAT = stringPreferencesKey("default_export_format")
        val EXPORT_QUALITY = stringPreferencesKey("default_export_quality")
        val SCAN_FILTER = stringPreferencesKey("default_scan_filter")
        val AUTO_OCR = booleanPreferencesKey("auto_ocr")
        val OCR_LANGUAGES = stringPreferencesKey("ocr_languages")
        val DARK_THEME = stringPreferencesKey("dark_theme")
        val ACCENT = stringPreferencesKey("accent")
        val GRID_OVERLAY = booleanPreferencesKey("grid_overlay_default")
        val SAVE_ORIGINAL = booleanPreferencesKey("save_original")
        val TRASH_RETENTION_DAYS = intPreferencesKey("trash_retention_days")
        val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
        val CRASH_REPORTING_ENABLED = booleanPreferencesKey("crash_reporting_enabled")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    private companion object {
        const val LANGUAGE_SEPARATOR = "\n"
        const val DARK_SYSTEM = "system"

        fun Boolean?.toStoredValue(): String = when (this) {
            true -> "true"
            false -> "false"
            null -> DARK_SYSTEM
        }

        fun String?.toDarkTheme(default: Boolean?): Boolean? = when (this) {
            "true" -> true
            "false" -> false
            DARK_SYSTEM -> null
            null -> default
            else -> default
        }
    }
}
