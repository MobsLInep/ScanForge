package com.scanforge.designsystem.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.scanforge.designsystem.theme.ScanForgeTheme

/**
 * A diagonal shimmer sweep, themed to the current surface. Paints the node's full bounds, so the
 * caller is responsible for sizing and clipping (e.g. `Modifier.clip(shape).shimmer()`).
 *
 * Used by [com.scanforge.designsystem.components.SfLoadingShimmer] and skeleton placeholders.
 */
fun Modifier.shimmer(): Modifier = composed {
    val base = ScanForgeTheme.colors.shimmerBase
    val highlight = ScanForgeTheme.colors.shimmerHighlight
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(ScanForgeMotion.Duration.Shimmer, easing = LinearEasing),
        ),
        label = "shimmer-progress",
    )
    drawWithCache {
        val travel = size.width * 2f
        val start = -size.width + travel * progress
        val brush = Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(start, 0f),
            end = Offset(start + size.width, size.height),
        )
        onDrawBehind {
            drawRect(color = base)
            drawRect(brush = brush)
        }
    }
}
