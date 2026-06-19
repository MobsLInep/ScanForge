@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.scanforge.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanforge.app.R
import com.scanforge.core.domain.library.SearchResult
import com.scanforge.core.domain.library.SearchSnippet
import com.scanforge.designsystem.components.SfCard
import com.scanforge.designsystem.components.SfChip
import com.scanforge.designsystem.components.SfEmptyState
import com.scanforge.designsystem.components.SfTextField

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onResultClick: (documentId: Long) -> Unit,
    onJumpToPage: (pageId: Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            androidx.compose.material3.TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = {
                    SfTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = stringResource(R.string.search_hint),
                        leadingIcon = Icons.Outlined.Search,
                        trailingIcon = if (state.hasQuery) Icons.Filled.Close else null,
                        trailingIconDescription = stringResource(R.string.cd_clear_search),
                        onTrailingClick = viewModel::clear,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        },
    ) { padding ->
        when {
            !state.hasQuery -> SfEmptyState(
                icon = Icons.Outlined.Search,
                title = stringResource(R.string.search_empty_title),
                description = stringResource(R.string.search_empty_body),
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            state.noResults -> SfEmptyState(
                icon = Icons.Outlined.Search,
                title = stringResource(R.string.search_no_results_title),
                description = stringResource(R.string.search_no_results_body, state.query),
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(state.results, key = { it.document.id }) { result ->
                    SearchResultCard(result, onResultClick, onJumpToPage)
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    onResultClick: (Long) -> Unit,
    onJumpToPage: (Long) -> Unit,
) {
    SfCard(onClick = { onResultClick(result.document.id) }) {
        Text(
            result.document.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (result.matchingPageIndices.isNotEmpty()) {
            Text(
                pluralStringResource(
                    R.plurals.search_match_count,
                    result.matchingPageIndices.size,
                    result.matchingPageIndices.size,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        result.snippet?.let { snippet ->
            Spacer(Modifier.height(8.dp))
            Text(
                highlight(snippet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            // Jump-to-page: open the page that contains the best match.
            SfChip(
                label = stringResource(R.string.search_jump_page, snippet.pageIndex + 1),
                selected = false,
                onClick = { onJumpToPage(snippet.pageId) },
            )
        }
    }
}

@Composable
private fun highlight(snippet: SearchSnippet) = buildAnnotatedString {
    val text = snippet.text
    var cursor = 0
    val highlightStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    snippet.highlights.sortedBy { it.start }.forEach { h ->
        val start = h.start.coerceIn(0, text.length)
        val end = h.endExclusive.coerceIn(start, text.length)
        if (start > cursor) append(text.substring(cursor, start))
        withStyle(highlightStyle) { append(text.substring(start, end)) }
        cursor = end
    }
    if (cursor < text.length) append(text.substring(cursor))
}
