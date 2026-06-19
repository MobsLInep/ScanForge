package com.scanforge.core.export.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.Inflater

/**
 * The core Phase 5 guarantee: the generated PDF carries an **extractable, searchable** text layer.
 * These run on the plain JVM (no emulator) by inflating the PDF's content streams and asserting the
 * recognised words are present as real PDF text-showing operators, drawn in invisible render mode.
 */
class SearchablePdfWriterTest {

    private val writer = SearchablePdfWriter()

    private fun fakeImage() = PdfImage(
        jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()),
        widthPx = 100,
        heightPx = 160,
        grayscale = false,
    )

    private fun latinPage(vararg words: String) = PdfPageSpec(
        image = fakeImage(),
        pageWidthPt = 595f,
        pageHeightPt = 842f,
        textRuns = words.mapIndexed { i, w ->
            PdfTextRun(text = w, xPt = 72f, baselineYPt = 700f - i * 14f, widthPt = 120f, fontSizePt = 12f)
        },
    )

    /** Decompresses every FlateDecode stream in the PDF and returns the joined Latin-1 text. */
    private fun inflatedStreams(pdf: ByteArray): String {
        val text = String(pdf, Charsets.ISO_8859_1)
        val builder = StringBuilder()
        var idx = 0
        while (true) {
            val s = text.indexOf("stream", idx)
            if (s < 0) break
            // Skip the EOL after the `stream` keyword (\r\n or \n).
            var dataStart = s + "stream".length
            if (dataStart < text.length && text[dataStart] == '\r') dataStart++
            if (dataStart < text.length && text[dataStart] == '\n') dataStart++
            val e = text.indexOf("endstream", dataStart)
            if (e < 0) break
            val raw = pdf.copyOfRange(dataStart, e)
            runCatching {
                val inflater = Inflater()
                inflater.setInput(raw)
                val out = ByteArray(raw.size * 20 + 1024)
                val n = inflater.inflate(out)
                inflater.end()
                if (n > 0) builder.append(String(out, 0, n, Charsets.ISO_8859_1))
            }
            idx = e + "endstream".length
        }
        return builder.toString()
    }

    @Test
    fun `produces a well-formed PDF document`() {
        val pdf = writer.write(listOf(latinPage("Hello")))
        val head = String(pdf.copyOf(8), Charsets.ISO_8859_1)
        val tail = String(pdf.copyOfRange(pdf.size - 6, pdf.size), Charsets.ISO_8859_1)
        assertTrue(head.startsWith("%PDF-1."), "expected PDF header, got '$head'")
        assertTrue(tail.contains("%%EOF"), "expected %%EOF trailer, got '$tail'")
    }

    @Test
    fun `embeds recognised Latin words as extractable text`() {
        val pdf = writer.write(listOf(latinPage("Invoice", "Total")))
        val streams = inflatedStreams(pdf)
        assertTrue(streams.contains("(Invoice) Tj"), "missing extractable 'Invoice' text run")
        assertTrue(streams.contains("(Total) Tj"), "missing extractable 'Total' text run")
    }

    @Test
    fun `text layer is drawn in invisible render mode 3`() {
        val pdf = writer.write(listOf(latinPage("Secret")))
        val streams = inflatedStreams(pdf)
        assertTrue(streams.contains("3 Tr"), "OCR text must use invisible text render mode (3 Tr)")
    }

    @Test
    fun `escapes PDF-special characters in recognised text`() {
        val page = latinPage().copy(
            textRuns = listOf(
                PdfTextRun("a(b)c\\d", xPt = 72f, baselineYPt = 700f, widthPt = 80f, fontSizePt = 12f),
            ),
        )
        val streams = inflatedStreams(writer.write(listOf(page)))
        assertTrue(streams.contains("(a\\(b\\)c\\\\d) Tj"), "unescaped parens/backslash would corrupt the PDF")
    }

    @Test
    fun `image-only pages carry no text-showing operator`() {
        val pdf = writer.write(listOf(latinPage().copy(textRuns = emptyList())))
        val streams = inflatedStreams(pdf)
        assertFalse(streams.contains(" Tj"), "image-only PDF should have no text layer")
        assertTrue(streams.contains("/Im0 Do"), "image-only PDF should still draw the page image")
    }

    @Test
    fun `password protected pdf declares an encryption dictionary`() {
        val pdf = writer.write(listOf(latinPage("Secret")), PdfSecurity("pw"))
        val text = String(pdf, Charsets.ISO_8859_1)
        assertTrue(text.contains("/Encrypt"), "missing /Encrypt reference in trailer")
        assertTrue(text.contains("/Filter /Standard"), "missing standard security handler")
    }

    @Test
    fun `password protected pdf hides the recognised text from plain extraction`() {
        val pdf = writer.write(listOf(latinPage("TopSecret")), PdfSecurity("pw"))
        assertFalse(inflatedStreams(pdf).contains("(TopSecret) Tj"), "text leaked despite encryption")
    }

    @Test
    fun `embeds non-Latin recognised text via an embedded Type0 font`() {
        val ttf = File("src/main/assets/fonts/NotoSansDevanagari-Regular.ttf")
        assumeTrue(ttf.exists(), "bundled Devanagari font not found")
        val w = SearchablePdfWriter(EmbeddedTrueTypeFont.parse(ttf.readBytes(), "NotoDevanagari"))
        val page = PdfPageSpec(
            image = fakeImage(),
            pageWidthPt = 595f,
            pageHeightPt = 842f,
            textRuns = listOf(PdfTextRun("नमस्ते", xPt = 72f, baselineYPt = 700f, widthPt = 120f, fontSizePt = 14f)),
        )
        val pdf = w.write(listOf(page))
        val text = String(pdf, Charsets.ISO_8859_1)
        assertTrue(text.contains("/Subtype /Type0"), "Devanagari run should use a Type0 font")
        assertTrue(text.contains("/FontFile2"), "the TrueType program must be embedded")
        assertTrue(inflatedStreams(pdf).uppercase().contains("0928"), "ToUnicode must map back to U+0928")
    }

    @Test
    fun `each page becomes one PDF page`() {
        val pdf = writer.write(listOf(latinPage("a"), latinPage("b"), latinPage("c")))
        val text = String(pdf, Charsets.ISO_8859_1)
        assertEquals(3, Regex("/Type\\s*/Page[^s]").findAll(text).count(), "expected 3 /Page objects")
        assertTrue(text.contains("/Count 3"), "Pages tree should report 3 kids")
    }
}
