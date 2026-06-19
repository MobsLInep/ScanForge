package com.scanforge.core.ocr

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Recognises text for a page or a whole document off the main thread, surviving app backgrounding
 * and process death (WorkManager re-runs it). All real work is delegated to [OcrRunner]; this class
 * only adapts WorkManager's input/result contract.
 *
 * Triggered with either [KEY_PAGE_ID] (single page) or [KEY_DOCUMENT_ID] (batch over all queued
 * pages). The image never leaves the device.
 */
@HiltWorker
class OcrWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val runner: OcrRunner,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val pageId = inputData.getLong(KEY_PAGE_ID, -1L)
        val documentId = inputData.getLong(KEY_DOCUMENT_ID, -1L)
        return try {
            when {
                pageId > 0L -> runner.runPage(pageId)
                documentId > 0L -> runner.runDocument(documentId)
                else -> return Result.failure()
            }
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_PAGE_ID = "pageId"
        const val KEY_DOCUMENT_ID = "documentId"
    }
}
