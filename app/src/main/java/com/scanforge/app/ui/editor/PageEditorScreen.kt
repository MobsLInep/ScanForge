@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanforge.app.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import coil.compose.AsyncImage
import com.scanforge.app.R
import com.scanforge.core.domain.imaging.EnhancementFilter
import com.scanforge.core.domain.imaging.EnhancementSettings
import com.scanforge.designsystem.components.SfButton
import com.scanforge.designsystem.components.SfChip
import com.scanforge.designsystem.components.SfSegmentedControl
import com.scanforge.designsystem.components.SfTopBar
import com.scanforge.designsystem.theme.ScanForgeTheme
import java.io.File

/**
 * Non-destructive page editor. Shows a live "after" preview that updates as the user picks an
 * enhancement filter, tunes adjustments, rotates, or re-crops; press-and-hold the preview to compare
 * against the untouched original. Apply renders at full resolution and persists the recipe.
 */
@Composable
fun PageEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PageEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    val cropQuad = state.processing.cropQuad
    val original = state.originalImagePath
    if (state.showCropEditor && original != null) {
        CropEditor(
            originalImagePath = original,
            initialQuad = cropQuad ?: com.scanforge.core.domain.scanning.DetectedQuad.FULL_FRAME,
            onApply = viewModel::applyCrop,
            onCancel = viewModel::cancelCropEditor,
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SfTopBar(
                title = stringResource(R.string.editor_title),
                onNavigationClick = onBack,
                actions = {
                    IconButton(
                        onClick = viewModel::rotate,
                        enabled = state.canEdit,
                        modifier = Modifier.testTag(EditorTestTags.EDITOR_ROTATE),
                    ) {
                        Icon(
                            Icons.Filled.Rotate90DegreesCw,
                            contentDescription = stringResource(R.string.cd_rotate_page),
                        )
                    }
                    IconButton(
                        onClick = viewModel::openCropEditor,
                        enabled = state.canEdit,
                        modifier = Modifier.testTag(EditorTestTags.EDITOR_CROP),
                    ) {
                        Icon(
                            Icons.Filled.Crop,
                            contentDescription = stringResource(R.string.cd_recrop_page),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            BeforeAfterPreview(
                originalPath = original,
                afterBitmap = state.preview,
                rendering = state.rendering,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            EditorControls(
                settings = state.processing.enhancement,
                enabled = state.canEdit,
                saving = state.saving,
                errorRes = state.errorRes,
                onSelectFilter = viewModel::selectFilter,
                onBrightness = viewModel::setBrightness,
                onContrast = viewModel::setContrast,
                onSharpness = viewModel::setSharpness,
                onDeskew = viewModel::toggleDeskew,
                onShadows = viewModel::toggleShadows,
                onDenoise = viewModel::toggleDenoise,
                onApply = viewModel::apply,
            )
        }
    }
}

@Composable
private fun BeforeAfterPreview(
    originalPath: String?,
    afterBitmap: android.graphics.Bitmap?,
    rendering: Boolean,
    modifier: Modifier = Modifier,
) {
    var comparing by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(originalPath) {
                detectTapGestures(
                    onPress = {
                        comparing = true
                        tryAwaitRelease()
                        comparing = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val showOriginal = comparing || afterBitmap == null
        if (showOriginal && originalPath != null) {
            AsyncImage(
                model = File(originalPath),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )
        } else if (afterBitmap != null) {
            Image(
                bitmap = afterBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )
        }

        // Before/After label.
        Text(
            text = stringResource(if (comparing) R.string.editor_before else R.string.editor_after),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(
                    if (comparing) Color(0xCC000000) else ScanForgeTheme.colors.scanBeam.copy(alpha = 0.85f),
                    MaterialTheme.shapes.small,
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Text(
            text = stringResource(R.string.editor_compare),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
        )
        if (rendering) {
            CircularProgressIndicator(
                color = ScanForgeTheme.colors.scanBeam,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(22.dp),
            )
        }
    }
}

@Composable
private fun EditorControls(
    settings: EnhancementSettings,
    enabled: Boolean,
    saving: Boolean,
    errorRes: Int?,
    onSelectFilter: (EnhancementFilter) -> Unit,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSharpness: (Float) -> Unit,
    onDeskew: (Boolean) -> Unit,
    onShadows: (Boolean) -> Unit,
    onDenoise: (Boolean) -> Unit,
    onApply: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SfSegmentedControl(
            options = listOf(
                stringResource(R.string.editor_tab_filters),
                stringResource(R.string.editor_tab_adjust),
            ),
            selectedIndex = tab,
            onSelected = { tab = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            if (tab == 0) {
                FilterRow(selected = settings.filter, enabled = enabled, onSelect = onSelectFilter)
            } else {
                AdjustPanel(
                    settings = settings,
                    enabled = enabled,
                    onBrightness = onBrightness,
                    onContrast = onContrast,
                    onSharpness = onSharpness,
                    onDeskew = onDeskew,
                    onShadows = onShadows,
                    onDenoise = onDenoise,
                )
            }
        }

        if (errorRes != null) {
            Text(
                text = stringResource(errorRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SfButton(
            text = stringResource(R.string.editor_apply),
            onClick = onApply,
            enabled = enabled,
            loading = saving,
            modifier = Modifier.fillMaxWidth().testTag(EditorTestTags.EDITOR_APPLY),
        )
    }
}

@Composable
private fun FilterRow(
    selected: EnhancementFilter,
    enabled: Boolean,
    onSelect: (EnhancementFilter) -> Unit,
) {
    val filters = listOf(
        EnhancementFilter.Auto to R.string.filter_auto,
        EnhancementFilter.MagicColor to R.string.filter_magic,
        EnhancementFilter.Grayscale to R.string.filter_grayscale,
        EnhancementFilter.BlackAndWhite to R.string.filter_bw,
        EnhancementFilter.Original to R.string.filter_original,
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { (filter, labelRes) ->
            SfChip(
                label = stringResource(labelRes),
                selected = filter == selected,
                enabled = enabled,
                onClick = { onSelect(filter) },
                modifier = Modifier.testTag("${EditorTestTags.FILTER_PREFIX}${filter.name}"),
            )
        }
    }
}

@Composable
private fun AdjustPanel(
    settings: EnhancementSettings,
    enabled: Boolean,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSharpness: (Float) -> Unit,
    onDeskew: (Boolean) -> Unit,
    onShadows: (Boolean) -> Unit,
    onDenoise: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LabeledSlider(R.string.adjust_brightness, settings.brightness, -1f..1f, enabled, onBrightness)
        LabeledSlider(R.string.adjust_contrast, settings.contrast, -1f..1f, enabled, onContrast)
        LabeledSlider(R.string.adjust_sharpness, settings.sharpness, 0f..1f, enabled, onSharpness)
        ToggleRow(R.string.adjust_deskew, settings.deskew, enabled, onDeskew)
        ToggleRow(R.string.adjust_shadows, settings.removeShadows, enabled, onShadows)
        ToggleRow(R.string.adjust_denoise, settings.denoise, enabled, onDenoise)
    }
}

@Composable
private fun LabeledSlider(
    labelRes: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
        )
    }
}

@Composable
private fun ToggleRow(labelRes: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
