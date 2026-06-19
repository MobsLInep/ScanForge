package com.scanforge.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.scanforge.core.domain.sync.SyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules [SyncWorker] under a CONNECTED (and battery-not-low) network constraint. Periodic sync is
 * only registered while the user opts in; disabling cancels it. All sync respects connectivity, so it
 * never runs offline.
 */
@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    override fun enable() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun disable() {
        workManager.cancelUniqueWork(SyncWorker.PERIODIC_NAME)
    }

    override fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(SyncWorker.ONESHOT_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

/** Binds the WorkManager-backed [SyncScheduler]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
