package com.scanforge.core.domain.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncEngineTest {

    @Test
    fun `local newer than remote uploads`() {
        val plan = SyncEngine.plan(
            local = listOf(SyncStamp(1, versionStamp = 200)),
            remote = listOf(RemoteEntry(1, versionStamp = 100, remoteId = "r1")),
        )
        assertEquals(listOf(SyncAction.Upload(1, "r1")), plan)
    }

    @Test
    fun `remote newer than local downloads`() {
        val plan = SyncEngine.plan(
            local = listOf(SyncStamp(1, versionStamp = 100)),
            remote = listOf(RemoteEntry(1, versionStamp = 200, remoteId = "r1")),
        )
        assertEquals(listOf(SyncAction.Download(1, "r1")), plan)
    }

    @Test
    fun `equal version stamps are in sync`() {
        val plan = SyncEngine.plan(
            local = listOf(SyncStamp(1, versionStamp = 100)),
            remote = listOf(RemoteEntry(1, versionStamp = 100, remoteId = "r1")),
        )
        assertEquals(listOf(SyncAction.InSync(1)), plan)
    }

    @Test
    fun `local-only document is uploaded with no remote id`() {
        val plan = SyncEngine.plan(
            local = listOf(SyncStamp(7, versionStamp = 50)),
            remote = emptyList(),
        )
        assertEquals(listOf(SyncAction.Upload(7, null)), plan)
    }

    @Test
    fun `remote-only document is downloaded`() {
        val plan = SyncEngine.plan(
            local = emptyList(),
            remote = listOf(RemoteEntry(9, versionStamp = 5, remoteId = "r9")),
        )
        assertEquals(listOf(SyncAction.Download(9, "r9")), plan)
    }

    @Test
    fun `summarize counts each action kind and failures`() {
        val actions = listOf(
            SyncAction.Upload(1, null),
            SyncAction.Download(2, "r2"),
            SyncAction.InSync(3),
        )
        val outcome = SyncEngine.summarize(actions, failedIds = setOf(2))
        assertEquals(1, outcome.uploaded)
        assertEquals(0, outcome.downloaded) // doc 2 failed, so not counted as downloaded
        assertEquals(1, outcome.unchanged)
        assertEquals(1, outcome.failed)
        assertTrue(!outcome.isClean)
    }

    @Test
    fun `engine drives a fake provider end to end`() = runBlocking {
        // A fully mockable in-memory provider proves the sync surface is testable without Android.
        val provider = FakeCloudSyncProvider(
            remote = mutableListOf(RemoteEntry(2, versionStamp = 999, remoteId = "remote-2")),
        )
        val local = listOf(
            SyncStamp(1, versionStamp = 10), // local only -> upload
            SyncStamp(2, versionStamp = 1),  // remote newer -> download
        )

        val plan = SyncEngine.plan(local, provider.listRemote())

        val failed = mutableSetOf<Long>()
        for (action in plan) {
            when (action) {
                is SyncAction.Upload -> provider.upload(action.documentId, 10, "doc-${action.documentId}".encodeToByteArray())
                is SyncAction.Download -> provider.download(action.remoteId)
                is SyncAction.InSync -> Unit
            }
        }

        val outcome = SyncEngine.summarize(plan, failed)
        assertEquals(1, outcome.uploaded)
        assertEquals(1, outcome.downloaded)
        assertEquals(1, provider.uploadedPayloads.size)
        assertEquals(1, provider.downloadCalls)
    }

    private class FakeCloudSyncProvider(
        private val remote: MutableList<RemoteEntry>,
    ) : CloudSyncProvider {
        val uploadedPayloads = mutableListOf<ByteArray>()
        var downloadCalls = 0

        override val id = "fake"
        override fun status(): Flow<SyncStatus> = flowOf(SyncStatus.Connected("fake@test"))
        override suspend fun connect() = SyncStatus.Connected("fake@test")
        override suspend fun disconnect() = Unit
        override suspend fun listRemote(): List<RemoteEntry> = remote
        override suspend fun upload(documentId: Long, versionStamp: Long, payload: ByteArray): String {
            uploadedPayloads += payload
            return "remote-$documentId"
        }

        override suspend fun download(remoteId: String): ByteArray {
            downloadCalls++
            return "payload".encodeToByteArray()
        }
    }
}
