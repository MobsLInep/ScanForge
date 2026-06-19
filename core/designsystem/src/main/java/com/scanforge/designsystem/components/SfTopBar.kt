@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanforge.designsystem.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.scanforge.designsystem.theme.ScanForgeTheme
import com.scanforge.designsystem.theme.SpaceGrotesk

/**
 * ScanForge top app bar. Title is set in Space Grotesk for the technical brand voice. Supply
 * [onNavigationClick] for a back/up affordance, and [actions] for trailing icons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SfTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = Icons.AutoMirrored.Filled.ArrowBack,
    onNavigationClick: (() -> Unit)? = null,
    navigationContentDescription: String? = "Navigate up",
    centered: Boolean = true,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val titleContent: @Composable () -> Unit = {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = SpaceGrotesk),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val navContent: @Composable () -> Unit = {
        if (onNavigationClick != null && navigationIcon != null) {
            IconButton(onClick = onNavigationClick) {
                Icon(navigationIcon, contentDescription = navigationContentDescription)
            }
        }
    }
    val colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (centered) {
        CenterAlignedTopAppBar(
            title = titleContent,
            modifier = modifier,
            navigationIcon = navContent,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    } else {
        TopAppBar(
            title = titleContent,
            modifier = modifier,
            navigationIcon = navContent,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    }
}

@Preview(name = "TopBar · Dark")
@Composable
private fun SfTopBarDarkPreview() = SfPreviewSurface(dark = true) {
    SfTopBar(title = "ScanForge", onNavigationClick = {})
}
