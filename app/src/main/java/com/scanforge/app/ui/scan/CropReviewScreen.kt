@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanforge.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import com.scanforge.app.R
import com.scanforge.core.domain.scanning.DetectedQuad
import com.scanforge.core.domain.scanning.NormalizedPoint
import com.scanforge.designsystem.components.SfButton
import com.scanforge.designsystem.components.SfButtonVariant
import com.scanforge.designsystem.theme.ScanForgeTheme
import java.io.File

/** Corner identity for drag handling. */
private enum class Corner { TL, TR, BR, BL }

/**
 * Crop-review surface: shows the captured page with the detected quad and four draggable corner
 * handles. When edge detection failed the quad starts as the full frame — this *is* the
 * manual-capture fallback. Confirm keeps the (adjusted) crop; retake discards it.
 */
@Composable
fun CropReviewScreen(
    originalImagePath: String,
    initialQuad: DetectedQuad,
    edgesDetected: Boolean,
    onConfirm: (DetectedQuad) -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var quad by remember(originalImagePath) { mutableStateOf(initialQuad) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val accent = ScanForgeTheme.colors.scanBeam

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it },
        ) {
            AsyncImage(
                model = File(originalImagePath),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            QuadOverlay(quad = quad, color = accent, modifier = Modifier.fillMaxSize())

            if (canvasSize != IntSize.Zero) {
                Corner.entries.forEach { corner ->
                    CornerHandle(
                        corner = corner,
                        quad = quad,
                        canvasSize = canvasSize,
                        color = accent,
                        onDrag = { quad = quad.move(corner, it) },
                    )
                }
            }
        }

        // Hint banner.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
                .background(Color(0xCC000000), MaterialTheme.shapes.medium)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (edgesDetected) Icons.Filled.CheckCircle else Icons.Filled.Info,
                contentDescription = null,
                tint = if (edgesDetected) ScanForgeTheme.colors.success else accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(
                    if (edgesDetected) R.string.scan_edges_found else R.string.scan_edges_manual,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }

        // Actions.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SfButton(
                text = stringResource(R.string.crop_retake),
                onClick = onRetake,
                variant = SfButtonVariant.Ghost,
                modifier = Modifier.weight(1f).testTag(TestTags.CROP_RETAKE),
            )
            SfButton(
                text = stringResource(R.string.crop_confirm),
                onClick = { onConfirm(quad) },
                modifier = Modifier.weight(1f).testTag(TestTags.CROP_CONFIRM),
            )
        }
    }
}

@Composable
private fun CornerHandle(
    corner: Corner,
    quad: DetectedQuad,
    canvasSize: IntSize,
    color: Color,
    onDrag: (delta: NormalizedPoint) -> Unit,
) {
    val point = quad.pointFor(corner)
    val handlePx = with(LocalDensity.current) { 44.dp.toPx() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (point.x * canvasSize.width - handlePx / 2).roundToInt(),
                    (point.y * canvasSize.height - handlePx / 2).roundToInt(),
                )
            }
            .size(44.dp)
            .testTag("${TestTags.CROP_HANDLE_PREFIX}${corner.name}")
            .pointerInput(corner, canvasSize) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(
                        NormalizedPoint(
                            dragAmount.x / canvasSize.width,
                            dragAmount.y / canvasSize.height,
                        ),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(18.dp).background(color, CircleShape))
    }
}

private fun DetectedQuad.pointFor(corner: Corner): NormalizedPoint = when (corner) {
    Corner.TL -> topLeft
    Corner.TR -> topRight
    Corner.BR -> bottomRight
    Corner.BL -> bottomLeft
}

private fun DetectedQuad.move(corner: Corner, delta: NormalizedPoint): DetectedQuad {
    val current = pointFor(corner)
    val moved = NormalizedPoint(
        (current.x + delta.x).coerceIn(0f, 1f),
        (current.y + delta.y).coerceIn(0f, 1f),
    )
    return when (corner) {
        Corner.TL -> copy(topLeft = moved)
        Corner.TR -> copy(topRight = moved)
        Corner.BR -> copy(bottomRight = moved)
        Corner.BL -> copy(bottomLeft = moved)
    }
}
