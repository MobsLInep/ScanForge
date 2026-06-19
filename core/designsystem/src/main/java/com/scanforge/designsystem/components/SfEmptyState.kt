package com.scanforge.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanforge.designsystem.theme.ScanForgeTheme

/**
 * The visual for the `Empty` arm of a UI state. Centered icon-in-a-disc, title, description, and
 * an optional primary action.
 */
@Composable
fun SfEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ScanForgeTheme.colors.brand,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            SfButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Preview(name = "EmptyState · Dark")
@Composable
private fun SfEmptyStateDarkPreview() = SfPreviewSurface(dark = true) {
    SfEmptyState(
        icon = Icons.Filled.DocumentScanner,
        title = "No documents yet",
        description = "Scan your first page to forge a crisp, searchable PDF.",
        actionText = "Scan document",
        onAction = {},
    )
}

@Preview(name = "EmptyState · Light")
@Composable
private fun SfEmptyStateLightPreview() = SfPreviewSurface(dark = false) {
    SfEmptyState(
        icon = Icons.Filled.DocumentScanner,
        title = "No documents yet",
        description = "Scan your first page to forge a crisp, searchable PDF.",
        actionText = "Scan document",
        onAction = {},
    )
}
