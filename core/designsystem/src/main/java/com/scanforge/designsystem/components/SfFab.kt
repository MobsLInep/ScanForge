package com.scanforge.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanforge.designsystem.theme.ScanForgeTheme

/**
 * The amber ScanForge FAB. Pass [text] (and keep it non-null) to render the extended variant; the
 * label collapses/expands with motion when [expanded] toggles (e.g. on scroll).
 */
@Composable
fun SfFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    expanded: Boolean = true,
) {
    if (text == null) {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            shape = ScanForgeTheme.shapesExt.card,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(icon, contentDescription = contentDescription)
        }
    } else {
        ExtendedFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            shape = ScanForgeTheme.shapesExt.card,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = if (expanded) null else contentDescription)
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(ScanForgeTheme.motion.tweenStandard()) +
                        expandHorizontally(ScanForgeTheme.motion.tweenStandard()),
                    exit = fadeOut(ScanForgeTheme.motion.tweenQuick()) +
                        shrinkHorizontally(ScanForgeTheme.motion.tweenQuick()),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        Text(text, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Preview(name = "FAB · Dark")
@Composable
private fun SfFabDarkPreview() = SfPreviewSurface(dark = true) {
    SfFab(
        icon = Icons.Filled.DocumentScanner,
        contentDescription = "Scan document",
        onClick = {},
        text = "Scan",
    )
}
