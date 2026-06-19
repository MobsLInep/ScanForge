package com.scanforge.app.ui.scan

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scanforge.app.R
import com.scanforge.core.domain.scanning.DetectedQuad
import kotlinx.coroutines.launch

/** Rule-of-thirds composition grid, drawn over the live preview when enabled. */
@Composable
fun GridOverlay(modifier: Modifier = Modifier) {
    val line = Color.White.copy(alpha = 0.28f)
    Canvas(modifier = modifier) {
        val thirdW = size.width / 3f
        val thirdH = size.height / 3f
        for (i in 1..2) {
            drawLine(line, Offset(thirdW * i, 0f), Offset(thirdW * i, size.height), 1f)
            drawLine(line, Offset(0f, thirdH * i), Offset(size.width, thirdH * i), 1f)
        }
    }
}

/**
 * Draws the detected page quad as an animated amber polygon over the captured frame. Used on the
 * crop-review surface; corners are normalized so the same quad maps onto any canvas size.
 */
@Composable
fun QuadOverlay(
    quad: DetectedQuad,
    color: Color,
    modifier: Modifier = Modifier,
    fillAlpha: Float = 0.12f,
) {
    Canvas(modifier = modifier) {
        fun point(nx: Float, ny: Float) = Offset(nx * size.width, ny * size.height)
        val path = Path().apply {
            moveTo(quad.topLeft.x * size.width, quad.topLeft.y * size.height)
            lineTo(quad.topRight.x * size.width, quad.topRight.y * size.height)
            lineTo(quad.bottomRight.x * size.width, quad.bottomRight.y * size.height)
            lineTo(quad.bottomLeft.x * size.width, quad.bottomLeft.y * size.height)
            close()
        }
        drawPath(path, color = color.copy(alpha = fillAlpha))
        drawPath(path, color = color, style = Stroke(width = 3.dp.toPx()))
        // Corner pips.
        listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft).forEach {
            drawCircle(color, radius = 6.dp.toPx(), center = point(it.x, it.y))
        }
    }
}

/**
 * The ScanForge shutter: a forge-amber ring with a white core that springs inward on press, plus a
 * one-shot scan-line sweep across the button on capture. Haptics are fired by the caller's [onClick]
 * site so this stays a pure visual.
 */
@Composable
fun CaptureButton(
    onClick: () -> Unit,
    ringColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val sweep = remember { Animatable(0f) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "capture-scale")
    val description = stringResource(R.string.cd_capture)

    Box(
        modifier = modifier
            .size(76.dp)
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) {
                scope.launch {
                    sweep.snapTo(0f)
                    sweep.animateTo(1f, com.scanforge.designsystem.motion.ScanForgeMotion.tweenStandard())
                    sweep.snapTo(0f)
                }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        val coreColor = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)
        Canvas(modifier = Modifier.size(76.dp).scale(scale)) {
            val stroke = 5.dp.toPx()
            drawCircle(
                color = ringColor,
                radius = size.minDimension / 2f - stroke / 2f,
                style = Stroke(width = stroke),
            )
            drawCircle(color = coreColor, radius = size.minDimension / 2f - stroke * 2.4f)
            if (sweep.value > 0f) {
                val y = size.height * sweep.value
                drawLine(ringColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            }
        }
    }
}
