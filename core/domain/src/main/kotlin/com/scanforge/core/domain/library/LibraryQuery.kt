package com.scanforge.core.domain.library

import com.scanforge.core.domain.model.Document
import com.scanforge.core.domain.model.OcrStatus
import java.time.Instant

/** How the document library is ordered. Covers the date / name / size sort axes. */
enum class DocumentSort {
    DateNewest,
    DateOldest,
    NameAZ,
    NameZA,
    SizeLargest,
    SizeSmallest,
    ;

    companion object {
        val DEFAULT = DateNewest
    }
}

/**
 * The set of constraints applied to the library list. An "empty" filter ([NONE]) matches every
 * non-trashed document. Combined predicates are AND-ed together.
 */
data class DocumentFilter(
    /** Document must carry *all* of these tag ids; empty = no tag constraint. */
    val tagIds: Set<Long> = emptySet(),
    /**
     * Restrict to a folder. `Unset` ignores folder; `Root` keeps only documents with no folder; an
     * `Id` keeps only documents directly in that folder.
     */
    val folder: FolderScope = FolderScope.Unset,
    /** Inclusive lower bound on the document's updated time. */
    val dateFrom: Instant? = null,
    /** Inclusive upper bound on the document's updated time. */
    val dateTo: Instant? = null,
    /** Restrict to documents at this aggregate OCR status; `null` = any. */
    val ocrStatus: OcrStatus? = null,
    /** Restrict to documents recognised in this language tag (e.g. `"Latin"`); `null` = any. */
    val language: String? = null,
) {
    val isActive: Boolean
        get() = tagIds.isNotEmpty() ||
            folder != FolderScope.Unset ||
            dateFrom != null ||
            dateTo != null ||
            ocrStatus != null ||
            language != null

    /** Number of distinct active constraints, for a filter-count badge. */
    val activeCount: Int
        get() = listOf(
            tagIds.isNotEmpty(),
            folder != FolderScope.Unset,
            dateFrom != null || dateTo != null,
            ocrStatus != null,
            language != null,
        ).count { it }

    /** True when [document] satisfies every active constraint. Trash state is handled upstream. */
    fun matches(document: Document): Boolean {
        if (tagIds.isNotEmpty() && !document.tags.map { it.id }.containsAll(tagIds)) return false
        when (val scope = folder) {
            FolderScope.Unset -> Unit
            FolderScope.Root -> if (document.folderId != null) return false
            is FolderScope.Id -> if (document.folderId != scope.value) return false
        }
        dateFrom?.let { if (document.updatedAt.isBefore(it)) return false }
        dateTo?.let { if (document.updatedAt.isAfter(it)) return false }
        ocrStatus?.let { if (document.ocrStatus != it) return false }
        language?.let { lang -> if (lang !in document.languages) return false }
        return true
    }

    companion object {
        val NONE = DocumentFilter()
    }
}

/** Selects which folder a [DocumentFilter] keeps. */
sealed interface FolderScope {
    /** Folder is not part of the filter. */
    data object Unset : FolderScope

    /** Only documents that are not inside any folder. */
    data object Root : FolderScope

    /** Only documents directly inside the folder with this id. */
    data class Id(val value: Long) : FolderScope
}

/**
 * Orders [documents] by [sort]. Favourites are *not* forced to the top here — that is the
 * presentation layer's "Favourites" section. Pure and deterministic for unit testing.
 */
fun List<Document>.sortedBy(sort: DocumentSort): List<Document> = when (sort) {
    DocumentSort.DateNewest -> sortedByDescending { it.updatedAt }
    DocumentSort.DateOldest -> sortedBy { it.updatedAt }
    DocumentSort.NameAZ -> sortedBy { it.title.lowercase() }
    DocumentSort.NameZA -> sortedByDescending { it.title.lowercase() }
    DocumentSort.SizeLargest -> sortedByDescending { it.sizeBytes }
    DocumentSort.SizeSmallest -> sortedBy { it.sizeBytes }
}
