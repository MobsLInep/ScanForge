@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.scanforge.app.ui.ocr

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.scanforge.app.R
import com.scanforge.core.domain.ocr.OcrDocument
import com.scanforge.core.domain.ocr.OcrLanguage
import com.scanforge.core.domain.ocr.OcrLanguageMode
import com.scanforge.designsystem.components.SfButton
import com.scanforge.designsystem.components.SfButtonVariant
import com.scanforge.designsystem.components.SfChip
import com.scanforge.designsystem.components.SfEmptyState
import com.scanforge.designsystem.components.SfSegmentedControl
import com.scanforge.designsystem.motion.processingPulse
import com.scanforge.designsystem.theme.ScanForgeTheme
import java.io.File

/** Words below this confidence are tinted in the heatmap; ML Kit confidence is `0f..1f`. */
private const val LOW_CONFIDENCE = 0.7f

@Composable
fun OcrResultScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OcrResultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            com.scanforge.designsystem.components.SfTopBar(
                title = stringResource(R.string.ocr_title),
                onNavigationClick = onBack,
                actions = {
                    if (state.isDone && state.hasText && !state.editing &&
                        state.viewMode == OcrViewMode.Text
                    ) {
                        IconButton(
                            onClick = viewModel::startEditing,
                            modifier = Modifier.testTag(OcrTestTags.EDIT),
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.ocr_edit),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Unit
            state.missing -> SfEmptyState(
                icon = Icons.AutoMirrored.Outlined.Notes,
                title = stringResource(R.string.ocr_missing_title),
                description = stringResource(R.string.ocr_missing_body),
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> OcrContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun OcrContent(
    state: OcrResultUiState,
    viewModel: OcrResultViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        SfSegmentedControl(
            options = listOf(
                stringResource(R.string.ocr_view_image),
                stringResource(R.string.ocr_view_text),
            ),
            selectedIndex = if (state.viewMode == OcrViewMode.Image) 0 else 1,
            onSelected = { viewModel.setViewMode(if (it == 0) OcrViewMode.Image else OcrViewMode.Text) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isProcessing -> ProcessingState(Modifier.fillMaxSize())
                !state.hasText && !state.isDone -> NotRecognizedState(
                    failed = state.isFailed,
                    onRun = viewModel::runOcr,
                    modifier = Modifier.fillMaxSize(),
                )
                state.viewMode == OcrViewMode.Image -> ImageView(state, Modifier.fillMaxSize())
                else -> TextView(state, viewModel, Modifier.fillMaxSize())
            }
        }

        LanguageBar(
            state = state,
            onReRun = { viewModel.reRunWith(it) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
    }
}

// ── Image view: page + optional word boxes / heatmap overlay ─────────────────────────────────────
@Composable
private fun ImageView(state: OcrResultUiState, modifier: Modifier = Modifier) {
    val ocr = state.ocr
    val aspect = if (ocr != null && ocr.imageWidth > 0 && ocr.imageHeight > 0) {
        ocr.imageWidth.toFloat() / ocr.imageHeight
    } else {
        0.72f
    }
    val warning = ScanForgeTheme.colors.warning
    val beam = ScanForgeTheme.colors.scanBeam

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = state.imagePath?.let(::File),
                contentDescription = stringResource(R.string.ocr_image_cd),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect),
            )
            if (ocr != null) {
                Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(aspect)) {
                    ocr.words.forEach { word ->
                        val l = word.box.left * size.width
                        val t = word.box.top * size.height
                        val w = word.box.width * size.width
                        val h = word.box.height * size.height
                        if (state.heatmapEnabled) {
                            val conf = word.confidence
                            if (conf != null && conf < LOW_CONFIDENCE) {
                                drawRect(
                                    color = warning.copy(alpha = 0.18f + 0.4f * (1f - conf)),
                                    topLeft = Offset(l, t),
                                    size = Size(w, h),
                                )
                            }
                        }
                        drawRect(
                            color = beam.copy(alpha = 0.55f),
                            topLeft = Offset(l, t),
                            size = Size(w, h),
                            style = Stroke(width = 1.5f),
                        )
                    }
                }
            }
        }
        HeatmapToggleRow(state, onToggle = {})
    }
}

