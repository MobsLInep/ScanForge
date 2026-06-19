@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.scanforge.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanforge.app.R
import com.scanforge.core.domain.library.DocumentFilter
import com.scanforge.core.domain.library.FolderScope
import com.scanforge.core.domain.model.Folder
import com.scanforge.core.domain.model.OcrStatus
import com.scanforge.core.domain.model.Tag
import com.scanforge.designsystem.components.SfBottomSheet
import com.scanforge.designsystem.components.SfButton
import com.scanforge.designsystem.components.SfButtonVariant
import com.scanforge.designsystem.components.SfChip
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
internal fun FilterSheet(
    current: DocumentFilter,
    tags: List<Tag>,
    folders: List<Folder>,
    languages: List<String>,
    onApply: (DocumentFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    val now = remember { Instant.now() }

    SfBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.filter_title)) {
        if (tags.isNotEmpty()) {
            FilterGroup(stringResource(R.string.filter_tags_label)) {
                tags.forEach { tag ->
                    SfChip(
                        label = tag.name,
                        selected = tag.id in draft.tagIds,
                        onClick = {
                            draft = draft.copy(
                                tagIds = if (tag.id in draft.tagIds) draft.tagIds - tag.id else draft.tagIds + tag.id,
                            )
                        },
                    )
                }
            }
        }

        if (folders.isNotEmpty()) {
            FilterGroup(stringResource(R.string.filter_folder_label)) {
                SfChip(
                    label = stringResource(R.string.filter_any),
                    selected = draft.folder == FolderScope.Unset,
                    onClick = { draft = draft.copy(folder = FolderScope.Unset) },
                )
                SfChip(
                    label = stringResource(R.string.filter_root_only),
                    selected = draft.folder == FolderScope.Root,
                    onClick = { draft = draft.copy(folder = FolderScope.Root) },
                )
                folders.forEach { folder ->
                    SfChip(
                        label = folder.name,
                        selected = (draft.folder as? FolderScope.Id)?.value == folder.id,
                        onClick = { draft = draft.copy(folder = FolderScope.Id(folder.id)) },
                    )
                }
            }
        }

        FilterGroup(stringResource(R.string.filter_ocr_label)) {
            SfChip(
                label = stringResource(R.string.filter_any),
                selected = draft.ocrStatus == null,
                onClick = { draft = draft.copy(ocrStatus = null) },
            )
            SfChip(
                label = stringResource(R.string.ocr_status_completed),
                selected = draft.ocrStatus == OcrStatus.Completed,
                onClick = { draft = draft.copy(ocrStatus = OcrStatus.Completed) },
            )
            SfChip(
                label = stringResource(R.string.ocr_status_pending),
                selected = draft.ocrStatus == OcrStatus.NotStarted,
                onClick = { draft = draft.copy(ocrStatus = OcrStatus.NotStarted) },
            )
        }

        if (languages.isNotEmpty()) {
            FilterGroup(stringResource(R.string.filter_language_label)) {
                SfChip(
                    label = stringResource(R.string.filter_any),
                    selected = draft.language == null,
                    onClick = { draft = draft.copy(language = null) },
                )
                languages.forEach { lang ->
                    SfChip(
                        label = lang,
                        selected = draft.language == lang,
                        onClick = { draft = draft.copy(language = lang) },
                    )
                }
            }
        }

        FilterGroup(stringResource(R.string.filter_date_label)) {
            SfChip(
                label = stringResource(R.string.filter_any),
                selected = draft.dateFrom == null,
                onClick = { draft = draft.copy(dateFrom = null, dateTo = null) },
            )
            DateChip(R.string.filter_date_7, draft, now, 7) { draft = it }
            DateChip(R.string.filter_date_30, draft, now, 30) { draft = it }
            DateChip(R.string.filter_date_year, draft, now, 365) { draft = it }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SfButton(
                text = stringResource(R.string.filter_clear_all),
                onClick = { draft = DocumentFilter.NONE },
                variant = SfButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            SfButton(
                text = stringResource(R.string.filter_apply),
                onClick = { onApply(draft) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DateChip(
    labelRes: Int,
    draft: DocumentFilter,
    now: Instant,
    days: Long,
    onChange: (DocumentFilter) -> Unit,
) {
    val from = now.minus(days, ChronoUnit.DAYS)
    SfChip(
        label = stringResource(labelRes),
        selected = draft.dateFrom == from,
        onClick = { onChange(draft.copy(dateFrom = from, dateTo = null)) },
    )
}

@Composable
private fun FilterGroup(label: String, content: @Composable FlowRowScope.() -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

private typealias FlowRowScope = androidx.compose.foundation.layout.FlowRowScope
