package com.scanforge.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.scanforge.core.database.entity.FolderEntity
import com.scanforge.core.database.entity.FolderWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    /** All folders with their direct non-trashed document counts, alphabetical. */
    @Query(
        """
        SELECT f.*, (
            SELECT COUNT(*) FROM documents d
            WHERE d.folder_id = f.id AND d.deleted_at IS NULL
        ) AS doc_count
        FROM folders f
        ORDER BY f.name COLLATE NOCASE ASC
        """,
    )
    fun observeFolders(): Flow<List<FolderWithCount>>

    /** Direct children of [parentId] (`null` = root-level folders). */
    @Query(
        """
        SELECT f.*, (
            SELECT COUNT(*) FROM documents d
            WHERE d.folder_id = f.id AND d.deleted_at IS NULL
        ) AS doc_count
        FROM folders f
        WHERE (:parentId IS NULL AND f.parent_id IS NULL) OR f.parent_id = :parentId
        ORDER BY f.name COLLATE NOCASE ASC
        """,
    )
    fun observeChildFolders(parentId: Long?): Flow<List<FolderWithCount>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolder(id: Long): FolderEntity?

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE folders SET color_hex = :colorHex WHERE id = :id")
    suspend fun setColor(id: Long, colorHex: String?)

    /** Re-homes child folders of [oldParentId] under [newParentId] (used when deleting a folder). */
    @Query("UPDATE folders SET parent_id = :newParentId WHERE parent_id = :oldParentId")
    suspend fun reassignParent(oldParentId: Long, newParentId: Long?)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)
}
