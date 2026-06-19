package com.scanforge.core.ocr

import com.scanforge.core.domain.imaging.PageProcessing
import com.scanforge.core.domain.model.Document
import com.scanforge.core.domain.model.NewPage
import com.scanforge.core.domain.model.OcrStatus
import com.scanforge.core.domain.model.Page
import com.scanforge.core.domain.ocr.OcrDocument
import com.scanforge.core.domain.ocr.OcrEngine
import com.scanforge.core.domain.ocr.OcrLanguage
import com.scanforge.core.domain.ocr.OcrLanguageMode
import com.scanforge.core.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class OcrRunnerTest {

    @Test
    fun `runPage transitions InProgress then Completed and stores the result`() = runTest {
        val repo = FakeRepo(page(1, OcrStatus.Queued))
        val engine = FakeEngine { OcrDocument.from(emptyList(), 10, 10, OcrLanguage.Latin).copy(fullText = "hi") }
        OcrRunner(engine, repo).runPage(1)

        assertEquals(
            listOf(OcrStatus.InProgress, OcrStatus.Completed),
            repo.statusHistory[1],
        )
        assertEquals("hi", repo.savedResults[1]?.fullText)
    }

    @Test
    fun `runPage marks the page Failed when the engine throws`() = runTest {
        val repo = FakeRepo(page(1, OcrStatus.Queued))
        val engine = FakeEngine { error("decode failed") }
        OcrRunner(engine, repo).runPage(1)

        assertEquals(OcrStatus.Failed, repo.pages.getValue(1).ocrStatus)
        assertTrue(repo.savedResults.isEmpty())
    }

    @Test
    fun `runPage passes the page's language selection to the engine`() = runTest {
        val repo = FakeRepo(page(1, OcrStatus.Queued, language = "Devanagari"))
        val engine = FakeEngine { OcrDocument.EMPTY }
        OcrRunner(engine, repo).runPage(1)

        assertEquals(OcrLanguageMode.Manual(OcrLanguage.Devanagari), engine.lastMode)
    }

    @Test
    fun `runDocument only processes queued or in-progress pages`() = runTest {
        val repo = FakeRepo(
            page(1, OcrStatus.Queued),
            page(2, OcrStatus.Completed),
            page(3, OcrStatus.NotStarted),
            page(4, OcrStatus.InProgress),
        )
        val engine = FakeEngine { OcrDocument.EMPTY }
        OcrRunner(engine, repo).runDocument(DOC_ID)

        assertEquals(setOf(1L, 4L), repo.savedResults.keys)
    }

    // ── Fakes ────────────────────────────────────────────────────────────────────────────────────

    private class FakeEngine(val result: () -> OcrDocument) : OcrEngine {
        var lastMode: OcrLanguageMode? = null
        override suspend fun recognize(imagePath: String, mode: OcrLanguageMode): OcrDocument {
            lastMode = mode
            return result()
        }
    }

    private class FakeRepo(vararg initial: Page) : DocumentRepository {
        val pages = initial.associateBy { it.id }.toMutableMap()
        val statusHistory = mutableMapOf<Long, MutableList<OcrStatus>>()
        val savedResults = mutableMapOf<Long, OcrDocument>()

        override suspend fun getPage(pageId: Long): Page? = pages[pageId]

        override suspend fun getDocumentPages(documentId: Long): List<Page> =
            pages.values.filter { it.documentId == documentId }.sortedBy { it.pageOrder }

        override suspend fun setPageOcrStatus(pageId: Long, status: OcrStatus) {
            statusHistory.getOrPut(pageId) { mutableListOf() }.add(status)
            pages[pageId]?.let { pages[pageId] = it.copy(ocrStatus = status) }
        }

        override suspend fun savePageOcrResult(pageId: Long, result: OcrDocument) {
            statusHistory.getOrPut(pageId) { mutableListOf() }.add(OcrStatus.Completed)
            savedResults[pageId] = result
            pages[pageId]?.let { pages[pageId] = it.copy(ocrStatus = OcrStatus.Completed) }
        }

        // Unused by OcrRunner.
        override fun observeDocuments(): Flow<List<Document>> = throw NotImplementedError()
        override fun observeDocument(id: Long): Flow<Document?> = throw NotImplementedError()
        override fun searchDocuments(query: String): Flow<List<Document>> = throw NotImplementedError()
        override suspend fun createDocument(title: String): Long = throw NotImplementedError()
        override suspend fun createDocumentWithPages(title: String, pages: List<NewPage>): Long = throw NotImplementedError()
        override fun observeDocumentPages(documentId: Long): Flow<List<Page>> = throw NotImplementedError()
        override fun observePage(pageId: Long): Flow<Page?> = throw NotImplementedError()
        override suspend fun updatePageProcessing(pageId: Long, processedImagePath: String, thumbnailPath: String, processing: PageProcessing) = throw NotImplementedError()
        override suspend fun queueDocumentForOcr(documentId: Long, mode: OcrLanguageMode) = throw NotImplementedError()
        override suspend fun queuePageForOcr(pageId: Long, mode: OcrLanguageMode) = throw NotImplementedError()
        override suspend fun updatePageText(pageId: Long, text: String) = throw NotImplementedError()
        override suspend fun addPage(documentId: Long, page: NewPage): Long = throw NotImplementedError()
        override suspend fun deletePage(pageId: Long) = throw NotImplementedError()
        override suspend fun reorderPages(documentId: Long, orderedPageIds: List<Long>) = throw NotImplementedError()
        override suspend fun renameDocument(id: Long, title: String) = throw NotImplementedError()
        override suspend fun deleteDocument(id: Long) = throw NotImplementedError()
        override fun observeLibrary(
            sort: com.scanforge.core.domain.library.DocumentSort,
            filter: com.scanforge.core.domain.library.DocumentFilter,
        ): Flow<List<Document>> = throw NotImplementedError()
        override fun search(query: String): Flow<List<com.scanforge.core.domain.library.SearchResult>> = throw NotImplementedError()
        override suspend fun setFavorite(id: Long, favorite: Boolean) = throw NotImplementedError()
        override suspend fun moveToFolder(documentIds: Collection<Long>, folderId: Long?) = throw NotImplementedError()
        override suspend fun duplicateDocument(id: Long): Long = throw NotImplementedError()
        override fun observeTrash(): Flow<List<Document>> = throw NotImplementedError()
        override suspend fun moveToTrash(ids: Collection<Long>) = throw NotImplementedError()
        override suspend fun restoreFromTrash(ids: Collection<Long>) = throw NotImplementedError()
        override suspend fun emptyTrash(): Int = throw NotImplementedError()
        override suspend fun purgeTrashedBefore(threshold: java.time.Instant): Int = throw NotImplementedError()
    }

    private companion object {
        const val DOC_ID = 42L

        fun page(id: Long, status: OcrStatus, language: String? = null) = Page(
            id = id,
            documentId = DOC_ID,
            pageOrder = id.toInt(),
            originalImagePath = "/tmp/$id.jpg",
            language = language,
            ocrStatus = status,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
