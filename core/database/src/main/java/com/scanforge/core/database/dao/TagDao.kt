package com.scanforge.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.scanforge.core.database.entity.DocumentTagCrossRef
import com.scanforge.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id IN (SELECT tag_id FROM document_tag_cross_ref WHERE document_id = :documentId)")
    fun observeTagsForDocument(documentId: Long): Flow<List<TagEntity>>

    /** One-shot tag names for a document, used to rebuild its full-text index row. */
    @Query("SELECT name FROM tags WHERE id IN (SELECT tag_id FROM document_tag_cross_ref WHERE document_id = :documentId)")
    suspend fun tagNamesForDocument(documentId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: DocumentTagCrossRef)

    @Query("DELETE FROM document_tag_cross_ref WHERE document_id = :documentId")
    suspend fun clearTagsForDocument(documentId: Long)

    /** Atomically replaces a document's tag set. */
    @Transaction
    suspend fun setDocumentTags(documentId: Long, tagIds: Set<Long>) {
        clearTagsForDocument(documentId)
        tagIds.forEach { tagId -> insertCrossRef(DocumentTagCrossRef(documentId, tagId)) }
    }
}
