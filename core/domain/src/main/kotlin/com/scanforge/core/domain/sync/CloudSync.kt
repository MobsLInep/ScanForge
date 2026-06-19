package com.scanforge.core.domain.sync

import kotlinx.coroutines.flow.Flow

/** A document's identity + version stamp used for conflict resolution. */
data class SyncStamp(
    val documentId: Long,
    /** Monotonic version (updatedAt epoch millis); the larger value wins under last-write-wins. */
    val versionStamp: Long,
)

/** A remote entry as reported by a provider — its id mapping and current version. */
data class RemoteEntry(
    val documentId: Long,
    val versionStamp: Long,
    /** Provider-specific handle (e.g. Drive file id), opaque to the engine. */
    val remoteId: String,
)

/** Per-document plan the engine produces; the worker carries each out via the provider/repository. */
sealed interface SyncAction {
    val documentId: Long

    /** Local copy is newer (or remote-absent): push it up. */
    data class Upload(override val documentId: Long, val remoteId: String?) : SyncAction

    /** Remote copy is newer: pull it down. */
    data class Download(override val documentId: Long, val remoteId: String) : SyncAction

    /** Same version on both sides: nothing to do. */
    data class InSync(override val documentId: Long) : SyncAction
}

/** Connection/auth state of the configured provider. */
sealed interface SyncStatus {
    /** Sync is switched off in settings. */
    data object Disabled : SyncStatus

    /** Enabled but no provider account is connected. */
    data object NotConnected : SyncStatus

    data class Connected(val accountLabel: String) : SyncStatus

    data class Error(val reason: String) : SyncStatus
}

/** Result of a single sync pass, surfaced for the UI / logs. */
data class SyncOutcome(
    val uploaded: Int,
    val downloaded: Int,
    val unchanged: Int,
    val failed: Int,
) {
    val isClean: Boolean get() = failed == 0

    companion object {
        val Empty = SyncOutcome(0, 0, 0, 0)
    }
}

/**
 * Pure conflict resolver. Given the local version stamps and what the remote reports, it decides per
 * document whether to upload, download, or leave alone — **last-write-wins by [SyncStamp.versionStamp]**.
 * Has zero I/O so it is fully unit-testable; the worker performs the planned actions.
 */
object SyncEngine {
    fun plan(local: List<SyncStamp>, remote: List<RemoteEntry>): List<SyncAction> {
        val remoteById = remote.associateBy { it.documentId }
        val localIds = local.map { it.documentId }.toSet()

        val actions = local.map { stamp ->
            when (val r = remoteById[stamp.documentId]) {
                null -> SyncAction.Upload(stamp.documentId, remoteId = null)
                else -> when {
                    stamp.versionStamp > r.versionStamp -> SyncAction.Upload(stamp.documentId, r.remoteId)
                    stamp.versionStamp < r.versionStamp -> SyncAction.Download(stamp.documentId, r.remoteId)
                    else -> SyncAction.InSync(stamp.documentId)
                }
            }
        }

        // Documents that exist only remotely should be pulled down.
        val remoteOnly = remote
            .filter { it.documentId !in localIds }
            .map { SyncAction.Download(it.documentId, it.remoteId) }

        return actions + remoteOnly
    }

    /** Tallies a set of carried-out actions into a [SyncOutcome]. */
    fun summarize(actions: List<SyncAction>, failedIds: Set<Long> = emptySet()): SyncOutcome =
        SyncOutcome(
            uploaded = actions.count { it is SyncAction.Upload && it.documentId !in failedIds },
            downloaded = actions.count { it is SyncAction.Download && it.documentId !in failedIds },
            unchanged = actions.count { it is SyncAction.InSync },
            failed = failedIds.size,
        )
}

/**
 * Abstraction over a cloud backend (Google Drive, etc.). Kept deliberately small and fully mockable
 * so sync can be exercised with a fake in unit tests. A real provider lives behind a feature flag and
 * is **off by default**; [GoogleDrive] ships as a stub until OAuth is configured.
 */
interface CloudSyncProvider {
    /** Stable identifier for the provider implementation. */
    val id: String

    fun status(): Flow<SyncStatus>

    /** Begins/refreshes authentication. Returns the connected status or an error. */
    suspend fun connect(): SyncStatus

    suspend fun disconnect()

    /** Lists the remote document stamps the engine needs to plan a sync. */
    suspend fun listRemote(): List<RemoteEntry>

    /** Uploads a serialized document payload, returning its remote id. */
    suspend fun upload(documentId: Long, versionStamp: Long, payload: ByteArray): String

    /** Downloads a remote document payload by handle. */
    suspend fun download(remoteId: String): ByteArray
}

/** Thrown by stub/unconfigured providers so callers can degrade gracefully. */
class SyncNotConfiguredException(providerId: String) :
    IllegalStateException("Cloud provider '$providerId' is not configured")

/** Schedules background sync passes under connectivity constraints. Off until [enable] is called. */
interface SyncScheduler {
    /** Registers periodic sync (network-constrained). No-op if already enabled. */
    fun enable()

    /** Cancels any scheduled/periodic sync. */
    fun disable()

    /** Requests a one-off sync now (still network-constrained). */
    fun syncNow()
}
