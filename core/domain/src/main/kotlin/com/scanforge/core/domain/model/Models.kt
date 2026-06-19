package com.scanforge.core.domain.model

import com.scanforge.core.domain.imaging.PageProcessing
import com.scanforge.core.domain.ocr.OcrDocument
import java.time.Instant

/** Lifecycle of OCR for a page or, aggregated, a whole document. */
enum class OcrStatus {
    NotStarted,
    Queued,
    InProgress,
    Completed,
    Failed,
}

/** A user-defined label that can be attached to many documents. */
data class Tag(
    val id: Long,
    val name: String,
    /** Optional `#RRGGBB` colour; `null` falls back to a theme default. */
    val colorHex: String? = null,
)

/** Structured OCR output for a single [Page]. Populated during the OCR phase. */
data class OcrResult(
    val fullText: String,
    val recognizedLanguages: List<String> = emptyList(),
    /** Engine confidence in `0f..1f`, or `null` when the engine does not report one. */
    val confidence: Float? = null,
)

/** A single scanned page belonging to a [Document]. */
data class Page(
    val id: Long,
    val documentId: Long,
    /** Zero-based position within the document. */
    val pageOrder: Int,
    val originalImagePath: String,
    val processedImagePath: String? = null,
    val thumbnailPath: String? = null,
    val extractedText: String? = null,
    /**
     * The chosen OCR language selection for this page, as an [com.scanforge.core.domain.ocr.OcrLanguageMode]
     * storage tag (`"Auto"` or a script tag). Drives "Re-run OCR"; `null` means the default (Auto).
     */
    val language: String? = null,
    val ocrStatus: OcrStatus = OcrStatus.NotStarted,
    /**
     * Structured OCR output (block/line/word boxes + confidence) once recognised, or `null` before
     * OCR runs. Powers the text view, confidence heatmap, and the future searchable-PDF text layer.
     */
    val ocrData: OcrDocument? = null,
    /**
     * The re-editable edit recipe that produced [processedImagePath], or `null` if the page has not
     * been enhanced yet. Stored so any edit can be reopened and changed non-destructively.
     */
    val processing: PageProcessing? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** A multi-page scanned document. [pages] may be empty when loaded as a list summary. */
data class Document(
    val id: Long,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val ocrStatus: OcrStatus = OcrStatus.NotStarted,
    val pageCount: Int = 0,
    val pages: List<Page> = emptyList(),
    val tags: List<Tag> = emptyList(),
    /** Starred by the user; surfaces in the Favourites section and survives sort/filter. */
    val isFavorite: Boolean = false,
    /** Containing folder id, or `null` when the document sits at the library root. */
    val folderId: Long? = null,
    /** Sum of the page image file sizes in bytes, kept current by the data layer for size sorting. */
    val sizeBytes: Long = 0,
    /** When non-`null`, the document is in the trash (soft-deleted) and was moved there at this time. */
    val deletedAt: Instant? = null,
    /** Thumbnail of the first page, mirrored onto the document for fast list rendering. */
    val thumbnailPath: String? = null,
) {
    val isTrashed: Boolean get() = deletedAt != null

    /** Distinct OCR languages recognised across the pages, in first-seen order. */
    val languages: List<String>
        get() = pages.flatMap { page -> page.ocrData?.recognizedLanguages ?: emptyList() }.distinct()
}

/** Output formats a document can be exported to. */
enum class ExportFormat {
    Pdf,
    SearchablePdf,
    PlainText,
    Images,
}

/** Colour processing applied to captured pages. */
enum class ColorMode {
    Original,
    Grayscale,
    BlackAndWhite,
    Enhanced,
}

/** Accent colour the user can pick for primary surfaces/CTAs. */
enum class AccentColor {
    /** ScanForge brand amber (default). */
    Amber,

    /** Brand teal — swaps the primary/secondary roles for a cooler look. */
    Teal,
}

/** Default export compression/quality preset, mirrored from the export module's presets. */
enum class ExportQuality {
    High,
    Balanced,
    Small,
}

/** User-configurable scanning/processing defaults, surfaced from DataStore. */
data class ScanSettings(
    val colorMode: ColorMode = ColorMode.Enhanced,
    val defaultExportFormat: ExportFormat = ExportFormat.SearchablePdf,
    /** Default compression preset applied when exporting. */
    val defaultExportQuality: ExportQuality = ExportQuality.Balanced,
    /** Default enhancement filter pre-selected in the page editor for new scans. */
    val defaultScanFilter: com.scanforge.core.domain.imaging.EnhancementFilter =
        com.scanforge.core.domain.imaging.EnhancementFilter.Auto,
    /** Run OCR automatically after each capture. */
    val autoOcr: Boolean = true,
    /** Preferred OCR script/language tags, in priority order. */
    val ocrLanguages: List<String> = listOf("Latin"),
    /** Brand default is dark; `null` means follow the system setting. */
    val darkTheme: Boolean? = true,
    /** Accent colour for primary surfaces. */
    val accent: AccentColor = AccentColor.Amber,
    /** Show the rule-of-thirds grid overlay by default in the camera. */
    val gridOverlayDefault: Boolean = false,
    /** Keep the untouched original alongside the enhanced page (non-destructive). */
    val saveOriginal: Boolean = true,
    /** Days a document stays in the trash before it is auto-purged. */
    val trashRetentionDays: Int = 30,
    /** Opt-in cloud sync. OFF by default; turning it on still requires a configured provider. */
    val syncEnabled: Boolean = false,
    /** Opt-in, privacy-respecting product analytics. OFF by default; nothing leaves the device. */
    val analyticsEnabled: Boolean = false,
    /** Opt-in crash reporting. OFF by default; with no reporter configured this stays a no-op. */
    val crashReportingEnabled: Boolean = false,
)
