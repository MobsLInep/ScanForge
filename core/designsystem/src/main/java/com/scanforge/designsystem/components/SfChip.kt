package com.scanforge.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanforge.designsystem.motion.rememberScanForgeHaptics
import com.scanforge.designsystem.theme.ScanForgeTheme
import com.scanforge.designsystem.theme.hairline

/** A selectable filter chip (e.g. filtering the library by tag). Toggling gives a light haptic. */
@Composable
fun SfChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberScanForgeHaptics()
    FilterChip(
        selected = selected,
        onClick = {
            haptics.toggle()
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = ScanForgeTheme.shapesExt.chip,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/**
 * A non-selectable document tag: a coloured dot + label in a pill. Pass [onRemove] to render a
 * trailing close affordance (e.g. while editing a document's tags).
 */
@Composable
fun SfTag(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    onRemove: (() -> Unit)? = null,
) {
    val dotColor = if (color != Color.Unspecified) color else ScanForgeTheme.colors.brand
    val shape = ScanForgeTheme.shapesExt.chip
    Surface(
        modifier = modifier.hairline(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onRemove != null) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove $label tag",
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .semantics { role = Role.Button }
                        .clickable(onClickLabel = "Remove $label tag", onClick = onRemove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(name = "Chips & Tags · Dark")
@Composable
private fun SfChipDarkPreview() = SfPreviewSurface(dark = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SfChip("Receipts", selected = true, onClick = {})
        SfChip("Work", selected = false, onClick = {})
        SfTag("Tax", color = ScanForgeTheme.colors.warning)
        SfTag("Personal", onRemove = {})
    }
}
