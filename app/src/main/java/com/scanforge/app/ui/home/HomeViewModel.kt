package com.scanforge.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanforge.core.domain.library.DocumentFilter
import com.scanforge.core.domain.library.DocumentSort
import com.scanforge.core.domain.repository.DocumentRepository
import com.scanforge.core.domain.repository.FolderRepository
import com.scanforge.core.domain.repository.SettingsRepository
import com.scanforge.core.domain.repository.TagRepository
import com.scanforge.core.domain.selection.SelectionAction
import com.scanforge.core.domain.selection.SelectionState
import com.scanforge.core.domain.selection.reduce
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Drives the document library home: sort, filter, grid/list mode, the Favourites/Recent sections,
 * folders, and the multi-select (batch) contextual mode. The main list reacts to [sortState] and
 * [filterState] via `flatMapLatest`; sections and folder counts come from the full, unfiltered
 * stream. On creation it auto-purges trash older than the user's retention setting.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val folderRepository: FolderRepository,
    private val tagRepository: TagRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val sortState = MutableStateFlow(DocumentSort.DEFAULT)
    private val filterState = MutableStateFlow(DocumentFilter.NONE)
    private val viewModeState = MutableStateFlow(LibraryViewMode.Grid)
    private val selectionState = MutableStateFlow(SelectionState.Idle)

    private val mainList = combine(sortState, filterState) { sort, filter -> sort to filter }
        .flatMapLatest { (sort, filter) -> repository.observeLibrary(sort, filter) }

    private val controls = combine(
        sortState,
        filterState,
        viewModeState,
        selectionState,
    ) { sort, filter, view, selection -> Controls(sort, filter, view, selection) }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeDocuments(),
        mainList,
        folderRepository.observeFolders(),
        tagRepository.observeTags(),
        controls,
    ) { all, list, folders, tags, c ->
        HomeUiState(
            loading = false,
            documents = list,
            favorites = all.filter { it.isFavorite },
            recent = all.take(RECENT_LIMIT),
            folders = folders,
            allTags = tags,
            allLanguages = all.flatMap { it.languages }.distinct(),
            totalCount = all.size,
            viewMode = c.view,
            sort = c.sort,
            filter = c.filter,
            selection = c.selection,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        purgeExpiredTrash()
    }

    // ── View controls ─────────────────────────────────────────────────────────────────────────────

    fun setSort(sort: DocumentSort) = sortState.update { sort }

    fun setFilter(filter: DocumentFilter) = filterState.update { filter }

    fun clearFilter() = filterState.update { DocumentFilter.NONE }

    fun toggleViewMode() = viewModeState.update {
        if (it == LibraryViewMode.Grid) LibraryViewMode.List else LibraryViewMode.Grid
    }

    // ── Favourites ────────────────────────────────────────────────────────────────────────────────

    fun toggleFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(id, favorite) }
    }

    // ── Multi-select ──────────────────────────────────────────────────────────────────────────────

    fun enterSelection(id: Long) = dispatch(SelectionAction.Enter(id))
    fun toggleSelection(id: Long) = dispatch(SelectionAction.Toggle(id))
    fun clearSelection() = dispatch(SelectionAction.Clear)
    fun selectAllVisible() = dispatch(SelectionAction.SelectAll(uiState.value.documents.map { it.id }))

    private fun dispatch(action: SelectionAction) = selectionState.update { it.reduce(action) }

    fun batchTrash() = withSelection { ids ->
        repository.moveToTrash(ids)
        clearSelection()
    }

    fun batchFavorite() = withSelection { ids ->
        ids.forEach { repository.setFavorite(it, true) }
        clearSelection()
    }

    fun batchMove(folderId: Long?) = withSelection { ids ->
        repository.moveToFolder(ids, folderId)
        clearSelection()
    }

    private fun withSelection(block: suspend (Set<Long>) -> Unit) {
        val ids = selectionState.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch { block(ids) }
    }

    // ── Folders ───────────────────────────────────────────────────────────────────────────────────

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { folderRepository.createFolder(trimmed) }
    }

    private fun purgeExpiredTrash() {
        viewModelScope.launch {
            val days = settingsRepository.observeSettings().first().trashRetentionDays
            val threshold = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
            repository.purgeTrashedBefore(threshold)
        }
    }

    private data class Controls(
        val sort: DocumentSort,
        val filter: DocumentFilter,
        val view: LibraryViewMode,
        val selection: SelectionState,
    )

    private companion object {
        const val RECENT_LIMIT = 6
    }
}
