@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanforge.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanforge.app.R
import com.scanforge.app.ui.util.formatBytes
import com.scanforge.core.domain.imaging.EnhancementFilter
import com.scanforge.core.domain.model.AccentColor
import com.scanforge.core.domain.model.ExportFormat
import com.scanforge.core.domain.model.ExportQuality
import com.scanforge.core.domain.ocr.OcrLanguage
import com.scanforge.designsystem.components.SfButton
import com.scanforge.designsystem.components.SfButtonVariant
import com.scanforge.designsystem.components.SfCard
import com.scanforge.designsystem.components.SfTextField
import com.scanforge.designsystem.components.SfTopBar
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Dialog visibility flags.
    var showTheme by remember { mutableStateOf(false) }
    var showAccent by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showBackupPassword by remember { mutableStateOf(false) }
    var pendingBackupPassword by remember { mutableStateOf("") }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.backup(it.toString(), pendingBackupPassword) } }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.inspectRestore(it.toString()) } }

    // Map one-shot message codes to localized snackbars.
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        val text = messageToText(msg, context)
        if (text != null) scope.launch { snackbar.showSnackbar(text) }
        viewModel.consumeMessage()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SfTopBar(title = stringResource(R.string.settings_title), onNavigationClick = null) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── Appearance ──────────────────────────────────────────────────────────────────
            SettingsGroup(stringResource(R.string.settings_group_appearance)) {
                PickerRow(
                    title = stringResource(R.string.settings_theme),
                    value = themeLabel(settings.darkTheme, context),
                    onClick = { showTheme = true },
                )
                RowDivider()
                PickerRow(
                    title = stringResource(R.string.settings_accent),
                    value = accentLabel(settings.accent, context),
                    onClick = { showAccent = true },
                )
            }

            // ── Scanning & OCR ──────────────────────────────────────────────────────────────
            SettingsGroup(stringResource(R.string.settings_group_scanning)) {
                PickerRow(
                    title = stringResource(R.string.settings_scan_filter),
                    value = filterLabel(settings.defaultScanFilter, context),
                    onClick = { showFilter = true },
                )
                RowDivider()
                PickerRow(
                    title = stringResource(R.string.settings_ocr_languages),
                    value = settings.ocrLanguages.joinToString(", ") { languageLabel(it, context) },
                    onClick = { showLanguages = true },
                )
                RowDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_auto_ocr),
                    subtitle = stringResource(R.string.settings_auto_ocr_body),
                    checked = settings.autoOcr,
                    onCheckedChange = viewModel::setAutoOcr,
                )
                RowDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_grid),
                    subtitle = stringResource(R.string.settings_grid_body),
                    checked = settings.gridOverlayDefault,
                    onCheckedChange = viewModel::setGridOverlay,
                )
                RowDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_save_original),
                    subtitle = stringResource(R.string.settings_save_original_body),
                    checked = settings.saveOriginal,
                    onCheckedChange = viewModel::setSaveOriginal,
                )
            }

            // ── Export ──────────────────────────────────────────────────────────────────────
            SettingsGroup(stringResource(R.string.settings_group_export)) {
                PickerRow(
                    title = stringResource(R.string.settings_export_format),
                    value = formatLabel(settings.defaultExportFormat, context),
                    onClick = { showFormat = true },
                )
                RowDivider()
                PickerRow(
                    title = stringResource(R.string.settings_export_quality),
                    value = qualityLabel(settings.defaultExportQuality, context),
                    onClick = { showQuality = true },
                )
            }

            // ── Trash ───────────────────────────────────────────────────────────────────────
            SettingsGroup(stringResource(R.string.settings_trash_title)) {
                Text(
                    stringResource(R.string.settings_trash_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        pluralStringResource(
                            R.plurals.settings_trash_days,
                            settings.trashRetentionDays,
                            settings.trashRetentionDays,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(onClick = { viewModel.setRetention(settings.trashRetentionDays - 7) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "−7")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(onClick = { viewModel.setRetention(settings.trashRetentionDays + 7) }) {
                        Icon(Icons.Filled.Add, contentDescription = "+7")
                    }
                }
            }

            // ── Storage ─────────────────────────────────────────────────────────────────────
            SettingsGroup(stringResource(R.string.settings_group_storage)) {
                StorageRow(stringResource(R.string.settings_storage_images), state.storage.images)
                StorageRow(stringResource(R.string.settings_storage_thumbnails), state.storage.thumbnails)
                StorageRow(stringResource(R.string.settings_storage_database), state.storage.database)
                StorageRow(stringResource(R.string.settings_storage_cache), state.storage.cache)
                RowDivider()
                StorageRow(stringResource(R.string.settings_storage_total), state.storage.total, bold = true)
                Spacer(Modifier.height(12.dp))
                SfButton(
                    text = stringResource(R.string.settings_clear_cache),
                    onClick = { viewModel.clearCache() },
                    variant = SfButtonVariant.Secondary,
                    leadingIcon = Icons.Outlined.CleaningServices,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Backup & restore ────────────────────────────────────────────────────────────
            SettingsGroup(stringResource(R.string.settings_group_backup)) {
                ActionRow(
                    title = stringResource(R.string.settings_backup),
                    subtitle = stringResource(R.string.settings_backup_body),
                    onClick = { showBackupPassword = true },
                )
                RowDivider()
                ActionRow(
                    title = stringResource(R.string.settings_restore),
                    subtitle = stringResource(R.string.settings_restore_body),
                    onClick = { openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                )
                if (state.restoreStaged) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.restore_done),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // ── Cloud sync ──────────────────────────────────────────────────────────────────
            SettingsGroup(stringResource(R.string.settings_group_sync)) {
                SwitchRow(
                    title = stringResource(R.string.settings_sync_enable),
                    subtitle = stringResource(R.string.settings_sync_body),
                    checked = settings.syncEnabled,
                    onCheckedChange = viewModel::setSyncEnabled,
                )
                if (settings.syncEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.settings_sync_status_notconnected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_sync_experimental),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // ── Privacy ─────────────────────────────────────────────────────────────────────
            SettingsGroup(stringResource(R.string.settings_group_privacy)) {
                Text(
                    stringResource(R.string.settings_privacy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_crash_enable),
                    subtitle = stringResource(R.string.settings_crash_body),
                    checked = settings.crashReportingEnabled,
                    onCheckedChange = viewModel::setCrashReportingEnabled,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_analytics_enable),
                    subtitle = stringResource(R.string.settings_analytics_body),
                    checked = settings.analyticsEnabled,
                    onCheckedChange = viewModel::setAnalyticsEnabled,
                )
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────────────────────
    if (showTheme) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                null to stringResource(R.string.settings_theme_system),
                true to stringResource(R.string.settings_theme_dark),
                false to stringResource(R.string.settings_theme_light),
            ),
            selected = settings.darkTheme,
            onSelect = { viewModel.setDarkTheme(it); showTheme = false },
            onDismiss = { showTheme = false },
        )
    }
    if (showAccent) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_accent),
            options = AccentColor.entries.map { it to accentLabel(it, context) },
            selected = settings.accent,
            onSelect = { viewModel.setAccent(it); showAccent = false },
            onDismiss = { showAccent = false },
        )
    }
    if (showFilter) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_scan_filter),
            options = EnhancementFilter.entries.map { it to filterLabel(it, context) },
            selected = settings.defaultScanFilter,
            onSelect = { viewModel.setScanFilter(it); showFilter = false },
            onDismiss = { showFilter = false },
        )
    }
    if (showFormat) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_export_format),
            options = ExportFormat.entries.map { it to formatLabel(it, context) },
            selected = settings.defaultExportFormat,
            onSelect = { viewModel.setExportFormat(it); showFormat = false },
            onDismiss = { showFormat = false },
        )
    }
    if (showQuality) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_export_quality),
            options = ExportQuality.entries.map { it to qualityLabel(it, context) },
            selected = settings.defaultExportQuality,
            onSelect = { viewModel.setExportQuality(it); showQuality = false },
            onDismiss = { showQuality = false },
        )
    }
    if (showLanguages) {
        MultiChoiceDialog(
            title = stringResource(R.string.ocr_lang_title),
            options = OcrLanguage.entries.map { it.tag to languageLabel(it.tag, context) },
            selected = settings.ocrLanguages.toSet(),
            onConfirm = { viewModel.setOcrLanguages(it); showLanguages = false },
            onDismiss = { showLanguages = false },
        )
    }
    if (showBackupPassword) {
        PasswordDialog(
            title = stringResource(R.string.backup_password_title),
            body = stringResource(R.string.backup_password_body),
            label = stringResource(R.string.backup_password_label),
            confirmText = stringResource(R.string.backup_create),
            requirePassword = false,
            onConfirm = { pw ->
                pendingBackupPassword = pw
                showBackupPassword = false
                createBackup.launch("scanforge-backup.zip")
            },
            onDismiss = { showBackupPassword = false },
        )
    }
    state.pendingRestore?.let { pending ->
        RestoreConfirmDialog(
            documentCount = pending.manifest.documentCount,
            encrypted = pending.manifest.encrypted,
            busy = state.busy == SettingsBusy.Restoring,
            onConfirm = { pw -> viewModel.confirmRestore(pw) },
            onDismiss = { viewModel.cancelRestorePrompt() },
        )
    }
}

