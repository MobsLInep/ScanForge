@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanforge.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.scanforge.app.R
import com.scanforge.app.ui.export.ExportSheet
import com.scanforge.app.ui.util.formatBytes
import com.scanforge.app.ui.util.formatDateTime
import com.scanforge.core.domain.model.Document
import com.scanforge.core.domain.model.Folder
import com.scanforge.core.domain.model.OcrStatus
import com.scanforge.core.domain.model.Page
import com.scanforge.designsystem.components.SfCard
import com.scanforge.designsystem.components.SfEmptyState
import com.scanforge.designsystem.components.SfTextField
import com.scanforge.designsystem.components.SfTopBar
import com.scanforge.designsystem.motion.processingPulse
import com.scanforge.designsystem.theme.ScanForgeTheme
import java.io.File

/**
 * Document detail: an ordered, reorderable list of page cards. Tapping a page opens the editor;
 * long-press the drag handle to reorder; each card can be deleted. Page order is persisted on drop.
 */
@Composable
fun DocumentDetailScreen(
    onNavigateUp: () -> Unit,
    onPageClick: (pageId: Long) -> Unit,
    onPageOcrClick: (pageId: Long) -> Unit,
    onPreviewPdf: (filePath: String) -> Unit,
    onDocumentDuplicated: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DocumentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExport by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SfTopBar(
                title = state.title.ifEmpty { stringResource(R.string.editor_title) },
                onNavigationClick = onNavigateUp,
                actions = {
                    if (state.document != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                if (state.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = stringResource(
                                    if (state.isFavorite) R.string.cd_unfavorite else R.string.cd_favorite,
                                ),
                                tint = if (state.isFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    if (state.pages.isNotEmpty()) {
                        IconButton(
                            onClick = viewModel::runOcr,
                            enabled = !state.ocrRunning,
                            modifier = Modifier.testTag(DetailTestTags.RUN_OCR),
                        ) {
                            Icon(
                                Icons.Outlined.TextFields,
                                contentDescription = stringResource(
                                    if (state.ocrComplete) R.string.detail_rerun_ocr
                                    else R.string.detail_run_ocr,
                                ),
                            )
                        }
                        IconButton(
                            onClick = { showExport = true },
                            modifier = Modifier.testTag(DetailTestTags.EXPORT),
                        ) {
                            Icon(
                                Icons.Outlined.IosShare,
                                contentDescription = stringResource(R.string.export_action),
                            )
                        }
                    }
                    DetailOverflowMenu(
                        onRename = { showRename = true },
                        onDuplicate = { viewModel.duplicate(onDocumentDuplicated) },
                        onMove = { showMove = true },
                        onTrash = {
                            viewModel.moveToTrash()
                            onNavigateUp()
                        },
                    )
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.ocrRunning) {
                OcrProgressBar(done = state.ocrDone, total = state.ocrTotal)
            }
            if (!state.loading && state.pages.isEmpty()) {
                SfEmptyState(
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.detail_empty_title),
                    description = stringResource(R.string.detail_empty_body),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ReorderablePageList(
                    pages = state.pages,
                    metadata = state.document,
                    onPageClick = onPageClick,
                    onPageOcrClick = onPageOcrClick,
                    onDelete = viewModel::deletePage,
                    onReorder = viewModel::reorder,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showExport) {
        ExportSheet(
            onDismiss = { showExport = false },
            onPreview = { path ->
                showExport = false
                onPreviewPdf(path)
            },
        )
    }
    if (showRename) {
        RenameDialog(
            initial = state.title,
            onConfirm = { viewModel.rename(it); showRename = false },
            onDismiss = { showRename = false },
        )
    }
    if (showMove) {
        DetailMoveDialog(
            folders = state.folders,
            onPick = { viewModel.moveToFolder(it); showMove = false },
            onDismiss = { showMove = false },
        )
    }
}

@Composable
private fun DetailOverflowMenu(
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_actions))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.detail_rename)) },
            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
            onClick = { expanded = false; onRename() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.detail_duplicate)) },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
            onClick = { expanded = false; onDuplicate() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.detail_move)) },
            leadingIcon = { Icon(Icons.Outlined.DriveFileMove, contentDescription = null) },
            onClick = { expanded = false; onMove() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.detail_delete)) },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            onClick = { expanded = false; onTrash() },
        )
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_rename_title)) },
        text = { SfTextField(value = name, onValueChange = { name = it }) },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.detail_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.folder_cancel)) } },
    )
}

@Composable
private fun DetailMoveDialog(folders: List<Folder>, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.folder_cancel)) } },
    )
}

