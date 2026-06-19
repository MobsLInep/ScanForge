package com.scanforge.core.data.mapper

import com.scanforge.core.domain.ocr.OcrBlock
import com.scanforge.core.domain.ocr.OcrDocument
import com.scanforge.core.domain.ocr.OcrLanguage
import com.scanforge.core.domain.ocr.OcrLine
import com.scanforge.core.domain.ocr.OcrWord
import com.scanforge.core.domain.ocr.TextBox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OcrJsonTest {

    @Test
    fun `encodes and decodes a structured result without loss`() {
        val original = OcrDocument.from(
            blocks = listOf(
                OcrBlock(
                    box = TextBox(0f, 0f, 1f, 0.5f),
                    lines = listOf(
                        OcrLine(
                            text = "Hello नमस्ते",
                            box = TextBox(0.1f, 0.1f, 0.9f, 0.2f),
                            confidence = 0.88f,
                            language = "und-Deva",
                            words = listOf(
                                OcrWord("Hello", TextBox(0.1f, 0.1f, 0.4f, 0.2f), 0.95f),
                                OcrWord("नमस्ते", TextBox(0.5f, 0.1f, 0.9f, 0.2f), 0.81f),
                            ),
                        ),
                    ),
                ),
            ),
            imageWidth = 1200,
            imageHeight = 1600,
            script = OcrLanguage.Devanagari,
        )

        val decoded = OcrJson.decode(OcrJson.encode(original))

        assertEquals(original, decoded)
        assertEquals("Hello नमस्ते", decoded!!.fullText)
        assertEquals(2, decoded.words.size)
        assertEquals(OcrLanguage.Devanagari, decoded.script)
    }

    @Test
    fun `null raw decodes to null`() {
        assertNull(OcrJson.decode(null))
    }

    @Test
    fun `malformed json decodes to null rather than throwing`() {
        assertNull(OcrJson.decode("{not valid"))
    }
}
