package com.scanforge.core.domain.repository

import com.scanforge.core.domain.imaging.PageProcessing
import com.scanforge.core.domain.library.DocumentFilter
import com.scanforge.core.domain.library.DocumentSort
import com.scanforge.core.domain.library.SearchResult
import com.scanforge.core.domain.model.Document
import com.scanforge.core.domain.model.Folder
import com.scanforge.core.domain.model.NewPage
import com.scanforge.core.domain.model.OcrStatus
import com.scanforge.core.domain.model.Page
import com.scanforge.core.domain.model.ScanSettings
import com.scanforge.core.domain.model.Tag
import com.scanforge.core.domain.ocr.OcrDocument
import com.scanforge.core.domain.ocr.OcrLanguageMode
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Document persistence contract. Defined in the domain so the presentation layer depends on this
 * interface, never on Room. Read APIs are reactive [Flow]s; writes are `suspend` and return the
 * affected id where useful.
 */
interface DocumentRepository {
    /** All non-trashed documents, newest first. */
    fun observeDocuments(): Flow<List<Document>>

    /**
     * Non-trashed documents narrowed by [filter] and ordered by [sort]. Backs the library home with
     * its sort menu and filter sheet; favourites are still included (the UI lifts them into a section).
     */
    fun observeLibrary(sort: DocumentSort, filter: DocumentFilter): Flow<List<Document>>

    /** Emits the document (with its pages and tags), or `null` if it no longer exists. */
    fun observeDocument(id: Long): Flow<Document?>

    /** Case-insensitive search over titles and extracted page text. */
    fun searchDocuments(query: String): Flow<List<Document>>

    /**
     * Full-text search across document titles, OCR text and tags, returning ranked results with
     * highlighted snippets and jump-to-page locations. Empty/blank query yields an empty list.
     */
    fun search(query: String): Flow<List<SearchResult>>

    suspend fun createDocument(title: String): Long

    /**
     * Creates one document and appends [pages] to it in list order, in a single transaction. Used
     * by single capture (one page), batch capture, and import. The new document's aggregate
     * [com.scanforge.core.domain.model.OcrStatus] reflects its pages (Queued when any page awaits
     * OCR). Returns the new document id.
     */
    suspend fun createDocumentWithPages(title: String, pages: List<NewPage>): Long

    /** Observes the pages of a document in [Page.pageOrder] order. */
    fun observeDocumentPages(documentId: Long): Flow<List<Page>>

    /** Emits a single page by id, or `null` if it no longer exists. Used by the page editor. */
    fun observePage(pageId: Long): Flow<Page?>

    /** One-shot page fetch, used by the OCR worker (no need for a reactive stream there). */
    suspend fun getPage(pageId: Long): Page?

    /** One-shot ordered page snapshot for a document, used by the batch OCR worker. */
    suspend fun getDocumentPages(documentId: Long): List<Page>

    // ── OCR ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Marks every page of a document (re-)queued for OCR with the given [mode] selection and sets the
     * document aggregate to [OcrStatus.Queued]. Call before enqueuing work so the UI shows PENDING
     * immediately. Used by both first run and "Re-run OCR".
     */
    suspend fun queueDocumentForOcr(documentId: Long, mode: OcrLanguageMode)

    /** Marks one page [OcrStatus.Queued] with [mode] (single-page re-run). */
    suspend fun queuePageForOcr(pageId: Long, mode: OcrLanguageMode)

    /** Transitions a page's OCR lifecycle (e.g. Queued→InProgress→Failed) and recomputes the document. */
    suspend fun setPageOcrStatus(pageId: Long, status: OcrStatus)

    /**
     * Persists a completed page OCR result: stores [result] as structured JSON, mirrors its text into
     * the searchable `extracted_text` column, sets the page [OcrStatus.Completed], and recomputes the
     * document aggregate.
     */
    suspend fun savePageOcrResult(pageId: Long, result: OcrDocument)

