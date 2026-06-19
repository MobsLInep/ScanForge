package com.scanforge.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.scanforge.core.database.entity.DocumentEntity
import com.scanforge.core.database.entity.DocumentFtsEntity
import com.scanforge.core.database.entity.PageEntity
import com.scanforge.core.database.entity.PopulatedDocument
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface DocumentDao {

    /** Library list: non-trashed documents, newest first. */
    @Transaction
    @Query("SELECT * FROM documents WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeDocuments(): Flow<List<PopulatedDocument>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeDocument(id: Long): Flow<PopulatedDocument?>

    /** Title or page-text match, newest first. Empty/blank query is handled by the repository. */
    @Transaction
    @Query(
        """
        SELECT * FROM documents WHERE deleted_at IS NULL AND id IN (
            SELECT d.id FROM documents d
            LEFT JOIN pages p ON p.document_id = d.id
            WHERE d.title LIKE '%' || :query || '%'
               OR p.extracted_text LIKE '%' || :query || '%'
        )
        ORDER BY updated_at DESC
        """,
    )
    fun searchDocuments(query: String): Flow<List<PopulatedDocument>>

    /**
     * Full-text search candidates: non-trashed documents whose FTS row matches [match] (an FTS4
     * MATCH expression built by the repository). Reactive so results refresh as content changes; the
     * repository re-ranks and builds snippets in Kotlin.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM documents
        WHERE deleted_at IS NULL
          AND id IN (SELECT rowid FROM document_fts WHERE document_fts MATCH :match)
        ORDER BY updated_at DESC
        """,
    )
    fun searchFts(match: String): Flow<List<PopulatedDocument>>

    @Query("SELECT title FROM documents WHERE id = :id")
    suspend fun getTitle(id: Long): String?

    /** One-shot fully-populated snapshot, used to deep-copy a document. */
    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getPopulated(id: Long): PopulatedDocument?

    // ── Trash (soft delete) ───────────────────────────────────────────────────────────────────────

    @Transaction
    @Query("SELECT * FROM documents WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun observeTrash(): Flow<List<PopulatedDocument>>

    @Query("UPDATE documents SET deleted_at = :now WHERE id IN (:ids)")
    suspend fun moveToTrash(ids: List<Long>, now: Instant)

    @Query("UPDATE documents SET deleted_at = NULL, updated_at = :now WHERE id IN (:ids)")
    suspend fun restoreFromTrash(ids: List<Long>, now: Instant)

    @Query("SELECT id FROM documents WHERE deleted_at IS NOT NULL")
    suspend fun trashedIds(): List<Long>

    @Query("SELECT id FROM documents WHERE deleted_at IS NOT NULL AND deleted_at < :threshold")
    suspend fun trashedIdsBefore(threshold: Instant): List<Long>

    // ── Organisation: favourites, folders, size ───────────────────────────────────────────────────

    @Query("UPDATE documents SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE documents SET folder_id = :folderId WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<Long>, folderId: Long?)

    @Query("UPDATE documents SET folder_id = :newFolderId WHERE folder_id = :oldFolderId")
    suspend fun reassignFolder(oldFolderId: Long, newFolderId: Long?)

    @Query("UPDATE documents SET size_bytes = :size WHERE id = :id")
    suspend fun updateSize(id: Long, size: Long)

    @Query("UPDATE documents SET thumbnail_path = :thumbnailPath WHERE id = :id")
    suspend fun updateThumbnail(id: Long, thumbnailPath: String?)

    // ── Full-text index maintenance ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFts(entity: DocumentFtsEntity)

    @Query("DELETE FROM document_fts WHERE rowid = :id")
    suspend fun deleteFts(id: Long)

    @Insert
    suspend fun insert(document: DocumentEntity): Long

    @Insert
    suspend fun insertPages(pages: List<PageEntity>): List<Long>

    /**
     * Atomically inserts a document and its pages. [pagesFor] receives the freshly generated
     * document id so each page can reference its parent. Returns the new document id.
     */
    @Transaction
    suspend fun insertDocumentWithPages(
        document: DocumentEntity,
        pagesFor: (documentId: Long) -> List<PageEntity>,
    ): Long {
        val documentId = insert(document)
        val pages = pagesFor(documentId)
        if (pages.isNotEmpty()) insertPages(pages)
        return documentId
    }

    @Query("UPDATE documents SET title = :title, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, updatedAt: Instant)

    @Query("UPDATE documents SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Instant)

    @Query("UPDATE documents SET ocr_status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateOcrStatus(id: Long, status: String, updatedAt: Instant)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)
}
