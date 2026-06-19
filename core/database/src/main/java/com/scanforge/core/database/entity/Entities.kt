package com.scanforge.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A scanned document. [ocrStatus] is stored as the [com.scanforge.core.domain.model.OcrStatus] name;
 * timestamps go through the [com.scanforge.core.database.Converters] Instant↔epoch-millis converter.
 *
 * Organisation columns added in the document-manager phase: [isFavorite] (star), [folderId]
 * (collection membership, plain id so folder deletion can re-home documents in the repository),
 * [sizeBytes] (sum of page image bytes, kept current for size sorting), and [deletedAt] (soft-delete
 * tombstone — non-null means the document lives in the trash).
 */
@Entity(
    tableName = "documents",
    indices = [Index("folder_id"), Index("deleted_at")],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "ocr_status") val ocrStatus: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(name = "folder_id") val folderId: Long? = null,
    @ColumnInfo(name = "size_bytes", defaultValue = "0") val sizeBytes: Long = 0,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/**
 * A user-created collection. [parentId] makes folders nestable (`null` = root). It is a plain id (no
 * Room foreign key) so the repository can re-home children and documents when a folder is deleted
 * rather than cascading the delete.
 */
@Entity(
    tableName = "folders",
    indices = [Index("parent_id")],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    @ColumnInfo(name = "color_hex") val colorHex: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

/**
 * Full-text search index over documents. [rowId] mirrors the document id so results join straight
 * back to [DocumentEntity]. Rows are maintained by the data layer whenever a document's title, OCR
 * text or tags change (notably on OCR completion).
 */
@Fts4
@Entity(tableName = "document_fts")
data class DocumentFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val body: String,
    val tags: String,
)

/** A single page. Deleting its parent document cascades to its pages. */
@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("document_id")],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "document_id") val documentId: Long,
    @ColumnInfo(name = "page_order") val pageOrder: Int,
    @ColumnInfo(name = "original_image_path") val originalImagePath: String,
    @ColumnInfo(name = "processed_image_path") val processedImagePath: String? = null,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
    @ColumnInfo(name = "extracted_text") val extractedText: String? = null,
    val language: String? = null,
    @ColumnInfo(name = "ocr_status") val ocrStatus: String,
    /** JSON-serialized edit recipe (crop quad + enhancement params); `null` until enhanced. */
    @ColumnInfo(name = "processing_params") val processingParams: String? = null,
    /** JSON-serialized structured OCR output (block/line/word boxes + confidence); `null` until OCR'd. */
    @ColumnInfo(name = "ocr_blocks") val ocrBlocks: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/** A user-defined tag. Names are unique (case-sensitive at the storage layer). */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "color_hex") val colorHex: String? = null,
)

/** Many-to-many join between documents and tags. */
@Entity(
    tableName = "document_tag_cross_ref",
    primaryKeys = ["document_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tag_id")],
)
data class DocumentTagCrossRef(
    @ColumnInfo(name = "document_id") val documentId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)
