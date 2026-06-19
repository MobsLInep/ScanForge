package com.scanforge.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanforge.designsystem.theme.ScanForgeTheme
import com.scanforge.designsystem.theme.hairline

/**
 * The base ScanForge container: 16dp corners, a layered shadow, and a 1px machined-edge hairline.
 * Pass [onClick] to make it interactive (adds ripple + button semantics).
 */
@Composable
fun SfCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = ScanForgeTheme.shapesExt.card
    val container = MaterialTheme.colorScheme.surfaceContainer
    val shadow = ScanForgeTheme.elevation.level1

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.hairline(shape),
            shape = shape,
            color = container,
            shadowElevation = shadow,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier.hairline(shape),
            shape = shape,
            color = container,
            shadowElevation = shadow,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Preview(name = "Card · Dark")
@Composable
private fun SfCardDarkPreview() = SfPreviewSurface(dark = true) {
    SfCard {
        Text("Invoice_2026_Q2.pdf", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("4 pages · 1.2 MB", style = ScanForgeTheme.mono.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(name = "Card · Light")
@Composable
private fun SfCardLightPreview() = SfPreviewSurface(dark = false) {
    SfCard {
        Text("Invoice_2026_Q2.pdf", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("4 pages · 1.2 MB", style = ScanForgeTheme.mono.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
