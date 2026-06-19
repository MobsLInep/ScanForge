@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanforge.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.scanforge.app.R
import com.scanforge.app.ui.scan.QuadOverlay
import com.scanforge.core.domain.scanning.DetectedQuad
import com.scanforge.core.domain.scanning.NormalizedPoint
import com.scanforge.designsystem.components.SfButton
import com.scanforge.designsystem.components.SfButtonVariant
import com.scanforge.designsystem.theme.ScanForgeTheme
import java.io.File
import kotlin.math.roundToInt

private enum class CropCorner { TL, TR, BR, BL }

/** Normalized distance under which a dragged corner snaps to the image edge. */
private const val SNAP_THRESHOLD = 0.035f

/**
 * Full-screen crop/corner editor with four draggable handles, a magnifier loupe (API 28+) that
 * zooms the area under the active corner, edge snapping, and undo. Returns the adjusted quad via
 * [onApply]; [onCancel] discards. Pure UI over a normalized [DetectedQuad].
 */
@Composable
fun CropEditor(
    originalImagePath: String,
    initialQuad: DetectedQuad,
    onApply: (DetectedQuad) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var quad by remember(originalImagePath) { mutableStateOf(initialQuad) }
    val history = remember(originalImagePath) { ArrayDeque<DetectedQuad>() }
    var historyDepth by remember(originalImagePath) { mutableStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activeCorner by remember { mutableStateOf<CropCorner?>(null) }
    val accent = ScanForgeTheme.colors.scanBeam

    BackHandler(onBack = onCancel)

    fun pushHistory() {
        history.addLast(quad)
        historyDepth = history.size
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Inset the crop canvas so the corner handles never sit on the screen edges, where the
        // system back/assistant gestures would otherwise swallow the drag.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 28.dp, vertical = 72.dp)
                .onSizeChanged { canvasSize = it }
                // Magnifier loupe: zooms whatever sits under the active corner; hidden otherwise.
                .magnifier(
                    sourceCenter = {
                        val corner = activeCorner
                        if (corner == null || canvasSize == IntSize.Zero) {
                            Offset.Unspecified
                        } else {
                            val p = quad.pointFor(corner)
                            Offset(p.x * canvasSize.width, p.y * canvasSize.height)
                        }
                    },
                    zoom = 2f,
                ),
        ) {
            AsyncImage(
                model = File(originalImagePath),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            QuadOverlay(quad = quad, color = accent, modifier = Modifier.fillMaxSize())

            if (canvasSize != IntSize.Zero) {
                CropCorner.entries.forEach { corner ->
                    CornerHandle(
                        corner = corner,
                        quad = quad,
                        canvasSize = canvasSize,
                        color = accent,
                        onDragStart = { pushHistory(); activeCorner = corner },
                        onDrag = { quad = quad.moveAndSnap(corner, it) },
                        onDragEnd = { activeCorner = null },
                    )
                }
            }
        }

        // Title.
        Text(
            text = stringResource(R.string.crop_editor_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
                .background(Color(0xCC000000), MaterialTheme.shapes.medium)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )

        // Undo affordance.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
        ) {
            SfButton(
                text = stringResource(R.string.crop_undo),
                onClick = {
                    if (history.isNotEmpty()) {
                        quad = history.removeLast()
                        historyDepth = history.size
                    }
                },
                variant = SfButtonVariant.Ghost,
                enabled = historyDepth > 0,
                leadingIcon = Icons.AutoMirrored.Filled.Undo,
                modifier = Modifier.testTag(EditorTestTags.CROP_UNDO),
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
                text = stringResource(R.string.crop_reset),
                onClick = { pushHistory(); quad = DetectedQuad.FULL_FRAME },
                variant = SfButtonVariant.Ghost,
                modifier = Modifier.weight(1f),
            )
            SfButton(
                text = stringResource(R.string.crop_apply),
                onClick = { onApply(quad) },
                modifier = Modifier.weight(1f).testTag(EditorTestTags.CROP_APPLY),
            )
        }
    }
}

@Composable
private fun CornerHandle(
    corner: CropCorner,
    quad: DetectedQuad,
    canvasSize: IntSize,
    color: Color,
    onDragStart: () -> Unit,
    onDrag: (delta: NormalizedPoint) -> Unit,
    onDragEnd: () -> Unit,
) {
    val point = quad.pointFor(corner)
    val handlePx = with(LocalDensity.current) { 44.dp.toPx() }
    val cornerDesc = stringResource(R.string.cd_crop_corner)
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (point.x * canvasSize.width - handlePx / 2).roundToInt(),
                    (point.y * canvasSize.height - handlePx / 2).roundToInt(),
                )
            }
            .size(44.dp)
            .semantics { contentDescription = cornerDesc }
            .testTag("${EditorTestTags.CROP_HANDLE_PREFIX}${corner.name}")
            .pointerInput(corner, canvasSize) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                ) { change, dragAmount ->
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
        Box(
            Modifier
                .size(18.dp)
                .background(color, CircleShape),
        )
    }
}

private fun DetectedQuad.pointFor(corner: CropCorner): NormalizedPoint = when (corner) {
    CropCorner.TL -> topLeft
    CropCorner.TR -> topRight
    CropCorner.BR -> bottomRight
    CropCorner.BL -> bottomLeft
}

/** Moves a corner by [delta], clamps to the frame, and snaps to an edge when within threshold. */
private fun DetectedQuad.moveAndSnap(corner: CropCorner, delta: NormalizedPoint): DetectedQuad {
    val current = pointFor(corner)
    fun snap(v: Float): Float = when {
        v < SNAP_THRESHOLD -> 0f
        v > 1f - SNAP_THRESHOLD -> 1f
        else -> v
    }
    val moved = NormalizedPoint(
        snap((current.x + delta.x).coerceIn(0f, 1f)),
        snap((current.y + delta.y).coerceIn(0f, 1f)),
    )
    return when (corner) {
        CropCorner.TL -> copy(topLeft = moved)
        CropCorner.TR -> copy(topRight = moved)
        CropCorner.BR -> copy(bottomRight = moved)
        CropCorner.BL -> copy(bottomLeft = moved)
    }
}

internal object EditorTestTags {
    const val CROP_HANDLE_PREFIX = "crop_editor_handle_"
    const val CROP_APPLY = "crop_editor_apply"
    const val CROP_UNDO = "crop_editor_undo"
    const val FILTER_PREFIX = "editor_filter_"
    const val EDITOR_APPLY = "editor_apply"
    const val EDITOR_ROTATE = "editor_rotate"
    const val EDITOR_CROP = "editor_crop"
    const val COMPARE_TOGGLE = "editor_compare"
}
