package com.scanforge.core.data.repository

import com.scanforge.core.database.dao.DocumentDao
import com.scanforge.core.database.dao.PageDao
import com.scanforge.core.database.dao.TagDao
import com.scanforge.core.database.entity.DocumentFtsEntity
import javax.inject.Inject

/**
 * Keeps the `document_fts` full-text index in sync with a document's searchable content: its title,
 * the OCR/edited text of every page, and its tag names. Called whenever any of those change — most
 * importantly on OCR completion. Centralised here so both the document and tag repositories reindex
 * through one path.
 */
class FtsIndexer @Inject constructor(
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val tagDao: TagDao,
) {
    /** Rebuilds (or removes) the FTS row for [documentId] from current content. */
    suspend fun reindex(documentId: Long) {
        val title = documentDao.getTitle(documentId)
        if (title == null) {
            documentDao.deleteFts(documentId)
            return
        }
        val body = pageDao.getPages(documentId)
            .joinToString(" ") { it.extractedText.orEmpty() }
            .trim()
        val tags = tagDao.tagNamesForDocument(documentId).joinToString(" ")
        documentDao.upsertFts(DocumentFtsEntity(rowId = documentId, title = title, body = body, tags = tags))
    }

    companion object {
        /**
         * Builds a safe FTS4 MATCH expression from raw user input: alphanumeric tokens turned into
         * AND-ed prefix queries (`foo* bar*`). Returns `null` when there is nothing to search for.
         */
        fun buildMatchQuery(query: String): String? {
            val tokens = query.lowercase()
                .split(Regex("[^\\p{L}\\p{Nd}]+"))
                .filter { it.isNotBlank() }
                .distinct()
            if (tokens.isEmpty()) return null
            return tokens.joinToString(" ") { "$it*" }
        }
    }
}
