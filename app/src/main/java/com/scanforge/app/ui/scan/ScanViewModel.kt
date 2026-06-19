package com.scanforge.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanforge.app.R
import com.scanforge.core.domain.model.NewPage
import com.scanforge.core.domain.repository.DocumentRepository
import com.scanforge.core.domain.scanning.DetectedQuad
import com.scanforge.core.domain.scanning.EdgeDetector
import com.scanforge.core.domain.scanning.PageImageStore
import com.scanforge.core.domain.scanning.PageImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Drives the capture-screen state machine ([ScanStep]): camera → processing → review → saving →
 * saved, plus batch accumulation and import. All persistence goes through the domain contracts so
 * the screen stays free of Room/OpenCV/Android-storage details.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val edgeDetector: EdgeDetector,
    private val imageStore: PageImageStore,
    private val pageImporter: PageImporter,
    private val titleProvider: ScanTitleProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val localIds = AtomicLong(0L)

    // ── Camera controls ──────────────────────────────────────────────────────────────────────
    fun toggleFlash() = _uiState.update { it.copy(flashEnabled = !it.flashEnabled) }

    fun toggleGrid() = _uiState.update { it.copy(gridEnabled = !it.gridEnabled) }

    fun toggleMode() = _uiState.update {
        it.copy(mode = if (it.mode == CaptureMode.Single) CaptureMode.Batch else CaptureMode.Single)
    }

    // ── Capture → process → review ───────────────────────────────────────────────────────────
    /** Called with the captured frame's JPEG bytes; stores it and runs edge detection. */
    fun onImageCaptured(jpegBytes: ByteArray) {
        _uiState.update { it.copy(step = ScanStep.Processing) }
        viewModelScope.launch {
            runCatching {
                val stored = imageStore.savePage(jpegBytes)
                val detected = edgeDetector.detectQuad(stored.originalImagePath)
                CapturedPage(
                    localId = localIds.getAndIncrement(),
                    originalImagePath = stored.originalImagePath,
                    thumbnailPath = stored.thumbnailPath,
                    quad = detected ?: DetectedQuad.FULL_FRAME,
                    edgesDetected = detected != null,
                )
            }.onSuccess { page ->
                _uiState.update { it.copy(step = ScanStep.Review(page)) }
            }.onFailure {
                _uiState.update { it.copy(step = ScanStep.Error(R.string.scan_error_generic)) }
            }
        }
    }

    /** Keep the reviewed page with its (possibly adjusted) [quad]. */
    fun onConfirmReview(quad: DetectedQuad) {
        val review = _uiState.value.step as? ScanStep.Review ?: return
        val page = review.page.copy(quad = quad)
        if (_uiState.value.isBatch) {
            _uiState.update { it.copy(step = ScanStep.Camera, capturedPages = it.capturedPages + page) }
        } else {
            saveDocument(listOf(page), titleProvider.scanTitle())
        }
    }

    /** Discard the just-captured page and return to the camera. */
    fun onRetake() {
        val review = _uiState.value.step as? ScanStep.Review ?: return
        viewModelScope.launch {
            imageStore.deletePageImages(review.page.originalImagePath, review.page.thumbnailPath)
        }
        _uiState.update { it.copy(step = ScanStep.Camera) }
    }

    // ── Batch management ─────────────────────────────────────────────────────────────────────
    fun onDeletePage(localId: Long) {
        val page = _uiState.value.capturedPages.firstOrNull { it.localId == localId } ?: return
        viewModelScope.launch {
            imageStore.deletePageImages(page.originalImagePath, page.thumbnailPath)
        }
        _uiState.update { it.copy(capturedPages = it.capturedPages.filterNot { p -> p.localId == localId }) }
    }

    /** Moves the page [localId] one slot toward the start ([toEarlier]) or end of the batch. */
    fun onMovePage(localId: Long, toEarlier: Boolean) {
        _uiState.update { state ->
            val pages = state.capturedPages.toMutableList()
            val index = pages.indexOfFirst { it.localId == localId }
            if (index < 0) return@update state
            val target = if (toEarlier) index - 1 else index + 1
            if (target !in pages.indices) return@update state
            pages.add(target, pages.removeAt(index))
            state.copy(capturedPages = pages)
        }
    }

    /** Finishes a batch session: creates one multi-page document from the captured pages. */
    fun onDoneBatch() {
        val pages = _uiState.value.capturedPages
        if (pages.isEmpty()) {
            _uiState.update { it.copy(step = ScanStep.Camera) }
            return
        }
        saveDocument(pages, titleProvider.scanTitle())
    }

    // ── Import ───────────────────────────────────────────────────────────────────────────────
    fun onImportImages(uris: List<String>) = importThen { pageImporter.importImages(uris) }

    fun onImportPdf(uri: String) = importThen { pageImporter.importPdf(uri) }

    fun consumeError() = _uiState.update { it.copy(step = ScanStep.Camera) }

    // ── Internals ────────────────────────────────────────────────────────────────────────────
    private fun importThen(importer: suspend () -> List<NewPage>) {
        _uiState.update { it.copy(step = ScanStep.Saving) }
        viewModelScope.launch {
            runCatching { importer() }
                .onSuccess { pages ->
                    if (pages.isEmpty()) {
                        _uiState.update { it.copy(step = ScanStep.Error(R.string.scan_error_generic)) }
                    } else {
                        val id = documentRepository.createDocumentWithPages(titleProvider.importTitle(), pages)
                        _uiState.update { it.copy(step = ScanStep.Saved(id)) }
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(step = ScanStep.Error(R.string.scan_error_generic)) }
                }
        }
    }

    private fun saveDocument(pages: List<CapturedPage>, title: String) {
        _uiState.update { it.copy(step = ScanStep.Saving) }
        viewModelScope.launch {
            runCatching {
                documentRepository.createDocumentWithPages(title, pages.map { it.toNewPage() })
            }.onSuccess { id ->
                _uiState.update { it.copy(step = ScanStep.Saved(id), capturedPages = emptyList()) }
            }.onFailure {
                _uiState.update { it.copy(step = ScanStep.Error(R.string.scan_error_generic)) }
            }
        }
    }

    private fun CapturedPage.toNewPage() = NewPage(
        originalImagePath = originalImagePath,
        thumbnailPath = thumbnailPath,
    )
}
