package com.scanforge.core.domain.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OcrDocumentTest {

    private fun word(text: String, conf: Float?) = OcrWord(text, TextBox.ZERO, conf)

    @Test
    fun `from derives full text by joining lines with newlines`() {
        val doc = OcrDocument.from(
            blocks = listOf(
                OcrBlock(
                    box = TextBox.ZERO,
                    lines = listOf(
                        OcrLine("Hello world", TextBox.ZERO, language = "und-Latn"),
                        OcrLine("नमस्ते", TextBox.ZERO, language = "und-Deva"),
                    ),
                ),
            ),
            imageWidth = 100,
            imageHeight = 200,
            script = OcrLanguage.Devanagari,
        )

        assertEquals("Hello world\nनमस्ते", doc.fullText)
        assertEquals(OcrLanguage.Devanagari, doc.script)
        assertEquals(100, doc.imageWidth)
        assertEquals(200, doc.imageHeight)
    }

    @Test
    fun `from averages word confidence and ignores nulls`() {
        val doc = OcrDocument.from(
            blocks = listOf(
                OcrBlock(
                    box = TextBox.ZERO,
                    lines = listOf(
                        OcrLine(
                            "a b",
                            TextBox.ZERO,
                            words = listOf(word("a", 0.9f), word("b", 0.7f)),
                        ),
                        OcrLine("c", TextBox.ZERO, words = listOf(word("c", null))),
                    ),
                ),
            ),
            imageWidth = 10,
            imageHeight = 10,
            script = OcrLanguage.Latin,
        )

        assertEquals(0.8f, doc.confidence!!, 1e-4f)
    }

    @Test
    fun `from reports null confidence when no word has one`() {
        val doc = OcrDocument.from(
            blocks = listOf(OcrBlock(TextBox.ZERO, listOf(OcrLine("x", TextBox.ZERO)))),
            imageWidth = 1,
            imageHeight = 1,
            script = OcrLanguage.Latin,
        )
        assertNull(doc.confidence)
    }

    @Test
    fun `recognized languages are ordered by frequency`() {
        val doc = OcrDocument.from(
            blocks = listOf(
                OcrBlock(
                    box = TextBox.ZERO,
                    lines = listOf(
                        OcrLine("1", TextBox.ZERO, language = "und-Latn"),
                        OcrLine("2", TextBox.ZERO, language = "und-Deva"),
                        OcrLine("3", TextBox.ZERO, language = "und-Latn"),
                    ),
                ),
            ),
            imageWidth = 1,
            imageHeight = 1,
            script = OcrLanguage.Devanagari,
        )
        assertEquals(listOf("und-Latn", "und-Deva"), doc.recognizedLanguages)
    }

    @Test
    fun `lines and words flatten across blocks`() {
        val doc = OcrDocument.from(
            blocks = listOf(
                OcrBlock(TextBox.ZERO, listOf(OcrLine("a", TextBox.ZERO, words = listOf(word("a", null))))),
                OcrBlock(TextBox.ZERO, listOf(OcrLine("b", TextBox.ZERO, words = listOf(word("b", null))))),
            ),
            imageWidth = 1,
            imageHeight = 1,
            script = OcrLanguage.Latin,
        )
        assertEquals(2, doc.lines.size)
        assertEquals(listOf("a", "b"), doc.words.map { it.text })
    }

    @Test
    fun `empty document reports isEmpty`() {
        assertTrue(OcrDocument.EMPTY.isEmpty)
    }
}
