package com.scanforge.core.ocr

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.scanforge.core.domain.ocr.OcrScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues [OcrWorker] via WorkManager so OCR survives app death. Document and per-page work use
 * stable unique names, [ExistingWorkPolicy.REPLACE], so re-running OCR (e.g. after a language change)
 * supersedes any in-flight pass instead of stacking duplicates.
 */
@Singleton
class WorkManagerOcrScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    // OCR is CPU-bound, not network-bound; only require the battery not be critically low.
    private val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()

    override fun enqueueDocument(documentId: Long) {
        val request = OneTimeWorkRequestBuilder<OcrWorker>()
            .setInputData(workDataOf(OcrWorker.KEY_DOCUMENT_ID to documentId))
            .setConstraints(constraints)
            .addTag(documentTag(documentId))
            .build()
        workManager.enqueueUniqueWork(documentWork(documentId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun enqueuePage(pageId: Long) {
        val request = OneTimeWorkRequestBuilder<OcrWorker>()
            .setInputData(workDataOf(OcrWorker.KEY_PAGE_ID to pageId))
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(pageWork(pageId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelDocument(documentId: Long) {
        workManager.cancelUniqueWork(documentWork(documentId))
        workManager.cancelAllWorkByTag(documentTag(documentId))
    }

    private fun documentWork(id: Long) = "ocr_doc_$id"
    private fun documentTag(id: Long) = "ocr_doc_tag_$id"
    private fun pageWork(id: Long) = "ocr_page_$id"
}
