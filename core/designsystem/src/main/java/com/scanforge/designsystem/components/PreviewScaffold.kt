package com.scanforge.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scanforge.designsystem.theme.ScanForgeTheme

/** Shared wrapper for component @Previews so each one renders inside a themed, padded surface. */
@Composable
internal fun SfPreviewSurface(
    dark: Boolean = true,
    content: @Composable () -> Unit,
) {
    ScanForgeTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.padding(16.dp)) { content() }
        }
    }
}
