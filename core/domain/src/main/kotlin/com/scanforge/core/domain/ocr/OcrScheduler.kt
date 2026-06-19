package com.scanforge.core.domain.ocr

/**
 * Enqueues background OCR work that survives app backgrounding/process death. The presentation layer
 * depends on this interface so it never references WorkManager directly; the implementation lives in
 * `:core:ocr`. Live progress is *not* exposed here — the UI derives it from the pages' reactive
 * [com.scanforge.core.domain.model.OcrStatus] in the repository (single source of truth).
 *
 * Callers are expected to mark the affected pages
 * [com.scanforge.core.domain.model.OcrStatus.Queued] *before* enqueuing, so the UI reflects PENDING
 * immediately even before the worker starts.
 */
interface OcrScheduler {
    /** Recognises every queued page of a document as one unit of work (batch). */
    fun enqueueDocument(documentId: Long)

    /** Recognises a single page (e.g. after a re-run on one page). */
    fun enqueuePage(pageId: Long)

    /** Cancels any in-flight or pending OCR for a document. */
    fun cancelDocument(documentId: Long)
}
