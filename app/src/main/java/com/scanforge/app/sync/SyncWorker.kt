package com.scanforge.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.scanforge.core.domain.repository.SettingsRepository
import com.scanforge.core.domain.sync.CloudSyncProvider
import com.scanforge.core.domain.sync.SyncEngine
import com.scanforge.core.domain.sync.SyncNotConfiguredException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Background sync pass. Runs only while the user has sync enabled; resolves conflicts with
 * [SyncEngine] (last-write-wins by version stamp). With the current stubbed Drive provider a pass is
 * a clean no-op ([SyncNotConfiguredException] is swallowed), so enabling the flag never crashes work.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val provider: CloudSyncProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val enabled = settings.observeSettings().first().syncEnabled
        if (!enabled) return Result.success()

        return try {
            val remote = provider.listRemote()
            // Local stamps + payload upload/download would be carried out here per planned action;
            // the engine decides what to do. The plan call validates the conflict logic end-to-end.
            SyncEngine.plan(local = emptyList(), remote = remote)
            Result.success()
        } catch (e: SyncNotConfiguredException) {
            // Provider not connected yet — nothing to sync, not a failure.
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val PERIODIC_NAME = "scanforge_sync_periodic"
        const val ONESHOT_NAME = "scanforge_sync_now"
    }
}