// ── Text view: selectable text / inline edit / copy / share / heatmap ────────────────────────────
@Composable
private fun TextView(
    state: OcrResultUiState,
    viewModel: OcrResultViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Column(modifier = modifier) {
        if (state.editing) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = viewModel::updateDraft,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag(OcrTestTags.EDIT_FIELD),
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SfButton(
                    text = stringResource(R.string.ocr_save),
                    onClick = viewModel::saveEditing,
                    modifier = Modifier.weight(1f),
                )
                SfButton(
                    text = stringResource(R.string.ocr_cancel),
                    onClick = viewModel::cancelEditing,
                    variant = SfButtonVariant.Ghost,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            ActionRow(
                onCopy = {
                    clipboard.setText(AnnotatedString(state.text))
                },
                onShare = { shareText(context, state.text) },
                confidencePercent = state.confidencePercent,
            )
            Spacer(Modifier.height(8.dp))
            HeatmapToggleRow(state, onToggle = viewModel::toggleHeatmap)
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                SelectionContainer(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text(
                        text = renderedText(state),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().testTag(OcrTestTags.TEXT),
                    )
                }
            }
        }
    }
}

/** Builds the displayed text, applying the confidence heatmap as word backgrounds when enabled. */
@Composable
private fun renderedText(state: OcrResultUiState): AnnotatedString {
    val ocr = state.ocr
    val warning = ScanForgeTheme.colors.warning
    // Heatmap needs the structured words and an unedited text; fall back to plain text otherwise.
    if (!state.heatmapEnabled || ocr == null || ocr.fullText != state.text) {
        return AnnotatedString(state.text)
    }
    return buildAnnotatedString {
        ocr.lines.forEachIndexed { lineIndex, line ->
            if (lineIndex > 0) append("\n")
            line.words.forEachIndexed { wordIndex, word ->
                if (wordIndex > 0) append(" ")
                val conf = word.confidence
                if (conf != null && conf < LOW_CONFIDENCE) {
                    withStyle(SpanStyle(background = warning.copy(alpha = 0.18f + 0.4f * (1f - conf)))) {
                        append(word.text)
                    }
                } else {
                    append(word.text)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    confidencePercent: Int?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCopy, modifier = Modifier.testTag(OcrTestTags.COPY)) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.ocr_copy))
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.ocr_share))
        }
        Spacer(Modifier.weight(1f))
        if (confidencePercent != null) {
            Text(
                text = stringResource(R.string.ocr_confidence, confidencePercent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeatmapToggleRow(
    state: OcrResultUiState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasConfidence = state.ocr?.words?.any { it.confidence != null } == true
    if (!hasConfidence) return
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ocr_heatmap),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Switch(
            checked = state.heatmapEnabled,
            onCheckedChange = { onToggle() },
            modifier = Modifier.testTag(OcrTestTags.HEATMAP),
        )
    }
}

@Composable
private fun LanguageBar(
    state: OcrResultUiState,
    onReRun: (OcrLanguageMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.ocr_language_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SfChip(
                label = stringResource(R.string.ocr_lang_auto),
                selected = state.languageMode is OcrLanguageMode.Auto,
                onClick = { onReRun(OcrLanguageMode.Auto) },
            )
            OcrResultViewModel.SELECTABLE_LANGUAGES.forEach { language ->
                SfChip(
                    label = languageLabel(language),
                    selected = (state.languageMode as? OcrLanguageMode.Manual)?.language == language,
                    onClick = { onReRun(OcrLanguageMode.Manual(language)) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        SfButton(
            text = stringResource(
                if (state.isDone || state.isFailed) R.string.ocr_rerun else R.string.ocr_run,
            ),
            onClick = { onReRun(state.languageMode) },
            variant = SfButtonVariant.Secondary,
            leadingIcon = Icons.Outlined.Refresh,
            enabled = !state.isProcessing,
            loading = state.isProcessing,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProcessingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .processingPulse(active = true),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = ScanForgeTheme.colors.processing)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.ocr_processing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotRecognizedState(
    failed: Boolean,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (failed) Icons.Outlined.Refresh else Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(if (failed) R.string.ocr_failed_body else R.string.ocr_idle_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        SfButton(
            text = stringResource(if (failed) R.string.ocr_retry else R.string.ocr_run),
            onClick = onRun,
            leadingIcon = Icons.Outlined.Refresh,
        )
    }
}

private fun languageLabel(language: OcrLanguage): String = when (language) {
    OcrLanguage.Latin -> "Latin"
    OcrLanguage.Devanagari -> "हिन्दी"
    OcrLanguage.Chinese -> "中文"
    OcrLanguage.Japanese -> "日本語"
    OcrLanguage.Korean -> "한국어"
}

private fun shareText(context: android.content.Context, text: String) {
    if (text.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

internal object OcrTestTags {
    const val EDIT = "ocr_edit"
    const val EDIT_FIELD = "ocr_edit_field"
    const val TEXT = "ocr_text"
    const val COPY = "ocr_copy"
    const val HEATMAP = "ocr_heatmap"
}
