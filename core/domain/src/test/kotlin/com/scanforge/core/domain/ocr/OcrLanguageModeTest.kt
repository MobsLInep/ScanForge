package com.scanforge.core.domain.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OcrLanguageModeTest {

    @Test
    fun `auto round-trips through its storage tag`() {
        assertEquals("Auto", OcrLanguageMode.Auto.storageTag())
        assertEquals(OcrLanguageMode.Auto, OcrLanguageMode.fromTag("Auto"))
    }

    @Test
    fun `manual selection round-trips through its script tag`() {
        val mode = OcrLanguageMode.Manual(OcrLanguage.Devanagari)
        assertEquals("Devanagari", mode.storageTag())
        assertEquals(mode, OcrLanguageMode.fromTag("Devanagari"))
    }

    @Test
    fun `null or blank tag decodes to Auto`() {
        assertEquals(OcrLanguageMode.Auto, OcrLanguageMode.fromTag(null))
        assertEquals(OcrLanguageMode.Auto, OcrLanguageMode.fromTag(""))
    }

    @Test
    fun `unknown tag falls back to Auto`() {
        assertEquals(OcrLanguageMode.Auto, OcrLanguageMode.fromTag("Klingon"))
    }

    @Test
    fun `language tag lookup is case-insensitive`() {
        assertEquals(OcrLanguage.Latin, OcrLanguage.fromTag("latin"))
    }
}
