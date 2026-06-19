package com.scanforge.app.ui.scan

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.scanforge.app.R
import com.scanforge.designsystem.components.SfButton
import com.scanforge.designsystem.motion.rememberScanForgeHaptics
import com.scanforge.designsystem.theme.ScanForgeTheme

/**
 * The live capture surface: CameraX [PreviewView] driven by a [LifecycleCameraController] (which
 * gives tap-to-focus and pinch-to-zoom for free), the rule-of-thirds grid, the top control cluster
 * (close / flash / grid / batch / import), the amber shutter, and — in batch mode — the filmstrip
 * plus Done action.
 */
@Composable
fun CameraCaptureContent(
    state: ScanUiState,
    onCapture: (ByteArray) -> Unit,
    onToggleFlash: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleMode: () -> Unit,
    onImport: () -> Unit,
    onClose: () -> Unit,
    onDeletePage: (Long) -> Unit,
    onMovePage: (Long, Boolean) -> Unit,
    onDoneBatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberScanForgeHaptics()
    val ringColor = ScanForgeTheme.colors.scanBeam

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    LaunchedEffect(state.flashEnabled) {
        runCatching { controller.enableTorch(state.flashEnabled) }
    }

    fun capture() {
        haptics.captureTick()
        controller.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bytes = runCatching { image.toUprightJpegBytes() }.getOrNull()
                    image.close()
                    if (bytes != null) onCapture(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    // Surface nothing destructive; the user can simply retry the shot.
                }
            },
        )
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize().testTag(TestTags.CAMERA_PREVIEW),
        )

        if (state.gridEnabled) GridOverlay(modifier = Modifier.fillMaxSize())

        // ── Top controls ──────────────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanControl(
                icon = Icons.Filled.Close,
                description = stringResource(R.string.cd_close_scanner),
                onClick = onClose,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanControl(
                    icon = if (state.flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    description = stringResource(
                        if (state.flashEnabled) R.string.cd_flash_off else R.string.cd_flash_on,
                    ),
                    active = state.flashEnabled,
                    activeTint = ringColor,
                    onClick = { haptics.toggle(); onToggleFlash() },
                    modifier = Modifier.testTag(TestTags.FLASH_TOGGLE),
                )
                ScanControl(
                    icon = if (state.gridEnabled) Icons.Filled.GridOn else Icons.Filled.GridOff,
                    description = stringResource(
                        if (state.gridEnabled) R.string.cd_grid_off else R.string.cd_grid_on,
                    ),
                    active = state.gridEnabled,
                    activeTint = ringColor,
                    onClick = { haptics.toggle(); onToggleGrid() },
                    modifier = Modifier.testTag(TestTags.GRID_TOGGLE),
                )
                ScanControl(
                    icon = if (state.isBatch) Icons.Filled.Layers else Icons.Filled.LayersClear,
                    description = stringResource(
                        if (state.isBatch) R.string.cd_batch_off else R.string.cd_batch_on,
                    ),
                    active = state.isBatch,
                    activeTint = ringColor,
                    onClick = { haptics.toggle(); onToggleMode() },
                    modifier = Modifier.testTag(TestTags.BATCH_TOGGLE),
                )
            }
        }

        if (state.isBatch) {
            Text(
                text = stringResource(R.string.scan_batch_badge),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 56.dp)
                    .background(ringColor.copy(alpha = 0.85f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // ── Bottom cluster: filmstrip + shutter + import/done ─────────────────────────────────
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0x66000000))
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            if (state.isBatch && state.capturedPages.isNotEmpty()) {
                BatchFilmstrip(
                    pages = state.capturedPages,
                    onDelete = onDeletePage,
                    onMove = onMovePage,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ScanControl(
                    icon = Icons.Filled.PhotoLibrary,
                    description = stringResource(R.string.cd_import),
                    onClick = onImport,
                    modifier = Modifier.testTag(TestTags.IMPORT_BUTTON),
                )
                CaptureButton(
                    onClick = { capture() },
                    ringColor = ringColor,
                    modifier = Modifier.testTag(TestTags.CAPTURE_BUTTON),
                )
                Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                    if (state.isBatch && state.capturedPages.isNotEmpty()) {
                        SfButton(
                            text = stringResource(R.string.batch_done),
                            onClick = onDoneBatch,
                            modifier = Modifier.testTag(TestTags.BATCH_DONE),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanControl(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeTint: Color = Color.White,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .background(Color(0x55000000), CircleShape),
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (active) activeTint else Color.White,
        )
    }
}
