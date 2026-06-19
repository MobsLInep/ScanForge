package com.scanforge.core.domain.export

import kotlinx.coroutines.flow.Flow

/**
 * Live state of a single export job, surfaced to the UI. Mirrors the WorkManager lifecycle but keeps
 * the presentation layer free of any WorkManager types.
 */
sealed interface ExportProgress {
    /** No export running / not yet started for the observed key. */
    data object Idle : ExportProgress

    /** Export underway; [completed] of [total] pages rendered into the output. */
    data class Running(val completed: Int, val total: Int) : ExportProgress

    /**
     * Finished. [outputPath] is a file in app storage ready to share/open; [mimeType] is its MIME
     * (e.g. `application/pdf`); [sizeBytes] is the real produced size.
     */
    data class Completed(val outputPath: String, val mimeType: String, val sizeBytes: Long) : ExportProgress

    /** Failed; [reason] is a short human-readable cause when known. */
    data class Failed(val reason: String? = null) : ExportProgress
}

/**
 * Schedules and observes document exports. Implemented over WorkManager so large documents export
 * page-by-page off the main thread with a progress notification, surviving app death. Kept in the
 * domain so the presentation layer depends on this contract, never on WorkManager.
 */
interface ExportManager {
    /**
     * Cheaply estimates the produced byte size for [documentId] under [options] without running the
     * full export. Used to show a size figure in the export sheet before the user commits.
     */
    suspend fun estimateSize(documentId: Long, options: ExportOptions): Long

    /**
     * Enqueues an export of [documentId] with [options] and returns a stable key identifying the job,
     * to be passed to [observe]. Re-enqueuing for the same document supersedes any in-flight export.
     */
    fun enqueue(documentId: Long, options: ExportOptions): String

    /** Observes the [ExportProgress] of the job identified by [workKey]. */
    fun observe(workKey: String): Flow<ExportProgress>
}
