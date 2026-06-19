package com.scanforge.app.ui.scan

import com.scanforge.core.domain.scanning.DetectedQuad

/** A page captured/processed within the current scanning session, before it is persisted. */
data class CapturedPage(
    /** Stable id within this session, used as filmstrip key and for reorder/delete. */
    val localId: Long,
    val originalImagePath: String,
    val thumbnailPath: String,
    /** Crop quad in normalized coords (detected or manually adjusted). */
    val quad: DetectedQuad,
    /** `false` when edge detection failed and the user is in the manual-crop fallback. */
    val edgesDetected: Boolean,
)

/** Whether a capture finishes the document immediately or accumulates into a multi-page batch. */
enum class CaptureMode { Single, Batch }

/**
 * Finite states of the capture flow. The screen is a deterministic state machine over these, which
 * is exactly what [ScanViewModel] drives and what the UI/unit tests assert against.
 */
sealed interface ScanStep {
    /** Live preview; capture, toggles, and import are available. */
    data object Camera : ScanStep

    /** A frame was captured; saving image + running edge detection. */
    data object Processing : ScanStep

    /** Reviewing/adjusting the crop for the just-captured [page] before keeping it. */
    data class Review(val page: CapturedPage) : ScanStep

    /** Persisting the document + pages. */
    data object Saving : ScanStep

    /** Terminal success — [documentId] is the newly created document to navigate to. */
    data class Saved(val documentId: Long) : ScanStep

    /** Recoverable failure; [messageRes] is a string resource shown to the user. */
    data class Error(val messageRes: Int) : ScanStep
}

/** Complete capture-screen state. Immutable; the ViewModel emits new copies. */
data class ScanUiState(
    val step: ScanStep = ScanStep.Camera,
    val mode: CaptureMode = CaptureMode.Single,
    val flashEnabled: Boolean = false,
    val gridEnabled: Boolean = false,
    val capturedPages: List<CapturedPage> = emptyList(),
) {
    val isBatch: Boolean get() = mode == CaptureMode.Batch
    val pageCount: Int get() = capturedPages.size
}
