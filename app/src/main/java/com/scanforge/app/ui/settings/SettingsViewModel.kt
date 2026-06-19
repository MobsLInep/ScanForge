package com.scanforge.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanforge.core.domain.backup.BackupManager
import com.scanforge.core.domain.backup.BackupManifest
import com.scanforge.core.domain.backup.BackupResult
import com.scanforge.core.domain.backup.RestoreResult
import com.scanforge.core.domain.model.AccentColor
import com.scanforge.core.domain.model.ExportFormat
import com.scanforge.core.domain.model.ExportQuality
import com.scanforge.core.domain.imaging.EnhancementFilter
import com.scanforge.core.domain.model.ScanSettings
import com.scanforge.core.domain.repository.SettingsRepository
import com.scanforge.core.domain.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A restore awaiting user confirmation (manifest already read via `peek`). */
data class PendingRestore(
    val uri: String,
    val manifest: BackupManifest,
)

enum class SettingsBusy { None, BackingUp, Restoring }

data class SettingsUiState(
    val settings: ScanSettings = ScanSettings(),
    val storage: StorageUsage = StorageUsage(),
    val busy: SettingsBusy = SettingsBusy.None,
    val pendingRestore: PendingRestore? = null,
    /** Set once a restore has staged successfully — the UI prompts the user to relaunch. */
    val restoreStaged: Boolean = false,
    /** One-shot user message (snackbar). Cleared via [consumeMessage]. */
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
    private val syncScheduler: SyncScheduler,
    private val storageStats: StorageStats,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { s ->
                _uiState.update { it.copy(settings = s) }
            }
        }
        refreshStorage()
    }

    // ── Preferences ────────────────────────────────────────────────────────────────────────────
    private fun edit(transform: (ScanSettings) -> ScanSettings) {
        viewModelScope.launch { settingsRepository.updateSettings(transform) }
    }

    fun setDarkTheme(value: Boolean?) = edit { it.copy(darkTheme = value) }
    fun setAccent(accent: AccentColor) = edit { it.copy(accent = accent) }
    fun setScanFilter(filter: EnhancementFilter) = edit { it.copy(defaultScanFilter = filter) }
    fun setExportFormat(format: ExportFormat) = edit { it.copy(defaultExportFormat = format) }
    fun setExportQuality(quality: ExportQuality) = edit { it.copy(defaultExportQuality = quality) }
    fun setOcrLanguages(tags: Set<String>) =
        edit { it.copy(ocrLanguages = tags.toList().ifEmpty { listOf("Latin") }) }

    fun setAutoOcr(enabled: Boolean) = edit { it.copy(autoOcr = enabled) }
    fun setGridOverlay(enabled: Boolean) = edit { it.copy(gridOverlayDefault = enabled) }
    fun setSaveOriginal(enabled: Boolean) = edit { it.copy(saveOriginal = enabled) }

    fun setRetention(days: Int) =
        edit { it.copy(trashRetentionDays = days.coerceIn(MIN_DAYS, MAX_DAYS)) }

    fun setSyncEnabled(enabled: Boolean) {
        edit { it.copy(syncEnabled = enabled) }
        if (enabled) syncScheduler.enable() else syncScheduler.disable()
    }

    fun setAnalyticsEnabled(enabled: Boolean) = edit { it.copy(analyticsEnabled = enabled) }
    fun setCrashReportingEnabled(enabled: Boolean) = edit { it.copy(crashReportingEnabled = enabled) }

    // ── Storage ────────────────────────────────────────────────────────────────────────────────
    fun refreshStorage() {
        viewModelScope.launch {
            _uiState.update { it.copy(storage = storageStats.measure()) }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val freed = storageStats.clearCache()
            _uiState.update {
                it.copy(storage = storageStats.measure(), message = "freed:$freed")
            }
        }
    }

    // ── Backup / restore ───────────────────────────────────────────────────────────────────────
    fun backup(destinationUri: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = SettingsBusy.BackingUp) }
            val result = backupManager.createBackup(destinationUri, password)
            _uiState.update {
                it.copy(
                    busy = SettingsBusy.None,
                    message = when (result) {
                        is BackupResult.Success -> "backup_ok:${result.bytesWritten}"
                        is BackupResult.Failure -> "backup_fail:${result.reason}"
                    },
                )
            }
        }
    }

    /** Reads the chosen archive's manifest so the UI can confirm before overwriting. */
    fun inspectRestore(sourceUri: String) {
        viewModelScope.launch {
            when (val r = backupManager.peek(sourceUri, password = "")) {
                is RestoreResult.Success ->
                    _uiState.update { it.copy(pendingRestore = PendingRestore(sourceUri, r.manifest)) }
                is RestoreResult.Incompatible ->
                    _uiState.update { it.copy(message = "restore_incompatible") }
                is RestoreResult.InvalidArchive ->
                    _uiState.update { it.copy(message = "restore_invalid") }
                is RestoreResult.Failure ->
                    _uiState.update { it.copy(message = "restore_fail:${r.reason}") }
                RestoreResult.WrongPassword -> Unit
            }
        }
    }

    fun cancelRestorePrompt() = _uiState.update { it.copy(pendingRestore = null) }

    fun confirmRestore(password: String) {
        val pending = _uiState.value.pendingRestore ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = SettingsBusy.Restoring) }
            val result = backupManager.restoreBackup(pending.uri, password)
            _uiState.update {
                when (result) {
                    is RestoreResult.Success -> it.copy(
                        busy = SettingsBusy.None,
                        pendingRestore = null,
                        restoreStaged = true,
                        message = "restore_ok",
                    )
                    RestoreResult.WrongPassword -> it.copy(busy = SettingsBusy.None, message = "restore_wrong")
                    is RestoreResult.InvalidArchive -> it.copy(busy = SettingsBusy.None, pendingRestore = null, message = "restore_invalid")
                    is RestoreResult.Incompatible -> it.copy(busy = SettingsBusy.None, pendingRestore = null, message = "restore_incompatible")
                    is RestoreResult.Failure -> it.copy(busy = SettingsBusy.None, message = "restore_fail:${result.reason}")
                }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        const val MIN_DAYS = 1
        const val MAX_DAYS = 365
    }
}
