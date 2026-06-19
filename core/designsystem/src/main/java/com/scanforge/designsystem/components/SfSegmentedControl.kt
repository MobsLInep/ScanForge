package com.scanforge.designsystem.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.scanforge.designsystem.motion.rememberScanForgeHaptics
import com.scanforge.designsystem.theme.ScanForgeTheme

/**
 * Single-choice segmented control (e.g. Grid/List view, theme switcher). The active segment uses
 * the amber primary; selecting fires a light haptic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SfSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = rememberScanForgeHaptics()
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = {
                    if (index != selectedIndex) {
                        haptics.toggle()
                        onSelected(index)
                    }
                },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

@Preview(name = "Segmented · Dark")
@Composable
private fun SfSegmentedDarkPreview() = SfPreviewSurface(dark = true) {
    SfSegmentedControl(options = listOf("Grid", "List"), selectedIndex = 0, onSelected = {})
}

@Preview(name = "Segmented · Light")
@Composable
private fun SfSegmentedLightPreview() = SfPreviewSurface(dark = false) {
    SfSegmentedControl(options = listOf("Grid", "List"), selectedIndex = 1, onSelected = {})
}
