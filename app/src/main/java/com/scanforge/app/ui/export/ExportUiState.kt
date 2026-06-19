package com.scanforge.app.ui.export

import com.scanforge.core.domain.export.CompressionPreset
import com.scanforge.core.domain.export.ExportColorMode
import com.scanforge.core.domain.export.ExportKind
import com.scanforge.core.domain.export.ExportOptions
import com.scanforge.core.domain.export.ExportProgress
import com.scanforge.core.domain.export.PdfPageSize
import java.util.Locale

/** Immutable UI state for the export sheet. */
data class ExportUiState(
    val kind: ExportKind = ExportKind.SearchablePdf,
    val pageSize: PdfPageSize = PdfPageSize.Auto,
    val colorMode: ExportColorMode = ExportColorMode.Original,
    val compression: CompressionPreset = CompressionPreset.Balanced,
    val passwordEnabled: Boolean = false,
    val password: String = "",
    val estimateBytes: Long? = null,
    val estimating: Boolean = false,
    val progress: ExportProgress = ExportProgress.Idle,
    val output: ExportProgress.Completed? = null,
) {
    val isPdf: Boolean get() = kind != ExportKind.PlainText
    val isExporting: Boolean get() = progress is ExportProgress.Running
    val isFailed: Boolean get() = progress is ExportProgress.Failed
    val canPreview: Boolean get() = output != null && !passwordEnabled && kind != ExportKind.PlainText

    fun toOptions(): ExportOptions = ExportOptions(
        kind = kind,
        pageSize = pageSize,
        colorMode = colorMode,
        compression = compression,
        userPassword = password.takeIf { passwordEnabled && it.isNotBlank() },
    )

    /** A human-readable size like "1.2 MB" from a byte count. */
    fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