// ── Reusable rows ───────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        SfCard { content() }
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(
    Modifier.padding(vertical = 8.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
)

@Composable
private fun PickerRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StorageRow(label: String, bytes: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatBytes(bytes),
            style = MaterialTheme.typography.bodyMedium,
            color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ── Dialogs ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = value == selected, onClick = { onSelect(value) }).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) } },
    )
}

@Composable
private fun MultiChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (tag, label) ->
                    val isChecked = tag in current
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = isChecked, onClick = {
                            current = current.toMutableSet().apply { if (!add(tag)) remove(tag) }
                                .ifEmpty { setOf(tag) }
                        }).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = isChecked, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(current) }) { Text(stringResource(R.string.generic_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) } },
    )
}

@Composable
private fun PasswordDialog(
    title: String,
    body: String,
    label: String,
    confirmText: String,
    requirePassword: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                SfTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = label,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = !requirePassword || password.isNotBlank(),
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) } },
    )
}

@Composable
private fun RestoreConfirmDialog(
    documentCount: Int,
    encrypted: Boolean,
    busy: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.restore_password_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.restore_password_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    pluralStringResource(R.plurals.restore_doc_count, documentCount, documentCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (encrypted) {
                    Spacer(Modifier.height(16.dp))
                    SfTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.restore_password_label),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = !busy && (!encrypted || password.isNotBlank())) {
                Text(stringResource(if (busy) R.string.restore_running else R.string.settings_restore))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.generic_cancel)) } },
    )
}

