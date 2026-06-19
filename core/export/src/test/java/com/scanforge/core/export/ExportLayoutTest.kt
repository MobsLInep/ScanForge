package com.scanforge.core.export

import com.scanforge.core.domain.export.PdfPageSize
import com.scanforge.core.domain.ocr.OcrWord
import com.scanforge.core.domain.ocr.TextBox
import com.scanforge.core.export.pdf.PdfRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Pure geometry that places page images and the invisible OCR layer; the alignment correctness core. */
class ExportLayoutTest {

    @Test
    fun `Original size maps pixels to points and fills the page`() {
        val box = ExportLayout.pageBox(PdfPageSize.Original, widthPx = 600, heightPx = 800)
        assertEquals(600f, box.widthPt, 0.01f)
        assertEquals(800f, box.heightPt, 0.01f)
        assertEquals(PdfRect(0f, 0f, 600f, 800f), box.imageRect)
    }

    @Test
    fun `A4 fits a square image preserving aspect and centres it`() {
        val box = ExportLayout.pageBox(PdfPageSize.A4, widthPx = 1000, heightPx = 1000)
        assertEquals(595.276f, box.widthPt, 0.01f)
        assertEquals(841.890f, box.heightPt, 0.01f)
        // Width-bound: the square spans the full A4 width and is centred vertically.
        assertEquals(595.276f, box.imageRect.widthPt, 0.5f)
        assertEquals(595.276f, box.imageRect.heightPt, 0.5f)
        assertEquals(0f, box.imageRect.xPt, 0.5f)
        assertTrue(box.imageRect.yPt > 100f, "square should be vertically centred on A4, got y=${box.imageRect.yPt}")
    }

    @Test
    fun `a top-left word maps to the top of the page in bottom-origin PDF space`() {
        val word = OcrWord(text = "Hi", box = TextBox(left = 0f, top = 0f, right = 0.5f, bottom = 0.1f), confidence = 0.9f)
        val runs = ExportLayout.textRuns(listOf(word), PdfRect(0f, 0f, 600f, 800f))
        val run = runs.single()
        assertEquals("Hi", run.text)
        assertEquals(0f, run.xPt, 0.1f)
        assertEquals(300f, run.widthPt, 0.1f) // 0.5 * 600
        assertEquals(80f, run.fontSizePt, 1f) // 0.1 * 800
        // Box occupies PDF y in [720, 800]; baseline sits just above the bottom edge.
        assertTrue(run.baselineYPt in 726f..740f, "baseline=${run.baselineYPt}")
    }

    @Test
    fun `blank words are dropped from the text layer`() {
        val words = listOf(
            OcrWord("  ", TextBox(0f, 0f, 0.1f, 0.1f), null),
            OcrWord("ok", TextBox(0.2f, 0.2f, 0.4f, 0.3f), null),
        )
        assertEquals(1, ExportLayout.textRuns(words, PdfRect(0f, 0f, 100f, 100f)).size)
    }
}
