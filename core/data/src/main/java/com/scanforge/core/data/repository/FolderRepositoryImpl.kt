package com.scanforge.core.data.repository

import com.scanforge.core.common.dispatchers.Dispatcher
import com.scanforge.core.common.dispatchers.ScanForgeDispatcher
import com.scanforge.core.data.mapper.toDomain
import com.scanforge.core.database.dao.DocumentDao
import com.scanforge.core.database.dao.FolderDao
import com.scanforge.core.database.entity.FolderEntity
import com.scanforge.core.domain.model.Folder
import com.scanforge.core.domain.repository.FolderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
    private val documentDao: DocumentDao,
    @Dispatcher(ScanForgeDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : FolderRepository {

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeFolders().map { list -> list.map { it.toDomain() } }

    override fun observeChildFolders(parentId: Long?): Flow<List<Folder>> =
        folderDao.observeChildFolders(parentId).map { list -> list.map { it.toDomain() } }

    override suspend fun createFolder(
        name: String,
        parentId: Long?,
        colorHex: String?,
    ): Long = withContext(ioDispatcher) {
        folderDao.insert(
            FolderEntity(name = name, parentId = parentId, colorHex = colorHex, createdAt = Instant.now()),
        )
    }

    override suspend fun renameFolder(id: Long, name: String) = withContext(ioDispatcher) {
        folderDao.rename(id, name)
    }

    override suspend fun setFolderColor(id: Long, colorHex: String?) = withContext(ioDispatcher) {
        folderDao.setColor(id, colorHex)
    }

    override suspend fun deleteFolder(id: Long) = withContext(ioDispatcher) {
        // Re-home the folder's documents and child folders to its parent (root if none), then delete.
        val parent = folderDao.getFolder(id)?.parentId
        documentDao.reassignFolder(oldFolderId = id, newFolderId = parent)
        folderDao.reassignParent(oldParentId = id, newParentId = parent)
        folderDao.delete(id)
    }
}
