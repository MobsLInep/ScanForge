@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanforge.app.ui.placeholder

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.scanforge.app.R
import com.scanforge.designsystem.components.SfEmptyState
import com.scanforge.designsystem.components.SfTopBar

/**
 * Generic "coming in a later phase" screen, reused by the not-yet-built destinations so the
 * navigation graph is fully wired and walkable today.
 */
@Composable
private fun PhasePlaceholder(
    title: String,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onNavigateUp: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SfTopBar(title = title, onNavigationClick = onNavigateUp) },
    ) { padding ->
        SfEmptyState(
            icon = icon,
            title = title,
            description = description,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PhasePlaceholder(
        title = stringResource(R.string.dest_settings),
        icon = Icons.Outlined.Settings,
        description = stringResource(R.string.placeholder_settings),
        modifier = modifier,
    )
}

@Composable
fun DocumentDetailScreen(
    documentId: Long,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PhasePlaceholder(
        title = stringResource(R.string.document_detail_title, documentId),
        icon = Icons.Filled.DocumentScanner,
        description = stringResource(R.string.placeholder_detail),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    )
}
