package com.scanforge.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanforge.designsystem.theme.ScanForgeTheme

enum class SfButtonVariant { Primary, Secondary, Ghost }

/**
 * The ScanForge button. 12dp corners, 48dp minimum touch target (a11y), and an inline [loading]
 * state that swaps the label for a spinner while keeping the button's footprint.
 *
 *  - [SfButtonVariant.Primary]   filled amber — the single most important action on a surface
 *  - [SfButtonVariant.Secondary] teal outline — supporting actions
 *  - [SfButtonVariant.Ghost]     text-only — low-emphasis / tertiary actions
 */
@Composable
fun SfButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SfButtonVariant = SfButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val shape = ScanForgeTheme.shapesExt.button
    val contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    val sizing = modifier.defaultMinSize(minHeight = 48.dp)
    val content: @Composable () -> Unit = { ButtonContent(text, loading, leadingIcon) }

    when (variant) {
        SfButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = sizing,
            enabled = enabled && !loading,
            shape = shape,
            colors = primaryColors(),
            contentPadding = contentPadding,
        ) { content() }

        SfButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = sizing,
            enabled = enabled && !loading,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.secondary,
            ),
            border = BorderStroke(
                1.dp,
                if (enabled) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.outlineVariant,
            ),
            contentPadding = contentPadding,
        ) { content() }

        SfButtonVariant.Ghost -> TextButton(
            onClick = onClick,
            modifier = sizing,
            enabled = enabled && !loading,
            shape = shape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            contentPadding = contentPadding,
        ) { content() }
    }
}

@Composable
private fun primaryColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun ButtonContent(text: String, loading: Boolean, leadingIcon: ImageVector?) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(18.dp)
                .clearAndSetSemantics { },
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview(name = "Buttons · Dark", showBackground = true)
@Composable
private fun SfButtonDarkPreview() = SfPreviewSurface(dark = true) { ButtonsPreviewContent() }

@Preview(name = "Buttons · Light", showBackground = true)
@Composable
private fun SfButtonLightPreview() = SfPreviewSurface(dark = false) { ButtonsPreviewContent() }

@Composable
private fun ButtonsPreviewContent() {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        SfButton("Scan document", {}, variant = SfButtonVariant.Primary)
        SfButton("Add tag", {}, variant = SfButtonVariant.Secondary)
        SfButton("Cancel", {}, variant = SfButtonVariant.Ghost)
        SfButton("Exporting", {}, loading = true)
        SfButton("Disabled", {}, enabled = false)
    }
}