    /** Saves a user's inline correction of the recognised text back to the page. */
    suspend fun updatePageText(pageId: Long, text: String)

    /**
     * Records the result of (re-)processing a page: the freshly rendered [processedImagePath], its
     * regenerated [thumbnailPath], and the re-editable [processing] recipe. The archived original is
     * untouched, keeping edits non-destructive.
     */
    suspend fun updatePageProcessing(
        pageId: Long,
        processedImagePath: String,
        thumbnailPath: String,
        processing: PageProcessing,
    )

    /** Appends a page to an existing document and returns the new page id. */
    suspend fun addPage(documentId: Long, page: NewPage): Long

    /** Deletes a single page and re-packs the remaining pages' order. */
    suspend fun deletePage(pageId: Long)

    /** Reassigns [Page.pageOrder] to match the given id ordering (0-based, head to tail). */
    suspend fun reorderPages(documentId: Long, orderedPageIds: List<Long>)

    suspend fun renameDocument(id: Long, title: String)

    // ── Organisation: favourites, folders, duplication ────────────────────────────────────────────

    /** Stars or un-stars a document. */
    suspend fun setFavorite(id: Long, favorite: Boolean)

    /** Moves [documentIds] into [folderId] (`null` = library root). */
    suspend fun moveToFolder(documentIds: Collection<Long>, folderId: Long?)

    /** Deep-copies a document and all of its pages (new ids) and returns the new document id. */
    suspend fun duplicateDocument(id: Long): Long

    // ── Trash (soft delete) ───────────────────────────────────────────────────────────────────────

    /** Trashed documents, most recently deleted first. */
    fun observeTrash(): Flow<List<Document>>

    /** Soft-deletes documents: they leave the library and move to the trash. */
    suspend fun moveToTrash(ids: Collection<Long>)

    /** Restores documents from the trash back into the library. */
    suspend fun restoreFromTrash(ids: Collection<Long>)

    /** Permanently deletes a single document (and its page files are cleaned up by the caller). */
    suspend fun deleteDocument(id: Long)

    /** Permanently empties the trash (deleting page image files too); returns the count removed. */
    suspend fun emptyTrash(): Int

    /**
     * Permanently deletes trashed documents whose deletion time is older than [threshold] (auto-purge),
     * removing their page image files too. Returns the number of documents purged.
     */
    suspend fun purgeTrashedBefore(threshold: Instant): Int
}

/** Folder (collection) CRUD and hierarchy. Folders are nestable via a parent id. */
interface FolderRepository {
    /** All folders, populated with their direct (non-trashed) document counts. */
    fun observeFolders(): Flow<List<Folder>>

    /** Direct child folders of [parentId] (`null` = root-level folders). */
    fun observeChildFolders(parentId: Long?): Flow<List<Folder>>

    suspend fun createFolder(name: String, parentId: Long? = null, colorHex: String? = null): Long

    suspend fun renameFolder(id: Long, name: String)

    suspend fun setFolderColor(id: Long, colorHex: String?)

    /** Deletes a folder; its documents and child folders fall back to the parent/root (not deleted). */
    suspend fun deleteFolder(id: Long)
}

/** Tag CRUD and document↔tag association. */
interface TagRepository {
    fun observeTags(): Flow<List<Tag>>

    suspend fun createTag(name: String, colorHex: String? = null): Long

    suspend fun deleteTag(id: Long)

    /** Replaces the full set of tags on a document. */
    suspend fun setDocumentTags(documentId: Long, tagIds: Set<Long>)
}

/** User scan/export preferences, backed by DataStore. */
interface SettingsRepository {
    fun observeSettings(): Flow<ScanSettings>

    suspend fun updateSettings(transform: (ScanSettings) -> ScanSettings)

    /** Whether first-launch onboarding has been completed. Drives the onboarding gate. */
    fun observeOnboardingComplete(): Flow<Boolean>

    suspend fun setOnboardingComplete(complete: Boolean)
}