// ── Label mappers ───────────────────────────────────────────────────────────────────────────

private fun themeLabel(dark: Boolean?, context: android.content.Context): String = when (dark) {
    null -> context.getString(R.string.settings_theme_system)
    true -> context.getString(R.string.settings_theme_dark)
    false -> context.getString(R.string.settings_theme_light)
}

private fun accentLabel(accent: AccentColor, context: android.content.Context): String = when (accent) {
    AccentColor.Amber -> context.getString(R.string.settings_accent_amber)
    AccentColor.Teal -> context.getString(R.string.settings_accent_teal)
}

private fun filterLabel(filter: EnhancementFilter, context: android.content.Context): String = when (filter) {
    EnhancementFilter.Original -> context.getString(R.string.settings_filter_original)
    EnhancementFilter.Auto -> context.getString(R.string.settings_filter_auto)
    EnhancementFilter.MagicColor -> context.getString(R.string.settings_filter_magic)
    EnhancementFilter.Grayscale -> context.getString(R.string.settings_filter_grayscale)
    EnhancementFilter.BlackAndWhite -> context.getString(R.string.settings_filter_bw)
}

private fun formatLabel(format: ExportFormat, context: android.content.Context): String = when (format) {
    ExportFormat.Pdf -> context.getString(R.string.settings_format_pdf)
    ExportFormat.SearchablePdf -> context.getString(R.string.settings_format_searchable)
    ExportFormat.PlainText -> context.getString(R.string.settings_format_text)
    ExportFormat.Images -> context.getString(R.string.settings_format_images)
}

private fun qualityLabel(quality: ExportQuality, context: android.content.Context): String = when (quality) {
    ExportQuality.High -> context.getString(R.string.export_quality_high)
    ExportQuality.Balanced -> context.getString(R.string.export_quality_balanced)
    ExportQuality.Small -> context.getString(R.string.export_quality_small)
}

private fun languageLabel(tag: String, context: android.content.Context): String = when (tag) {
    OcrLanguage.Latin.tag -> context.getString(R.string.ocr_lang_latin)
    OcrLanguage.Devanagari.tag -> context.getString(R.string.ocr_lang_devanagari)
    OcrLanguage.Chinese.tag -> context.getString(R.string.ocr_lang_chinese)
    OcrLanguage.Japanese.tag -> context.getString(R.string.ocr_lang_japanese)
    OcrLanguage.Korean.tag -> context.getString(R.string.ocr_lang_korean)
    else -> tag
}

private fun messageToText(code: String, context: android.content.Context): String? = when {
    code.startsWith("freed:") -> context.getString(R.string.settings_cache_cleared)
    code.startsWith("backup_ok:") ->
        context.getString(R.string.backup_done, formatBytes(code.removePrefix("backup_ok:").toLongOrNull() ?: 0))
    code.startsWith("backup_fail:") ->
        context.getString(R.string.backup_failed, code.removePrefix("backup_fail:"))
    code == "restore_ok" -> context.getString(R.string.restore_done)
    code == "restore_wrong" -> context.getString(R.string.restore_wrong_password)
    code == "restore_invalid" -> context.getString(R.string.restore_invalid)
    code == "restore_incompatible" -> context.getString(R.string.restore_incompatible)
    code.startsWith("restore_fail:") ->
        context.getString(R.string.restore_failed, code.removePrefix("restore_fail:"))
    else -> null
}
