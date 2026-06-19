package com.scanforge.core.ocr

import com.scanforge.core.domain.model.OcrStatus
import com.scanforge.core.domain.model.Page
import com.scanforge.core.domain.ocr.OcrEngine
import com.scanforge.core.domain.ocr.OcrLanguageMode
import com.scanforge.core.domain.repository.DocumentRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * The OCR orchestration logic, kept Android-free so it can be unit-tested on the JVM with a fake
 * engine. [OcrWorker] is a thin WorkManager shell that delegates here; the recognition pipeline,
 * status transitions, and per-page failure isolation all live in this class.
 *
 * Each page is driven through InProgress (PROCESSING) → Completed (DONE) or Failed, writing status
 * to the repository so the UI reflects progress live.
 */
class OcrRunner @Inject constructor(
    private val engine: OcrEngine,
    private val repository: DocumentRepository,
) {
    /** Recognises every queued page of a document; a failed page never aborts the batch. */
    suspend fun runDocument(documentId: Long) {
        repository.getDocumentPages(documentId)
            .filter { it.ocrStatus == OcrStatus.Queued || it.ocrStatus == OcrStatus.InProgress }
            .forEach { runPage(it) }
    }

    /** Recognises a single page by id (no-op if it no longer exists). */
    suspend fun runPage(pageId: Long) {
        repository.getPage(pageId)?.let { runPage(it) }
    }

    private suspend fun runPage(page: Page) {
        repository.setPageOcrStatus(page.id, OcrStatus.InProgress)
        try {
            val mode = OcrLanguageMode.fromTag(page.language)
            val source = page.processedImagePath ?: page.originalImagePath
            repository.savePageOcrResult(page.id, engine.recognize(source, mode))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            repository.setPageOcrStatus(page.id, OcrStatus.Failed)
        }
    }
}
