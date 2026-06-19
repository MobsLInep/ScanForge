package com.scanforge.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * A document together with its ordered pages and associated tags. Returned by read queries so the
 * data layer can build a fully-populated [com.scanforge.core.domain.model.Document] in one shot.
 */
data class PopulatedDocument(
    @Embedded val document: DocumentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "document_id",
    )
    val pages: List<PageEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = DocumentTagCrossRef::class,
            parentColumn = "document_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<TagEntity>,
)

/** A folder together with the number of non-trashed documents directly inside it. */
data class FolderWithCount(
    @Embedded val folder: FolderEntity,
    @ColumnInfo(name = "doc_count") val docCount: Int,
)
