package com.scanforge.core.data.sync

import com.scanforge.core.domain.sync.CloudSyncProvider
import com.scanforge.core.domain.sync.RemoteEntry
import com.scanforge.core.domain.sync.SyncNotConfiguredException
import com.scanforge.core.domain.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inert provider used when no cloud backend is wired. Keeps the sync surface live (and fully
 * mockable) while doing nothing — sync stays effectively off.
 */
@Singleton
class NoOpCloudSyncProvider @Inject constructor() : CloudSyncProvider {
    override val id: String = "none"
    override fun status(): Flow<SyncStatus> = flowOf(SyncStatus.NotConnected)
    override suspend fun connect(): SyncStatus = SyncStatus.NotConnected
    override suspend fun disconnect() = Unit
    override suspend fun listRemote(): List<RemoteEntry> = emptyList()
    override suspend fun upload(documentId: Long, versionStamp: Long, payload: ByteArray): String =
        throw SyncNotConfiguredException(id)
    override suspend fun download(remoteId: String): ByteArray = throw SyncNotConfiguredException(id)
}

/**
 * Google Drive provider — **stub behind the sync feature flag**. The abstraction, scheduling, and
 * conflict engine are complete; wiring real OAuth (play-services-auth) + the Drive REST client is the
 * only remaining step. Until then it advertises itself but refuses to connect, so enabling sync in
 * settings surfaces a clear "not configured" state rather than failing silently.
 */
@Singleton
class GoogleDriveSyncProvider @Inject constructor() : CloudSyncProvider {
    override val id: String = "google_drive"

    override fun status(): Flow<SyncStatus> = flowOf(SyncStatus.NotConnected)

    override suspend fun connect(): SyncStatus =
        SyncStatus.Error("Google Drive sign-in is not configured in this build")

    override suspend fun disconnect() = Unit

    override suspend fun listRemote(): List<RemoteEntry> = throw SyncNotConfiguredException(id)

    override suspend fun upload(documentId: Long, versionStamp: Long, payload: ByteArray): String =
        throw SyncNotConfiguredException(id)

    override suspend fun download(remoteId: String): ByteArray = throw SyncNotConfiguredException(id)
}
