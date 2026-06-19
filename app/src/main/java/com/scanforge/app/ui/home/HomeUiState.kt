package com.scanforge.app.ui.home

import com.scanforge.core.domain.library.DocumentFilter
import com.scanforge.core.domain.library.DocumentSort
import com.scanforge.core.domain.model.Document
import com.scanforge.core.domain.model.Folder
import com.scanforge.core.domain.model.Tag
import com.scanforge.core.domain.selection.SelectionState

/** Grid or list presentation of the library. */
enum class LibraryViewMode { Grid, List }

/**
 * State for the document library home. Unlike the per-screen Loading/Empty/Error split used
 * elsewhere, the library keeps its toolbar and view controls visible at all times, so this is a
 * single data class with [loading]/empty flags rather than a sealed hierarchy. The Favourites and
 * Recent sections only appear when no filter is active and the user is not multi-selecting.
 */
data class HomeUiState(
    val loading: Boolean = true,
    /** Filtered + sorted documents that make up the main "All documents" list. */
    val documents: List<Document> = emptyList(),
    val favorites: List<Document> = emptyList(),
    val recent: List<Document> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val allLanguages: List<String> = emptyList(),
    val totalCount: Int = 0,
    val viewMode: LibraryViewMode = LibraryViewMode.Grid,
    val sort: DocumentSort = DocumentSort.DEFAULT,
    val filter: DocumentFilter = DocumentFilter.NONE,
    val selection: SelectionState = SelectionState.Idle,
) {
    val inSelectionMode: Boolean get() = selection.active

    /** True when the whole library is empty (not merely filtered to nothing). */
    val isEmpty: Boolean get() = !loading && totalCount == 0

    /** True when filters are active but match nothing. */
    val isFilteredEmpty: Boolean get() = !loading && totalCount > 0 && documents.isEmpty()

    /** Sections are only meaningful in the default, unfiltered, non-selecting view. */
    val showSections: Boolean
        get() = !filter.isActive && !inSelectionMode
}
