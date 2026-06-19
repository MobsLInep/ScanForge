package com.scanforge.core.export

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.scanforge.core.domain.export.ExportManager
import com.scanforge.core.domain.export.ExportOptions
import com.scanforge.core.domain.export.ExportProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules exports via WorkManager and projects the job's [WorkInfo] back into the domain's
 * [ExportProgress]. Each document has a single unique export job ([ExistingWorkPolicy.REPLACE]) so
 * re-exporting supersedes any in-flight run.
 */
@Singleton
class WorkManagerExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderer: ExportRenderer,
) : ExportManager {

    private val workManager get() = WorkManager.getInstance(context)

    override suspend fun estimateSize(documentId: Long, options: ExportOptions): Long =
        renderer.estimateSize(documentId, options)

    override fun enqueue(documentId: Long, options: ExportOptions): String {
        val name = workName(documentId)
        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(
                workDataOf(
                    ExportWorker.KEY_DOCUMENT_ID to documentId,
                    ExportWorker.KEY_OPTIONS to Json.encodeToString(options),
                ),
            )
            .build()
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        return name
    }

    override fun observe(workKey: String): Flow<ExportProgress> =
        workManager.getWorkInfosForUniqueWorkFlow(workKey).map { infos ->
            infos.lastOrNull()?.toProgress() ?: ExportProgress.Idle
        }

    private fun WorkInfo.toProgress(): ExportProgress = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ExportProgress.Running(0, 0)
        WorkInfo.State.RUNNING -> ExportProgress.Running(
            progress.getInt(ExportWorker.KEY_DONE, 0),
            progress.getInt(ExportWorker.KEY_TOTAL, 0),
        )
        WorkInfo.State.SUCCEEDED -> ExportProgress.Completed(
            outputPath = outputData.getString(ExportWorker.KEY_OUTPUT_PATH).orEmpty(),
            mimeType = outputData.getString(ExportWorker.KEY_OUTPUT_MIME) ?: "application/pdf",
            sizeBytes = outputData.getLong(ExportWorker.KEY_OUTPUT_SIZE, 0L),
        )
        WorkInfo.State.FAILED -> ExportProgress.Failed(outputData.getString(ExportWorker.KEY_ERROR))
        WorkInfo.State.CANCELLED -> ExportProgress.Failed("Export cancelled")
    }

    private fun workName(documentId: Long) = "export_doc_$documentId"
}
