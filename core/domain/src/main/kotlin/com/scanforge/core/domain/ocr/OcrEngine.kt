package com.scanforge.core.domain.ocr

/**
 * On-device text recognition contract. The presentation and worker layers depend on this interface,
 * never on ML Kit, so the engine can be swapped (e.g. for a future cloud OCR) without touching
 * callers. Operates only on a file path + domain types — no Android/ML Kit types leak across.
 */
interface OcrEngine {
    /**
     * Recognises text in the image at [imagePath] using [mode] to pick the script model(s).
     *
     * @return the structured [OcrDocument]; [OcrDocument.EMPTY] when no text is found.
     * @throws Exception if the image cannot be decoded or recognition fails — the caller (OCR worker)
     *   translates a failure into [com.scanforge.core.domain.model.OcrStatus.Failed].
     */
    suspend fun recognize(imagePath: String, mode: OcrLanguageMode): OcrDocument
}
