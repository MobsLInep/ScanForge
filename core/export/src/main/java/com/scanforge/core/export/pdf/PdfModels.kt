package com.scanforge.core.export.pdf

/**
 * A page image to embed in a PDF as a `/DCTDecode` XObject. The JPEG bytes are embedded verbatim (no
 * re-encode), so [jpegBytes] must already be JPEG. [grayscale] selects the PDF colour space so a
 * single-channel JPEG isn't misread as RGB.
 */
data class PdfImage(
    val jpegBytes: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
    val grayscale: Boolean = false,
) {
    // Array-bearing data class: identity equality is fine here (instances are short-lived per export).
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

/** A rectangle in PDF points (origin bottom-left). */
data class PdfRect(val xPt: Float, val yPt: Float, val widthPt: Float, val heightPt: Float)

/**
 * One recognised text fragment to place in the invisible OCR layer. Positioned by its [xPt],
 * [baselineYPt] (bottom-left of the text), stretched horizontally to span [widthPt] so the selectable
 * text tracks the word on the page image, at [fontSizePt] roughly matching the word's height.
 */
data class PdfTextRun(
    val text: String,
    val xPt: Float,
    val baselineYPt: Float,
    val widthPt: Float,
    val fontSizePt: Float,
)

/**
 * One PDF page: the [image] placed at [imageRect] (defaults to filling the [pageWidthPt]×[pageHeightPt]
 * MediaBox), plus the invisible [textRuns] in page-point coordinates.
 */
data class PdfPageSpec(
    val image: PdfImage,
    val pageWidthPt: Float,
    val pageHeightPt: Float,
    val textRuns: List<PdfTextRun> = emptyList(),
    val imageRect: PdfRect? = null,
)

/**
 * Open-password protection for a PDF using the standard security handler (RC4-128, V2/R3). When
 * supplied, every string and stream in the document is encrypted and the file requires [userPassword]
 * to open. [ownerPassword] defaults to the user password (full-access owner).
 */
data class PdfSecurity(
    val userPassword: String,
    val ownerPassword: String = userPassword,
)
