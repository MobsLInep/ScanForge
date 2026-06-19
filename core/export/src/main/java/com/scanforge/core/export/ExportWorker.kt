package com.scanforge.core.export

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.scanforge.core.domain.export.ExportOptions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Runs a document export off the main thread as a foreground job with a progress notification, so a
 * large document keeps exporting even if the user leaves the app. All rendering is delegated to
 * [ExportRenderer]; this worker only adapts WorkManager's input/progress/result contract.
 */
@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val renderer: ExportRenderer,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentId = inputData.getLong(KEY_DOCUMENT_ID, -1L)
        val optionsJson = inputData.getString(KEY_OPTIONS) ?: return Result.failure()
        if (documentId <= 0L) return Result.failure()
        val options = runCatching { Json.decodeFromString<ExportOptions>(optionsJson) }
            .getOrNull() ?: return Result.failure()

        return try {
            // Best-effort foreground promotion for a visible progress notification; if the platform
            // refuses (e.g. background start limits), fall through and still export in the background.
            runCatching { setForeground(ExportNotifications.foregroundInfo(appContext, NOTIF_TITLE, 0, 0)) }
            val artifact = renderer.render(documentId, options) { done, total ->
                setProgressAsync(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
            }
            Result.success(
                workDataOf(
                    KEY_OUTPUT_PATH to artifact.file.absolutePath,
                    KEY_OUTPUT_MIME to artifact.mimeType,
                    KEY_OUTPUT_SIZE to artifact.sizeBytes,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Result.failure(workDataOf(KEY_ERROR to (t.message ?: "Export failed")))
        }
    }

    companion object {
        const val KEY_DOCUMENT_ID = "documentId"
        const val KEY_OPTIONS = "options"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_OUTPUT_PATH = "outputPath"
        const val KEY_OUTPUT_MIME = "outputMime"
        const val KEY_OUTPUT_SIZE = "outputSize"
        const val KEY_ERROR = "error"
        private const val NOTIF_TITLE = "Exporting document"
    }
}
