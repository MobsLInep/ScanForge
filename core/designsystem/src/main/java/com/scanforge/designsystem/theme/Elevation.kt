package com.scanforge.designsystem.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Layered elevation tokens for the "industrial-premium" depth. */
@Immutable
data class ScanForgeElevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 2.dp,
    val level2: Dp = 4.dp,
    val level3: Dp = 8.dp,
    val level4: Dp = 12.dp,
    val level5: Dp = 16.dp,
)

val ScanForgeElevationDefault = ScanForgeElevation()

/** 1px brushed "machined edge" hairline border, themed to the current surface. */
fun Modifier.hairline(shape: Shape, width: Dp = 1.dp): Modifier = composed {
    border(BorderStroke(width, ScanForgeTheme.colors.hairline), shape)
}
