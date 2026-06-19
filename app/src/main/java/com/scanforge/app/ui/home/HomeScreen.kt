@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanforge.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.scanforge.designsystem.theme.SpaceGrotesk
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanforge.app.R
import com.scanforge.app.ui.util.formatBytes
import com.scanforge.core.domain.library.DocumentSort
import com.scanforge.core.domain.model.Document
import com.scanforge.core.domain.model.Folder
import com.scanforge.designsystem.components.SfBottomSheet
import com.scanforge.designsystem.components.SfEmptyState
import com.scanforge.designsystem.components.SfFab
import com.scanforge.designsystem.components.SfTextField

@Composable
fun HomeScreen(
    onDocumentClick: (Long) -> Unit,
    onScanClick: () -> Unit,
    onSearchClick: () -> Unit,
    onTrashClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onDocumentClick = onDocumentClick,
        onScanClick = onScanClick,
        onSearchClick = onSearchClick,
        onTrashClick = onTrashClick,
        onToggleView = viewModel::toggleViewMode,
        onSetSort = viewModel::setSort,
        onApplyFilter = viewModel::setFilter,
        onToggleFavorite = viewModel::toggleFavorite,
        onEnterSelection = viewModel::enterSelection,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onSelectAll = viewModel::selectAllVisible,
        onBatchTrash = viewModel::batchTrash,
        onBatchFavorite = viewModel::batchFavorite,
        onBatchMove = viewModel::batchMove,
        onCreateFolder = viewModel::createFolder,
        onClearFilters = viewModel::clearFilter,
        modifier = modifier,
    )
}

@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onDocumentClick: (Long) -> Unit,
    onScanClick: () -> Unit,
    onSearchClick: () -> Unit,
    onTrashClick: () -> Unit,
    onToggleView: () -> Unit,
    onSetSort: (DocumentSort) -> Unit,
    onApplyFilter: (com.scanforge.core.domain.library.DocumentFilter) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onEnterSelection: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onBatchTrash: () -> Unit,
    onBatchFavorite: () -> Unit,
    onBatchMove: (Long?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSort by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (state.inSelectionMode) {
                SelectionTopBar(
                    count = state.selection.count,
                    onClose = onClearSelection,
                    onSelectAll = onSelectAll,
                    onFavorite = onBatchFavorite,
                    onMove = { showMove = true },
                    onDelete = onBatchTrash,
                )
            } else {
                LibraryTopBar(
                    grid = state.viewMode == LibraryViewMode.Grid,
                    filterCount = state.filter.activeCount,
                    onToggleView = onToggleView,
                    onSort = { showSort = true },
                    onSearch = onSearchClick,
                    onFilter = { showFilter = true },
                    onTrash = onTrashClick,
                    onNewFolder = { showNewFolder = true },
                )
            }
        },
        floatingActionButton = {
            if (!state.inSelectionMode) {
                SfFab(
                    onClick = onScanClick,
                    icon = Icons.Filled.DocumentScanner,
                    contentDescription = stringResource(R.string.dest_scan),
                )
            }
        },
    ) { padding ->
        when {
            state.isEmpty -> SfEmptyState(
                icon = Icons.Filled.DocumentScanner,
                title = stringResource(R.string.home_empty_title),
                description = stringResource(R.string.home_empty_description),
                actionText = stringResource(R.string.home_scan_action),
                onAction = onScanClick,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            state.isFilteredEmpty -> SfEmptyState(
                icon = Icons.Outlined.FilterList,
                title = stringResource(R.string.library_filtered_empty_title),
                description = stringResource(R.string.library_filtered_empty_body),
                actionText = stringResource(R.string.library_clear_filters),
                onAction = onClearFilters,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> LibraryContent(
                state = state,
                padding = padding,
                onDocumentClick = onDocumentClick,
                onToggleFavorite = onToggleFavorite,
                onEnterSelection = onEnterSelection,
                onToggleSelection = onToggleSelection,
            )
        }
    }

    if (showSort) {
        SortSheet(current = state.sort, onPick = { onSetSort(it); showSort = false }, onDismiss = { showSort = false })
    }
    if (showNewFolder) {
        NewFolderDialog(onCreate = { onCreateFolder(it); showNewFolder = false }, onDismiss = { showNewFolder = false })
    }
    if (showMove) {
        MoveToFolderDialog(
            folders = state.folders,
            onPick = { onBatchMove(it); showMove = false },
            onDismiss = { showMove = false },
        )
    }
    if (showFilter) {
        FilterSheet(
            current = state.filter,
            tags = state.allTags,
            folders = state.folders,
            languages = state.allLanguages,
            onApply = { onApplyFilter(it); showFilter = false },
            onDismiss = { showFilter = false },
        )
    }
}

@Composable
private fun LibraryContent(
    state: HomeUiState,
    padding: PaddingValues,
    onDocumentClick: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onEnterSelection: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
) {
    val grid = state.viewMode == LibraryViewMode.Grid
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) 2 else 1),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        if (state.showSections && state.folders.isNotEmpty()) {
            fullSpan { SectionHeader(stringResource(R.string.section_folders)) }
            fullSpan { FolderRow(state.folders) }
        }
        if (state.showSections && state.favorites.isNotEmpty()) {
            fullSpan { SectionHeader(stringResource(R.string.section_favorites)) }
            fullSpan {
                DocumentCarousel(
                    documents = state.favorites,
                    onClick = onDocumentClick,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
        fullSpan {
            SectionHeader(stringResource(R.string.section_all), count = state.documents.size)
        }
        items(state.documents, key = { it.id }) { document ->
            val selected = state.selection.isSelected(document.id)
            if (grid) {
                DocumentGridCard(
                    document = document,
                    selected = selected,
                    selectionMode = state.inSelectionMode,
                    onClick = {
                        if (state.inSelectionMode) onToggleSelection(document.id) else onDocumentClick(document.id)
                    },
                    onLongClick = { onEnterSelection(document.id) },
                    onToggleFavorite = { onToggleFavorite(document.id, !document.isFavorite) },
                )
            } else {
                DocumentListRow(
                    document = document,
                    selected = selected,
                    selectionMode = state.inSelectionMode,
                    onClick = {
                        if (state.inSelectionMode) onToggleSelection(document.id) else onDocumentClick(document.id)
                    },
                    onLongClick = { onEnterSelection(document.id) },
                    onToggleFavorite = { onToggleFavorite(document.id, !document.isFavorite) },
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullSpan(
    content: @Composable () -> Unit,
) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun SectionHeader(title: String, count: Int? = null) {
    if (title.isBlank() && count == null) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = pluralStringResource(R.plurals.library_doc_count, count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FolderRow(folders: List<Folder>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        folders.forEach { folder -> FolderChip(folder) }
    }
}

@Composable
private fun FolderChip(folder: Folder) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.width(150.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = folder.colorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                    ?: MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    pluralStringResource(R.plurals.folder_doc_count, folder.documentCount, folder.documentCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DocumentCarousel(
    documents: List<Document>,
    onClick: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        documents.forEach { document ->
            Box(Modifier.width(150.dp)) {
                DocumentGridCard(
                    document = document,
                    selected = false,
                    selectionMode = false,
                    onClick = { onClick(document.id) },
                    onLongClick = {},
                    onToggleFavorite = { onToggleFavorite(document.id, !document.isFavorite) },
                )
            }
        }
    }
}

@Composable
private fun LibraryTopBar(
    grid: Boolean,
    filterCount: Int,
    onToggleView: () -> Unit,
    onSort: () -> Unit,
    onSearch: () -> Unit,
    onFilter: () -> Unit,
    onTrash: () -> Unit,
    onNewFolder: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    androidx.compose.material3.TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = SpaceGrotesk),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
        // Keep the three most-used actions inline; the rest live in an overflow menu so the title
        // never gets squeezed onto a second line.
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.cd_search))
            }
            IconButton(onClick = onFilter) {
                BadgedBox(badge = { if (filterCount > 0) Badge { Text("$filterCount") } }) {
                    Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.cd_filter))
                }
            }
            IconButton(onClick = onToggleView) {
                Icon(
                    if (grid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                    contentDescription = stringResource(if (grid) R.string.cd_view_list else R.string.cd_view_grid),
                )
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.cd_more_actions))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_title)) },
                    leadingIcon = { Icon(Icons.Outlined.Sort, contentDescription = null) },
                    onClick = { menuOpen = false; onSort() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folder_new)) },
                    leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                    onClick = { menuOpen = false; onNewFolder() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cd_open_trash)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    onClick = { menuOpen = false; onTrash() },
                )
            }
        },
    )
}

