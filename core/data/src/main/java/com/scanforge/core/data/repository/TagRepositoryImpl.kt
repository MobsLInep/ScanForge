package com.scanforge.core.data.repository

import com.scanforge.core.common.dispatchers.Dispatcher
import com.scanforge.core.common.dispatchers.ScanForgeDispatcher
import com.scanforge.core.data.mapper.toDomain
import com.scanforge.core.database.dao.TagDao
import com.scanforge.core.database.entity.TagEntity
import com.scanforge.core.domain.model.Tag
import com.scanforge.core.domain.repository.TagRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
    private val ftsIndexer: FtsIndexer,
    @Dispatcher(ScanForgeDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : TagRepository {

    override fun observeTags(): Flow<List<Tag>> =
        tagDao.observeTags().map { list -> list.map(TagEntity::toDomain) }

    override suspend fun createTag(name: String, colorHex: String?): Long = withContext(ioDispatcher) {
        tagDao.insert(TagEntity(name = name, colorHex = colorHex))
    }

    override suspend fun deleteTag(id: Long) = withContext(ioDispatcher) {
        tagDao.delete(id)
    }

    override suspend fun setDocumentTags(documentId: Long, tagIds: Set<Long>) = withContext(ioDispatcher) {
        tagDao.setDocumentTags(documentId, tagIds)
        // Tag names contribute to the full-text index.
        ftsIndexer.reindex(documentId)
    }
}
