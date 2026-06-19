@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.scanforge.app.ui.export

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanforge.app.R
import com.scanforge.core.domain.export.CompressionPreset
import com.scanforge.core.domain.export.ExportColorMode
import com.scanforge.core.domain.export.ExportKind
import com.scanforge.core.domain.export.ExportProgress
import com.scanforge.core.domain.export.PdfPageSize
import com.scanforge.designsystem.components.SfBottomSheet
import com.scanforge.designsystem.components.SfButton
import com.scanforge.designsystem.components.SfButtonVariant
import com.scanforge.designsystem.components.SfChip
import com.scanforge.designsystem.components.SfSegmentedControl
import com.scanforge.designsystem.components.SfTextField
import java.io.File

/**
 * Bottom sheet for exporting the current document. Collects format/size/colour/compression/password
 * options with a live size estimate, runs the export via WorkManager, then offers preview, share,
 * save-to-device (SAF) and open-with for the produced file.
 */
@Composable
fun ExportSheet(
    onDismiss: () -> Unit,
    onPreview: (String) -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SfBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        title = stringResource(R.string.export_title),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val output = state.output
            when {
                output != null && !state.isExporting -> CompletedContent(state, output, onPreview, context)
                state.isExporting || state.progress is ExportProgress.Running -> ProgressContent(state)
                else -> OptionsContent(state, viewModel)
            }
        }
    }
}

@Composable
private fun OptionsContent(state: ExportUiState, viewModel: ExportViewModel) {
    Section(stringResource(R.string.export_format)) {
        val kinds = listOf(ExportKind.SearchablePdf, ExportKind.ImagePdf, ExportKind.PlainText)
        SfSegmentedControl(
            options = listOf(
                stringResource(R.string.export_format_searchable),
                stringResource(R.string.export_format_image),
                stringResource(R.string.export_format_text),
            ),
            selectedIndex = kinds.indexOf(state.kind),
            onSelected = { viewModel.setKind(kinds[it]) },
        )
    }

    if (state.isPdf) {
        Section(stringResource(R.string.export_page_size)) {
            val sizes = listOf(PdfPageSize.Auto, PdfPageSize.Original, PdfPageSize.A4, PdfPageSize.Letter)
            val labels = listOf(
                stringResource(R.string.export_size_auto),
                stringResource(R.string.export_size_original),
                stringResource(R.string.export_size_a4),
                stringResource(R.string.export_size_letter),
            )
            ChipRow(labels, sizes.indexOf(state.pageSize)) { viewModel.setPageSize(sizes[it]) }
        }

        Section(stringResource(R.string.export_color)) {
            val modes = listOf(ExportColorMode.Original, ExportColorMode.Grayscale, ExportColorMode.BlackAndWhite)
            val labels = listOf(
                stringResource(R.string.export_color_original),
                stringResource(R.string.export_color_grayscale),
                stringResource(R.string.export_color_bw),
            )
            ChipRow(labels, modes.indexOf(state.colorMode)) { viewModel.setColor(modes[it]) }
        }

        Section(stringResource(R.string.export_quality)) {
            val presets = listOf(CompressionPreset.HighQuality, CompressionPreset.Balanced, CompressionPreset.Small)
            SfSegmentedControl(
                options = listOf(
                    stringResource(R.string.export_quality_high),
                    stringResource(R.string.export_quality_balanced),
                    stringResource(R.string.export_quality_small),
                ),
                selectedIndex = presets.indexOf(state.compression),
                onSelected = { viewModel.setCompression(presets[it]) },
            )
        }
    }

    EstimateLine(state)

    if (state.isPdf) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.export_password_protect),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = state.passwordEnabled, onCheckedChange = viewModel::setPasswordEnabled)
        }
        if (state.passwordEnabled) {
            SfTextField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                label = stringResource(R.string.export_password_label),
                placeholder = stringResource(R.string.export_password_hint),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (state.isFailed) {
        Text(
            stringResource(R.string.export_low_storage),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    SfButton(
        text = stringResource(R.string.export_start),
        onClick = viewModel::startExport,
        enabled = !state.isPdf || !state.passwordEnabled || state.password.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EstimateLine(state: ExportUiState) {
    val text = if (state.estimating || state.estimateBytes == null) {
        stringResource(R.string.export_estimating)
    } else {
        stringResource(R.string.export_estimate, state.formatSize(state.estimateBytes))
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ProgressContent(state: ExportUiState) {
    val running = state.progress as? ExportProgress.Running
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            if (running != null && running.total > 0) {
                stringResource(R.string.export_exporting, running.completed, running.total)
            } else {
                stringResource(R.string.export_preparing)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (running != null && running.total > 0) {
            LinearProgressIndicator(
                progress = { running.completed.toFloat() / running.total },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CompletedContent(
    state: ExportUiState,
    output: ExportProgress.Completed,
    onPreview: (String) -> Unit,
    context: Context,
) {
    val file = File(output.outputPath)
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.export_done, state.formatSize(output.sizeBytes)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (state.kind != ExportKind.PlainText && state.passwordEnabled) {
            Text(
                stringResource(R.string.export_preview_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.canPreview) {
            SfButton(
                text = stringResource(R.string.export_preview),
                onClick = { onPreview(output.outputPath) },
                leadingIcon = Icons.Outlined.Visibility,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SfButton(
            text = stringResource(R.string.export_share),
            onClick = { shareFile(context, file, output.mimeType) },
            variant = SfButtonVariant.Secondary,
            leadingIcon = Icons.Outlined.IosShare,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SfButton(
                text = stringResource(R.string.export_save),
                onClick = { saveLauncher.launch(file.name) },
                variant = SfButtonVariant.Ghost,
                leadingIcon = Icons.Outlined.SaveAlt,
                modifier = Modifier.weight(1f),
            )
            SfButton(
                text = stringResource(R.string.export_open),
                onClick = { openFile(context, file, output.mimeType) },
                variant = SfButtonVariant.Ghost,
                leadingIcon = Icons.Outlined.FileOpen,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun ChipRow(labels: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            SfChip(label = label, selected = index == selectedIndex, onClick = { onSelected(index) })
        }
    }
}

private fun fileUri(context: Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

private fun shareFile(context: Context, file: File, mime: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, fileUri(context, file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_share)))
}

private fun openFile(context: Context, file: File, mime: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(fileUri(context, file), mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_open))) }
}
