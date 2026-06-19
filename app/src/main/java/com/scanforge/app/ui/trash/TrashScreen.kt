@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanforge.app.ui.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanforge.app.R
import com.scanforge.app.ui.util.formatDate
import com.scanforge.core.domain.model.Document
import com.scanforge.designsystem.components.SfCard
import com.scanforge.designsystem.components.SfEmptyState
import com.scanforge.designsystem.components.SfTopBar

@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SfTopBar(
                title = stringResource(R.string.trash_title),
                onNavigationClick = onBack,
                actions = {
                    if (state.documents.isNotEmpty()) {
                        IconButton(onClick = viewModel::emptyTrash) {
                            Icon(
                                Icons.Outlined.DeleteSweep,
                                contentDescription = stringResource(R.string.trash_empty_action),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isEmpty) {
            SfEmptyState(
                icon = Icons.Outlined.DeleteForever,
                title = stringResource(R.string.trash_empty_title),
                description = stringResource(R.string.trash_empty_body),
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item {
                    Text(
                        stringResource(R.string.trash_retention_note, state.retentionDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(state.documents, key = { it.id }) { document ->
                    TrashRow(
                        document = document,
                        onRestore = { viewModel.restore(document.id) },
                        onDelete = { viewModel.deleteForever(document.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashRow(document: Document, onRestore: () -> Unit, onDelete: () -> Unit) {
    SfCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    pluralStringResource(R.plurals.home_page_count, document.pageCount, document.pageCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                document.deletedAt?.let {
                    Text(
                        stringResource(R.string.trash_deleted_on, it.formatDate()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Outlined.RestoreFromTrash,
                    contentDescription = stringResource(R.string.trash_restore),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteForever,
                    contentDescription = stringResource(R.string.trash_delete_forever),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
