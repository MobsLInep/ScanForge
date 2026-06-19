package com.scanforge.app.ui.export

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.scanforge.app.navigation.ScanForgeRoute
import com.scanforge.core.common.storage.StorageGuard
import com.scanforge.core.domain.export.CompressionPreset
import com.scanforge.core.domain.export.ExportColorMode
import com.scanforge.core.domain.export.ExportKind
import com.scanforge.core.domain.export.ExportManager
import com.scanforge.core.domain.export.ExportOptions
import com.scanforge.core.domain.export.ExportProgress
import com.scanforge.core.domain.export.PdfPageSize
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the export sheet: holds the in-progress [ExportOptions], a live size estimate that refreshes
 * as options change, and the [ExportProgress] of the running job. The actual rendering happens in a
 * WorkManager job behind [ExportManager]; this VM only collects its progress.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportManager: ExportManager,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val documentId: Long =
        savedStateHandle.get<Long>("documentId") ?: savedStateHandle.toRoute<ScanForgeRoute.DocumentDetail>().documentId

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    private var estimateJob: Job? = null

    init {
        refreshEstimate()
    }

    fun setKind(kind: ExportKind) = mutate { it.copy(kind = kind) }
    fun setPageSize(size: PdfPageSize) = mutate { it.copy(pageSize = size) }
    fun setColor(mode: ExportColorMode) = mutate { it.copy(colorMode = mode) }
    fun setCompression(preset: CompressionPreset) = mutate { it.copy(compression = preset) }
    fun setPasswordEnabled(enabled: Boolean) = mutate { it.copy(passwordEnabled = enabled) }
    fun setPassword(password: String) = _state.update { it.copy(password = password) }

    private fun mutate(transform: (ExportUiState) -> ExportUiState) {
        _state.update(transform)
        refreshEstimate()
    }

    private fun refreshEstimate() {
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch {
            _state.update { it.copy(estimating = true) }
            val bytes = runCatching { exportManager.estimateSize(documentId, _state.value.toOptions()) }
                .getOrDefault(0L)
            _state.update { it.copy(estimateBytes = bytes, estimating = false) }
        }
    }

    fun startExport() {
        // Fail closed on low storage so the export doesn't die mid-write with an opaque ENOSPC.
        val estimate = _state.value.estimateBytes ?: 0L
        if (!StorageGuard.hasHeadroom(freeBytes = freeBytesForExports(), requiredBytes = estimate)) {
            _state.update { it.copy(progress = ExportProgress.Failed(LOW_STORAGE_REASON)) }
            return
        }
        val workKey = exportManager.enqueue(documentId, _state.value.toOptions())
        viewModelScope.launch {
            exportManager.observe(workKey).collect { progress ->
                _state.update { current ->
                    current.copy(
                        progress = progress,
                        output = (progress as? ExportProgress.Completed) ?: current.output,
                    )
                }
            }
        }
    }

    /** Clears a finished/failed run so the sheet returns to the options form. */
    fun reset() = _state.update {
        it.copy(progress = ExportProgress.Idle, output = null)
    }

    /** Free bytes on the volume backing the cache dir, where exports are written. -1 if unknown. */
    private fun freeBytesForExports(): Long = runCatching {
        StatFs(context.cacheDir.path).availableBytes
    }.getOrDefault(-1L)

    private companion object {
        const val LOW_STORAGE_REASON = "low_storage"
    }
}
