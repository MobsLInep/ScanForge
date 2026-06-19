package com.scanforge.core.domain.model

import java.time.Instant

/**
 * A user-created collection that groups documents. Folders are nestable via [parentId]
 * (`null` = a top-level folder). [documentCount] is the number of *non-trashed* documents directly
 * inside this folder (not counting descendants) and is populated by list queries.
 */
data class Folder(
    val id: Long,
    val name: String,
    /** Parent folder id, or `null` when this folder lives at the library root. */
    val parentId: Long? = null,
    /** Optional `#RRGGBB` accent colour; `null` falls back to a theme default. */
    val colorHex: String? = null,
    val documentCount: Int = 0,
    val createdAt: Instant,
)
