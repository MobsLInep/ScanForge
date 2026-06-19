package com.scanforge.designsystem.motion

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scanforge.designsystem.theme.ScanForgeTheme

/**
 * An amber scan-line that sweeps top→bottom with a soft glow trail — the core "scanning beam"
 * motif used on the capture ring and any surface being scanned. Drawn on top of existing content;
 * gated by [active] so it can be toggled without recomposing the subtree.
 */
fun Modifier.scanBeamSweep(
    active: Boolean = true,
    beamColor: Color = Color.Unspecified,
): Modifier = composed {
    val color = if (beamColor != Color.Unspecified) beamColor else ScanForgeTheme.colors.scanBeam
    val transition = rememberInfiniteTransition(label = "scan-beam")
    val position by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(ScanForgeMotion.Duration.ScanSweep, easing = ScanForgeMotion.Linear),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scan-beam-position",
    )
    drawWithContent {
        drawContent()
        if (active) {
            val beamY = size.height * position
            val glow = size.height * 0.16f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, color.copy(alpha = 0.35f), Color.Transparent),
                    startY = (beamY - glow).coerceAtLeast(0f),
                    endY = (beamY + glow).coerceAtMost(size.height),
                ),
            )
            drawLine(
                color = color,
                start = Offset(0f, beamY),
                end = Offset(size.width, beamY),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

/**
 * A breathing teal overlay used while OCR / processing runs (pairs well with [shimmer] on a page
 * thumbnail). [active] gates the effect.
 */
fun Modifier.processingPulse(
    active: Boolean = true,
    pulseColor: Color = Color.Unspecified,
): Modifier = composed {
    val color = if (pulseColor != Color.Unspecified) pulseColor else ScanForgeTheme.colors.processing
    val transition = rememberInfiniteTransition(label = "processing-pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(ScanForgeMotion.Duration.Pulse, easing = ScanForgeMotion.Standard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "processing-alpha",
    )
    drawWithContent {
        drawContent()
        if (active) drawRect(color = color.copy(alpha = alpha))
    }
}