@Composable
private fun MetadataCard(document: Document) {
    SfCard {
        Text(
            stringResource(R.string.detail_metadata_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(8.dp))
        MetaRow(stringResource(R.string.meta_pages), document.pageCount.toString())
        MetaRow(stringResource(R.string.meta_size), formatBytes(document.sizeBytes))
        MetaRow(stringResource(R.string.meta_created), document.createdAt.formatDateTime())
        MetaRow(stringResource(R.string.meta_modified), document.updatedAt.formatDateTime())
        MetaRow(
            stringResource(R.string.meta_languages),
            document.languages.takeIf { it.isNotEmpty() }?.joinToString(", ")
                ?: stringResource(R.string.meta_none),
        )
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun OcrProgressBar(done: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.detail_ocr_progress, done, total),
            style = MaterialTheme.typography.labelMedium,
            color = ScanForgeTheme.colors.processing,
        )
        Spacer(Modifier.size(6.dp))
        if (total > 0) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
                color = ScanForgeTheme.colors.processing,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = ScanForgeTheme.colors.processing,
            )
        }
    }
}

@Composable
private fun ReorderablePageList(
    pages: List<Page>,
    metadata: Document?,
    onPageClick: (Long) -> Unit,
    onPageOcrClick: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local working order; resyncs whenever the persisted list changes (after a committed reorder).
    var order by remember(pages) { mutableStateOf(pages) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        metadata?.let { item(key = "metadata") { MetadataCard(it) } }
        item {
            Text(
                text = stringResource(R.string.detail_reorder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(order, key = { it.id }) { page ->
            val index = order.indexOfFirst { it.id == page.id }
            val isDragging = page.id == draggingId
            PageCard(
                page = page,
                position = index + 1,
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f }
                    .onSizeChanged { if (it.height > 0) itemHeightPx = it.height.toFloat() }
                    .then(if (!isDragging) Modifier.animateItem() else Modifier),
                onClick = { onPageClick(page.id) },
                onOcrClick = { onPageOcrClick(page.id) },
                onDelete = { onDelete(page.id) },
                dragHandleModifier = Modifier.pointerInput(order) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggingId = page.id; dragOffsetY = 0f },
                        onDragEnd = {
                            draggingId = null
                            dragOffsetY = 0f
                            onReorder(order.map { it.id })
                        },
                        onDragCancel = { draggingId = null; dragOffsetY = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        dragOffsetY += dragAmount.y
                        val current = order.indexOfFirst { it.id == draggingId }
                        if (current < 0 || itemHeightPx <= 0f) return@detectDragGesturesAfterLongPress
                        val threshold = itemHeightPx / 2f
                        if (dragOffsetY > threshold && current < order.lastIndex) {
                            order = order.toMutableList().apply { add(current + 1, removeAt(current)) }
                            dragOffsetY -= itemHeightPx
                        } else if (dragOffsetY < -threshold && current > 0) {
                            order = order.toMutableList().apply { add(current - 1, removeAt(current)) }
                            dragOffsetY += itemHeightPx
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun PageCard(
    page: Page,
    position: Int,
    onClick: () -> Unit,
    onOcrClick: () -> Unit,
    onDelete: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val enhanced = page.processedImagePath != null || page.processing != null
    val thumb = page.thumbnailPath ?: page.processedImagePath ?: page.originalImagePath
    val openLabel = stringResource(R.string.cd_open_page, position)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClickLabel = openLabel, onClick = onClick)
                .padding(12.dp)
                .testTag("${DetailTestTags.PAGE_PREFIX}${page.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                AsyncImage(
                    model = File(thumb),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp, 72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (page.ocrStatus == OcrStatus.InProgress) {
                                Modifier.processingPulse(active = true)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.cd_open_page, position),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.size(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OcrStatusChip(page.ocrStatus)
                    if (enhanced) {
                        Spacer(Modifier.width(6.dp))
                        EnhancedBadge()
                    }
                }
            }
            IconButton(
                onClick = onOcrClick,
                modifier = Modifier.testTag("${DetailTestTags.OCR_PREFIX}${page.id}"),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Notes,
                    contentDescription = stringResource(R.string.cd_open_text, position),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.cd_delete_page, position),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = dragHandleModifier
                    .size(44.dp)
                    .semantics { contentDescription = openLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EnhancedBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = ScanForgeTheme.colors.success.copy(alpha = 0.18f),
    ) {
        Text(
            text = stringResource(R.string.page_badge_enhanced),
            style = MaterialTheme.typography.labelSmall,
            color = ScanForgeTheme.colors.success,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** A compact pill reflecting a page's OCR lifecycle state, colour-coded by outcome. */
@Composable
private fun OcrStatusChip(status: OcrStatus) {
    val (labelRes, color) = when (status) {
        OcrStatus.NotStarted -> R.string.ocr_status_none to MaterialTheme.colorScheme.onSurfaceVariant
        OcrStatus.Queued -> R.string.ocr_status_queued to ScanForgeTheme.colors.processing
        OcrStatus.InProgress -> R.string.ocr_status_processing to ScanForgeTheme.colors.processing
        OcrStatus.Completed -> R.string.ocr_status_done to ScanForgeTheme.colors.success
        OcrStatus.Failed -> R.string.ocr_status_failed to ScanForgeTheme.colors.warning
    }
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.16f)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

internal object DetailTestTags {
    const val PAGE_PREFIX = "detail_page_"
    const val OCR_PREFIX = "detail_ocr_"
    const val RUN_OCR = "detail_run_ocr"
    const val EXPORT = "detail_export"
}
