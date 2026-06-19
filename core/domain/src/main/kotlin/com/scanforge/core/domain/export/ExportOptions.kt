package com.scanforge.core.domain.export

import kotlinx.serialization.Serializable

/** What kind of artifact an export produces. */
enum class ExportKind {
    /** A PDF whose pages are the rendered images with an invisible, selectable OCR text layer. */
    SearchablePdf,

    /** A PDF whose pages are just the rendered images (no text layer). */
    ImagePdf,

    /** A flat `.txt` of the recognised text, pages separated by form feeds. */
    PlainText,
}

/**
 * Page geometry for PDF output. [A4] and [Letter] are fixed physical sizes (the image is fit inside,
 * preserving aspect); [Original] makes each PDF page exactly the source image's pixel size mapped at
 * 72 DPI; [Auto] is an alias of [Original] kept for a clearer label in the UI.
 */
enum class PdfPageSize(val widthPt: Float?, val heightPt: Float?) {
    A4(595.276f, 841.890f),
    Letter(612f, 792f),
    Auto(null, null),
    Original(null, null),
}

/** Colour treatment applied at export time, independent of each page's saved enhancement recipe. */
enum class ExportColorMode {
    /** Keep the page exactly as processed/captured. */
    Original,

    /** Convert to greyscale (smaller files, neutral tone). */
    Grayscale,

    /** Hard black-and-white threshold (smallest files, crisp text scans). */
    BlackAndWhite,
}

/**
 * JPEG re-encode preset trading file size against fidelity. [maxEdgePx] caps the longer edge of each
 * page image; [jpegQuality] is the encoder quality (`0..100`). Used both to render the export and to
 * estimate the resulting size before the user commits.
 */
@Serializable
enum class CompressionPreset(val jpegQuality: Int, val maxEdgePx: Int, val bytesPerPixel: Float) {
    /** Visually lossless; largest files. */
    HighQuality(92, 3000, 0.42f),

    /** Good balance of clarity and size (default). */
    Balanced(75, 2200, 0.22f),

    /** Aggressive compression for email/upload; smallest files. */
    Small(55, 1500, 0.11f);

    /**
     * Heuristic estimate of the exported byte size for pages with the given pixel areas. Each page is
     * first downscaled so its longer edge fits [maxEdgePx] (area scales by the square of that ratio),
     * then estimated at [bytesPerPixel]. A flat per-page container overhead is added. This is an
     * estimate shown before export, not an exact figure.
     */
    fun estimateBytes(pageAreasPx: List<Long>, longerEdges: List<Int>): Long {
        require(pageAreasPx.size == longerEdges.size) { "areas and edges must align per page" }
        var total = PER_DOCUMENT_OVERHEAD
        for (i in pageAreasPx.indices) {
            val area = pageAreasPx[i].coerceAtLeast(0L)
            val edge = longerEdges[i].coerceAtLeast(1)
            val scale = if (edge > maxEdgePx) maxEdgePx.toFloat() / edge else 1f
            val scaledArea = area * scale * scale
            total += (scaledArea * bytesPerPixel).toLong() + PER_PAGE_OVERHEAD
        }
        return total
    }

    private companion object {
        const val PER_PAGE_OVERHEAD = 1_200L
        const val PER_DOCUMENT_OVERHEAD = 2_500L
    }
}

/**
 * A fully specified export request payload. Serialized into the WorkManager job so the export can run
 * in the background and survive process death.
 *
 * @param pageIds the subset of pages to export in order, or `null`/empty for the whole document.
 * @param userPassword when non-blank (PDF kinds only), the PDF is encrypted with this open password.
 */
@Serializable
data class ExportOptions(
    val kind: ExportKind,
    val pageSize: PdfPageSize = PdfPageSize.Auto,
    val colorMode: ExportColorMode = ExportColorMode.Original,
    val compression: CompressionPreset = CompressionPreset.Balanced,
    val userPassword: String? = null,
    val pageIds: List<Long>? = null,
) {
    val isPdf: Boolean get() = kind == ExportKind.SearchablePdf || kind == ExportKind.ImagePdf
    val isEncrypted: Boolean get() = isPdf && !userPassword.isNullOrBlank()
}
