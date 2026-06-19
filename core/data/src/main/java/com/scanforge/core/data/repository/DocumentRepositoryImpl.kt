package com.scanforge.core.data.repository

import com.scanforge.core.common.dispatchers.Dispatcher
import com.scanforge.core.common.dispatchers.ScanForgeDispatcher
import com.scanforge.core.data.mapper.OcrJson
import com.scanforge.core.data.mapper.ProcessingJson
import com.scanforge.core.data.mapper.toDomain
import com.scanforge.core.database.dao.DocumentDao
import com.scanforge.core.database.dao.PageDao
import com.scanforge.core.database.dao.TagDao
import com.scanforge.core.database.entity.DocumentEntity
import com.scanforge.core.database.entity.DocumentTagCrossRef
import com.scanforge.core.database.entity.PageEntity
import com.scanforge.core.domain.imaging.PageProcessing
import com.scanforge.core.domain.library.DocumentFilter
import com.scanforge.core.domain.library.DocumentSort
import com.scanforge.core.domain.library.SearchRanker
import com.scanforge.core.domain.library.SearchResult
import com.scanforge.core.domain.library.sortedBy
import com.scanforge.core.domain.model.Document
import com.scanforge.core.domain.model.NewPage
import com.scanforge.core.domain.model.OcrStatus
import com.scanforge.core.domain.model.Page
import com.scanforge.core.domain.ocr.OcrDocument
import com.scanforge.core.domain.ocr.OcrLanguageMode
import com.scanforge.core.domain.repository.DocumentRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val tagDao: TagDao,
    private val ftsIndexer: FtsIndexer,
    @Dispatcher(ScanForgeDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : DocumentRepository {

    override fun observeDocuments(): Flow<List<Document>> =
        documentDao.observeDocuments().map { list -> list.map { it.toDomain() } }

    override fun observeLibrary(sort: DocumentSort, filter: DocumentFilter): Flow<List<Document>> =
        documentDao.observeDocuments().map { list ->
            list.map { it.toDomain() }
                .filter(filter::matches)
                .sortedBy(sort)
        }

    override fun observeDocument(id: Long): Flow<Document?> =
        documentDao.observeDocument(id).map { it?.toDomain() }

    override fun searchDocuments(query: String): Flow<List<Document>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            observeDocuments()
        } else {
            documentDao.searchDocuments(trimmed).map { list -> list.map { it.toDomain() } }
        }
    }

    override fun search(query: String): Flow<List<SearchResult>> {
        val match = FtsIndexer.buildMatchQuery(query) ?: return flowOf(emptyList())
        return documentDao.searchFts(match).map { candidates ->
            SearchRanker.rank(query, candidates.map { it.toDomain() })
        }
    }

    override fun observeDocumentPages(documentId: Long): Flow<List<Page>> =
        pageDao.observePages(documentId).map { list -> list.map { it.toDomain() } }

    override fun observePage(pageId: Long): Flow<Page?> =
        pageDao.observePage(pageId).map { it?.toDomain() }

    override suspend fun getPage(pageId: Long): Page? = withContext(ioDispatcher) {
        pageDao.getPage(pageId)?.toDomain()
    }

    override suspend fun getDocumentPages(documentId: Long): List<Page> = withContext(ioDispatcher) {
        pageDao.getPages(documentId).map { it.toDomain() }
    }

    override suspend fun queueDocumentForOcr(
        documentId: Long,
        mode: OcrLanguageMode,
    ) = withContext(ioDispatcher) {
        val now = Instant.now()
        val tag = mode.storageTag()
        pageDao.getPages(documentId).forEach { page ->
            pageDao.updateOcrSelection(page.id, OcrStatus.Queued.name, tag, now)
        }
        documentDao.updateOcrStatus(documentId, OcrStatus.Queued.name, now)
    }

    override suspend fun queuePageForOcr(
        pageId: Long,
        mode: OcrLanguageMode,
    ) = withContext(ioDispatcher) {
        val now = Instant.now()
        pageDao.updateOcrSelection(pageId, OcrStatus.Queued.name, mode.storageTag(), now)
        pageDao.documentIdOf(pageId)?.let { recomputeDocumentStatus(it, now) }
        Unit
    }

    override suspend fun setPageOcrStatus(
        pageId: Long,
        status: OcrStatus,
    ) = withContext(ioDispatcher) {
        val now = Instant.now()
        pageDao.updateOcrStatus(pageId, status.name, now)
        pageDao.documentIdOf(pageId)?.let { recomputeDocumentStatus(it, now) }
        Unit
    }

    override suspend fun savePageOcrResult(
        pageId: Long,
        result: OcrDocument,
    ) = withContext(ioDispatcher) {
        val now = Instant.now()
        pageDao.updateOcrResult(
            id = pageId,
            extractedText = result.fullText,
            ocrBlocks = OcrJson.encode(result),
            status = OcrStatus.Completed.name,
            updatedAt = now,
        )
        pageDao.documentIdOf(pageId)?.let { documentId ->
            recomputeDocumentStatus(documentId, now)
            // Newly recognised text becomes searchable immediately.
            ftsIndexer.reindex(documentId)
        }
        Unit
    }

    override suspend fun updatePageText(
        pageId: Long,
        text: String,
    ) = withContext(ioDispatcher) {
        val now = Instant.now()
        pageDao.updateExtractedText(pageId, text, now)
        pageDao.documentIdOf(pageId)?.let { documentId ->
            documentDao.touch(documentId, now)
            ftsIndexer.reindex(documentId)
        }
        Unit
    }

    /** Recomputes a document's aggregate OCR status from its pages and writes it. */
    private suspend fun recomputeDocumentStatus(documentId: Long, now: Instant) {
        val statuses = pageDao.ocrStatuses(documentId).map { it.toOcrStatusOrNotStarted() }
        documentDao.updateOcrStatus(documentId, aggregateStatus(statuses).name, now)
    }

    override suspend fun updatePageProcessing(
        pageId: Long,
        processedImagePath: String,
        thumbnailPath: String,
        processing: PageProcessing,
    ) = withContext(ioDispatcher) {
        val now = Instant.now()
        pageDao.updateProcessing(
            id = pageId,
            processedImagePath = processedImagePath,
            thumbnailPath = thumbnailPath,
            processingParams = ProcessingJson.encode(processing),
            updatedAt = now,
        )
        pageDao.documentIdOf(pageId)?.let { documentId ->
            documentDao.touch(documentId, now)
            refreshDerived(documentId)
        }
        Unit
    }

    override suspend fun createDocument(title: String): Long = withContext(ioDispatcher) {
        val now = Instant.now()
        documentDao.insert(
            DocumentEntity(
                title = title,
                ocrStatus = OcrStatus.NotStarted.name,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun createDocumentWithPages(
        title: String,
        pages: List<NewPage>,
    ): Long = withContext(ioDispatcher) {
        val now = Instant.now()
        val aggregate = aggregateStatus(pages.map { it.ocrStatus })
        val documentId = documentDao.insertDocumentWithPages(
            document = DocumentEntity(
                title = title,
                ocrStatus = aggregate.name,
                thumbnailPath = pages.firstOrNull()?.thumbnailPath,
                createdAt = now,
                updatedAt = now,
            ),
            pagesFor = { docId ->
                pages.mapIndexed { index, page -> page.toEntity(docId, index, now) }
            },
        )
        refreshDerived(documentId)
        ftsIndexer.reindex(documentId)
        documentId
    }

    override suspend fun addPage(documentId: Long, page: NewPage): Long = withContext(ioDispatcher) {
        val now = Instant.now()
        val order = pageDao.countPages(documentId)
        val id = pageDao.insert(page.toEntity(documentId, order, now))
        documentDao.touch(documentId, now)
        refreshDerived(documentId)
        id
    }

    override suspend fun deletePage(pageId: Long) = withContext(ioDispatcher) {
        val documentId = pageDao.documentIdOf(pageId) ?: return@withContext
        pageDao.delete(pageId)
        // Re-pack remaining pages so order stays a dense 0..n-1 sequence.
        val now = Instant.now()
        pageDao.getPages(documentId).forEachIndexed { index, page ->
            if (page.pageOrder != index) pageDao.updateOrder(page.id, index, now)
        }
        documentDao.touch(documentId, now)
        refreshDerived(documentId)
        ftsIndexer.reindex(documentId)
    }

    override suspend fun reorderPages(
        documentId: Long,
        orderedPageIds: List<Long>,
    ) = withContext(ioDispatcher) {
        val now = Instant.now()
        orderedPageIds.forEachIndexed { index, pageId -> pageDao.updateOrder(pageId, index, now) }
        documentDao.touch(documentId, now)
        refreshDerived(documentId)
    }

    override suspend fun renameDocument(id: Long, title: String) = withContext(ioDispatcher) {
        documentDao.updateTitle(id, title, Instant.now())
        ftsIndexer.reindex(id)
    }

    // ── Organisation ──────────────────────────────────────────────────────────────────────────────

    override suspend fun setFavorite(id: Long, favorite: Boolean) = withContext(ioDispatcher) {
        documentDao.setFavorite(id, favorite)
    }

    override suspend fun moveToFolder(
        documentIds: Collection<Long>,
        folderId: Long?,
    ) = withContext(ioDispatcher) {
        documentDao.moveToFolder(documentIds.toList(), folderId)
    }

    override suspend fun duplicateDocument(id: Long): Long = withContext(ioDispatcher) {
        val source = documentDao.getPopulated(id) ?: return@withContext 0L
        val now = Instant.now()
        val newId = documentDao.insertDocumentWithPages(
            document = DocumentEntity(
                title = "${source.document.title} (copy)",
                ocrStatus = source.document.ocrStatus,
                thumbnailPath = copyImageFile(source.document.thumbnailPath),
                isFavorite = false,
                folderId = source.document.folderId,
                sizeBytes = source.document.sizeBytes,
                createdAt = now,
                updatedAt = now,
            ),
            pagesFor = { docId ->
                source.pages.sortedBy { it.pageOrder }.mapIndexed { index, page ->
                    page.copy(
                        id = 0,
                        documentId = docId,
                        pageOrder = index,
                        originalImagePath = copyImageFile(page.originalImagePath) ?: page.originalImagePath,
                        processedImagePath = copyImageFile(page.processedImagePath),
                        thumbnailPath = copyImageFile(page.thumbnailPath),
                        createdAt = now,
                        updatedAt = now,
                    )
                }
            },
        )
        source.tags.forEach { tagDao.insertCrossRef(DocumentTagCrossRef(newId, it.id)) }
        ftsIndexer.reindex(newId)
        newId
    }

    // ── Trash (soft delete) ───────────────────────────────────────────────────────────────────────

    override fun observeTrash(): Flow<List<Document>> =
        documentDao.observeTrash().map { list -> list.map { it.toDomain() } }

    override suspend fun moveToTrash(ids: Collection<Long>) = withContext(ioDispatcher) {
        documentDao.moveToTrash(ids.toList(), Instant.now())
        // Trashed documents must not surface in search.
        ids.forEach { documentDao.deleteFts(it) }
    }

    override suspend fun restoreFromTrash(ids: Collection<Long>) = withContext(ioDispatcher) {
        documentDao.restoreFromTrash(ids.toList(), Instant.now())
        ids.forEach { ftsIndexer.reindex(it) }
    }

    override suspend fun deleteDocument(id: Long) = withContext(ioDispatcher) {
        documentDao.deleteFts(id)
        documentDao.delete(id)
    }

    override suspend fun emptyTrash(): Int = withContext(ioDispatcher) {
        purgeDocuments(documentDao.trashedIds())
    }

    override suspend fun purgeTrashedBefore(threshold: Instant): Int = withContext(ioDispatcher) {
        purgeDocuments(documentDao.trashedIdsBefore(threshold))
    }

    /** Permanently deletes [ids], removing their page image files and FTS rows; returns the count. */
    private suspend fun purgeDocuments(ids: List<Long>): Int {
        ids.forEach { id ->
            pageDao.getPages(id).forEach { page ->
                deleteFileQuietly(page.originalImagePath)
                deleteFileQuietly(page.processedImagePath)
                deleteFileQuietly(page.thumbnailPath)
            }
            documentDao.deleteFts(id)
            documentDao.delete(id) // cascades to pages
        }
        return ids.size
    }

    private fun deleteFileQuietly(path: String?) {
        if (path == null) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    /** Recomputes a document's stored size (sum of page file bytes) and mirrors its first thumbnail. */
    private suspend fun refreshDerived(documentId: Long) {
        val pages = pageDao.getPages(documentId)
        val size = pages.sumOf { page ->
            val path = page.processedImagePath ?: page.originalImagePath
            runCatching { File(path).length() }.getOrDefault(0L)
        }
        documentDao.updateSize(documentId, size)
        documentDao.updateThumbnail(documentId, pages.firstOrNull()?.thumbnailPath)
    }

    /** Deep-copies an image file alongside the original with a fresh name; `null` paths pass through. */
    private fun copyImageFile(path: String?): String? {
        if (path == null) return null
        val source = File(path)
        if (!source.exists()) return path
        val ext = source.extension.ifEmpty { "jpg" }
        val dest = File(source.parentFile, "${UUID.randomUUID()}.$ext")
        return runCatching { source.copyTo(dest, overwrite = true).absolutePath }.getOrDefault(path)
    }

    private fun NewPage.toEntity(documentId: Long, order: Int, now: Instant) = PageEntity(
        documentId = documentId,
        pageOrder = order,
        originalImagePath = originalImagePath,
        processedImagePath = processedImagePath,
        thumbnailPath = thumbnailPath,
        ocrStatus = ocrStatus.name,
        processingParams = processing?.let(ProcessingJson::encode),
        createdAt = now,
        updatedAt = now,
    )

    /**
     * Rolls a document's aggregate OCR status up from its pages: any page processing wins, else any
     * still queued, else all done is Completed, else any failure surfaces Failed, else NotStarted.
     */
    private fun aggregateStatus(statuses: List<OcrStatus>): OcrStatus = when {
        statuses.any { it == OcrStatus.InProgress } -> OcrStatus.InProgress
        statuses.any { it == OcrStatus.Queued } -> OcrStatus.Queued
        statuses.isNotEmpty() && statuses.all { it == OcrStatus.Completed } -> OcrStatus.Completed
        statuses.any { it == OcrStatus.Failed } -> OcrStatus.Failed
        else -> OcrStatus.NotStarted
    }

    private fun String.toOcrStatusOrNotStarted(): OcrStatus =
        runCatching { OcrStatus.valueOf(this) }.getOrDefault(OcrStatus.NotStarted)
}
