package com.scanforge.core.export.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Parses the bundled Noto Sans Devanagari TTF so non-Latin recognised text can be embedded as a
 * Type0/Identity-H font with a ToUnicode CMap — making Hindi/Marathi text in the PDF selectable and
 * searchable. Unit tests run with the module directory as the working dir, so the asset is read from
 * `src/main/assets`.
 */
class EmbeddedTrueTypeFontTest {

    private lateinit var font: EmbeddedTrueTypeFont

    @BeforeEach
    fun setUp() {
        val ttf = File("src/main/assets/fonts/NotoSansDevanagari-Regular.ttf")
        assumeTrue(ttf.exists(), "bundled Devanagari font not found")
        font = EmbeddedTrueTypeFont.parse(ttf.readBytes(), "NotoDevanagari")
    }

    @Test
    fun `recognises that it can render Devanagari but not bare Latin`() {
        assertTrue(font.canRender("नमस्ते"), "should map Devanagari code points to glyphs")
        assertFalse(font.canRender("Hello"), "pure WinAnsi text should stay on the standard font")
    }

    @Test
    fun `maps each character to a two-byte glyph id`() {
        val hex = font.hexGlyphs("न") // single Devanagari letter NA (U+0928)
        assertEquals(4, hex.length, "one BMP char must encode to one 2-byte glyph id")
        assertNotEquals("0000", hex, "U+0928 must resolve to a real glyph, not .notdef")
    }

    @Test
    fun `ToUnicode CMap maps glyphs back to their code points`() {
        val cmap = font.toUnicodeCMap()
        assertTrue(cmap.contains("beginbfchar") || cmap.contains("beginbfrange"), cmap.take(200))
        assertTrue(cmap.uppercase().contains("0928"), "ToUnicode must let viewers recover U+0928 (न)")
    }

    @Test
    fun `exposes scaled font metrics in the 1000-unit em`() {
        assertTrue(font.ascent in 1..2000, "ascent ${font.ascent}")
        assertTrue(font.descent in -1000..0, "descent ${font.descent}")
        assertEquals(4, font.bbox.size)
    }
}
