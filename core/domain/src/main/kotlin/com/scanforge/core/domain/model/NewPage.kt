package com.scanforge.core.domain.model

import com.scanforge.core.domain.imaging.PageProcessing

/**
 * A page to be persisted, as produced by the capture / import pipeline before it has a database id.
 * [ocrStatus] defaults to [OcrStatus.Queued] — a freshly captured page is waiting for OCR, which
 * the OCR phase will pick up. When the capture flow has already enhanced the page, [processedImagePath]
 * and [processing] carry the result and its re-editable recipe.
 */
data class NewPage(
    val originalImagePath: String,
    val thumbnailPath: String? = null,
    val processedImagePath: String? = null,
    val processing: PageProcessing? = null,
    val ocrStatus: OcrStatus = OcrStatus.Queued,
)
