package com.scanforge.app.ui.ocr

import com.scanforge.core.domain.model.OcrStatus
import com.scanforge.core.domain.ocr.OcrDocument
import com.scanforge.core.domain.ocr.OcrLanguageMode

/** Whether the OCR results screen shows the page image (with text overlay) or the recognised text. */
enum class OcrViewMode { Image, Text }

/**
 * UI state for the OCR results screen. [text] is the editable, possibly-corrected text (mirrors the
 * page's `extracted_text`); [ocr] holds the structured geometry used for the image overlay and the
 * confidence heatmap. While [editing], [draft] is the in-progress correction.
 */
data class OcrResultUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val imagePath: String? = null,
    val status: OcrStatus = OcrStatus.NotStarted,
    val text: String = "",
    val ocr: OcrDocument? = null,
    val viewMode: OcrViewMode = OcrViewMode.Text,
    val heatmapEnabled: Boolean = false,
    val editing: Boolean = false,
    val draft: String = "",
    val languageMode: OcrLanguageMode = OcrLanguageMode.Auto,
) {
    val hasText: Boolean get() = text.isNotBlank()
    val isProcessing: Boolean get() = status == OcrStatus.Queued || status == OcrStatus.InProgress
    val isDone: Boolean get() = status == OcrStatus.Completed
    val isFailed: Boolean get() = status == OcrStatus.Failed
    /** Mean confidence as a percentage, or `null` when the engine reported none. */
    val confidencePercent: Int? get() = ocr?.confidence?.let { (it * 100).toInt() }
}
