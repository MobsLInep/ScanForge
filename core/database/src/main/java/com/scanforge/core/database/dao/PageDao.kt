package com.scanforge.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.scanforge.core.database.entity.PageEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface PageDao {

    @Query("SELECT * FROM pages WHERE document_id = :documentId ORDER BY page_order ASC")
    fun observePages(documentId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE id = :id")
    fun observePage(id: Long): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPage(id: Long): PageEntity?

    /** One-shot ordered snapshot, used when re-packing order after a delete. */
    @Query("SELECT * FROM pages WHERE document_id = :documentId ORDER BY page_order ASC")
    suspend fun getPages(documentId: Long): List<PageEntity>

    @Query("SELECT document_id FROM pages WHERE id = :id")
    suspend fun documentIdOf(id: Long): Long?

    @Query("SELECT COUNT(*) FROM pages WHERE document_id = :documentId")
    suspend fun countPages(documentId: Long): Int

    @Insert
    suspend fun insert(page: PageEntity): Long

    @Insert
    suspend fun insertAll(pages: List<PageEntity>): List<Long>

    @Update
    suspend fun update(page: PageEntity)

    @Query("UPDATE pages SET page_order = :order, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateOrder(id: Long, order: Int, updatedAt: Instant)

    /** Records a (re-)processing result without touching the archived original. */
    @Query(
        """
        UPDATE pages
        SET processed_image_path = :processedImagePath,
            thumbnail_path = :thumbnailPath,
            processing_params = :processingParams,
            updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateProcessing(
        id: Long,
        processedImagePath: String,
        thumbnailPath: String,
        processingParams: String,
        updatedAt: Instant,
    )

    // ── OCR ──────────────────────────────────────────────────────────────────────────────────────

    /** Sets a page's OCR lifecycle state (PENDING/PROCESSING/etc.) without touching its content. */
    @Query("UPDATE pages SET ocr_status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateOcrStatus(id: Long, status: String, updatedAt: Instant)

    /** Queues a page for OCR with a chosen language selection tag. */
    @Query(
        "UPDATE pages SET ocr_status = :status, language = :language, updated_at = :updatedAt WHERE id = :id",
    )
    suspend fun updateOcrSelection(id: Long, status: String, language: String, updatedAt: Instant)

    /** Records a completed OCR result: structured blocks + mirrored plain text + Completed status. */
    @Query(
        """
        UPDATE pages
        SET extracted_text = :extractedText,
            ocr_blocks = :ocrBlocks,
            ocr_status = :status,
            updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateOcrResult(
        id: Long,
        extractedText: String,
        ocrBlocks: String,
        status: String,
        updatedAt: Instant,
    )

    /** Saves a user's inline correction of the recognised text. */
    @Query("UPDATE pages SET extracted_text = :text, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateExtractedText(id: Long, text: String, updatedAt: Instant)

    /** OCR statuses of every page in a document, used to recompute the document's aggregate status. */
    @Query("SELECT ocr_status FROM pages WHERE document_id = :documentId")
    suspend fun ocrStatuses(documentId: Long): List<String>

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun delete(id: Long)
}