@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onFavorite: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    androidx.compose.material3.TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_exit_selection))
            }
        },
        title = { Text(pluralStringResource(R.plurals.selection_count, count, count)) },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Outlined.DoneAll, contentDescription = stringResource(R.string.sel_select_all))
            }
            IconButton(onClick = onFavorite) {
                Icon(Icons.Outlined.Star, contentDescription = stringResource(R.string.sel_favorite))
            }
            IconButton(onClick = onMove) {
                Icon(Icons.Outlined.DriveFileMove, contentDescription = stringResource(R.string.sel_move))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.sel_delete))
            }
        },
    )
}

@Composable
private fun SortSheet(current: DocumentSort, onPick: (DocumentSort) -> Unit, onDismiss: () -> Unit) {
    SfBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.sort_title)) {
        sortOptions().forEach { (sort, labelRes) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(sort) }
                    .padding(vertical = 14.dp),
            ) {
                Icon(
                    if (sort == current) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = if (sort == current) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (sort == current) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

private fun sortOptions(): List<Pair<DocumentSort, Int>> = listOf(
    DocumentSort.DateNewest to R.string.sort_date_newest,
    DocumentSort.DateOldest to R.string.sort_date_oldest,
    DocumentSort.NameAZ to R.string.sort_name_az,
    DocumentSort.NameZA to R.string.sort_name_za,
    DocumentSort.SizeLargest to R.string.sort_size_large,
    DocumentSort.SizeSmallest to R.string.sort_size_small,
)

@Composable
private fun NewFolderDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_new)) },
        text = {
            SfTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.folder_name_hint),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.folder_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.folder_cancel)) }
        },
    )
}

@Composable
private fun MoveToFolderDialog(folders: List<Folder>, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_folder_title)) },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 12.dp),
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.move_root), color = MaterialTheme.colorScheme.onSurface)
                }
                folders.forEach { folder ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(folder.id) }.padding(vertical = 12.dp),
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(folder.name, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.folder_cancel)) }
        },
    )
}

/** Shared favourite-star toggle. */
@Composable
internal fun FavoriteToggle(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier.size(32.dp)) {
        Icon(
            if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = stringResource(if (favorite) R.string.cd_unfavorite else R.string.cd_favorite),
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
