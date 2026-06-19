package com.scanforge.core.export

import com.scanforge.core.domain.export.PdfPageSize
import com.scanforge.core.domain.ocr.OcrWord
import com.scanforge.core.export.pdf.PdfRect
import com.scanforge.core.export.pdf.PdfTextRun

/**
 * Pure geometry shared by the export renderer: it computes each PDF page box and image placement, and
 * maps normalized OCR word boxes (top-left origin, `0..1`) into PDF text runs (bottom-left origin,
 * points). Kept free of Android types so the alignment of the searchable layer is unit-testable.
 */
object ExportLayout {

    /** A PDF page's size plus where the page image is drawn within it. */
    data class PageBox(val widthPt: Float, val heightPt: Float, val imageRect: PdfRect)

    /**
     * Computes the page box for an image of [widthPx]×[heightPx] under [pageSize]. Fixed sizes (A4,
     * Letter) fit the image inside, preserving aspect and centring; [PdfPageSize.Original] and
     * [PdfPageSize.Auto] make the page the image's pixel size at 72 DPI (image fills the page).
     */
    fun pageBox(pageSize: PdfPageSize, widthPx: Int, heightPx: Int): PageBox {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val fixedW = pageSize.widthPt
        val fixedH = pageSize.heightPt
        if (fixedW == null || fixedH == null) {
            return PageBox(w.toFloat(), h.toFloat(), PdfRect(0f, 0f, w.toFloat(), h.toFloat()))
        }
        val scale = minOf(fixedW / w, fixedH / h)
        val rectW = w * scale
        val rectH = h * scale
        val rect = PdfRect((fixedW - rectW) / 2f, (fixedH - rectH) / 2f, rectW, rectH)
        return PageBox(fixedW, fixedH, rect)
    }

    /**
     * Maps each non-blank [words] box into a [PdfTextRun] placed inside [rect]. The normalized box's
     * top edge becomes the higher PDF-y (bottom-origin); the baseline is set just above the box's
     * bottom so selection rectangles track the visible word. Font size approximates the box height.
     */
    fun textRuns(words: List<OcrWord>, rect: PdfRect): List<PdfTextRun> = words.mapNotNull { word ->
        if (word.text.isBlank()) return@mapNotNull null
        val b = word.box
        val widthPt = b.width * rect.widthPt
        val heightPt = b.height * rect.heightPt
        if (widthPt <= 0f || heightPt <= 0f) return@mapNotNull null
        val xPt = rect.xPt + b.left * rect.widthPt
        val bottomPt = rect.yPt + rect.heightPt - b.bottom * rect.heightPt
        PdfTextRun(
            text = word.text,
            xPt = xPt,
            baselineYPt = bottomPt + 0.15f * heightPt,
            widthPt = widthPt,
            fontSizePt = heightPt,
        )
    }
}
