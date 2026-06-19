package com.scanforge.core.export.pdf

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.Deflater

/**
 * A small, self-contained PDF 1.7 writer (no third-party library) that produces image-backed pages
 * with an optional **invisible, selectable OCR text layer**, and optional standard-security-handler
 * encryption. The text layer is real PDF text drawn in render mode 3 (neither filled nor stroked), so
 * it is invisible on screen yet fully selectable and searchable in any PDF viewer.
 *
 * Page images are embedded verbatim as `/DCTDecode` (JPEG) XObjects — no pixel re-encode here. The
 * caller (the Android export renderer) produces the JPEGs, picks the colour space, and maps normalized
 * OCR boxes to text-run positions.
 *
 * Pure JVM (only `java.*`), which keeps the searchable-layer guarantee unit-testable without an
 * emulator. Latin text uses the standard Helvetica font (WinAnsi); runs needing other scripts are
 * routed to an [embeddedFont] (a Type0 Identity-H CID font) when one is provided.
 */
class SearchablePdfWriter(
    /** Optional embedded TrueType font for non-Latin (e.g. Devanagari) selectable text. */
    private val embeddedFont: EmbeddedTrueTypeFont? = null,
) {

    fun write(pages: List<PdfPageSpec>, security: PdfSecurity? = null): ByteArray {
        val objects = ArrayList<PdfObject>()
        fun reserve(): PdfObject = PdfObject(objects.size + 1).also { objects.add(it) }

        val catalog = reserve()
        val pagesNode = reserve()
        val helvetica = reserve()
        helvetica.dict = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"

        val needsEmbedded = embeddedFont != null && pages.any { p ->
            p.textRuns.any { embeddedFont.canRender(it.text) && it.text.any { c -> !isWinAnsi(c) } }
        }
        val type0: PdfObject? = if (needsEmbedded) appendEmbeddedFont(embeddedFont!!, ::reserve) else null

        val pageRefs = ArrayList<Int>()
        for (page in pages) {
            val img = reserve()
            img.dict = buildString {
                append("<< /Type /XObject /Subtype /Image")
                append(" /Width ${page.image.widthPx} /Height ${page.image.heightPx}")
                append(" /ColorSpace ${if (page.image.grayscale) "/DeviceGray" else "/DeviceRGB"}")
                append(" /BitsPerComponent 8 /Filter /DCTDecode")
                append(" /Length ${page.image.jpegBytes.size} >>")
            }
            img.stream = page.image.jpegBytes

            val content = reserve()
            val deflated = deflate(buildContentStream(page, type0 != null))
            content.dict = "<< /Length ${deflated.size} /Filter /FlateDecode >>"
            content.stream = deflated

            val pageObj = reserve()
            pageRefs.add(pageObj.num)
            pageObj.dict = buildString {
                append("<< /Type /Page /Parent ${pagesNode.num} 0 R")
                append(" /MediaBox [0 0 ${fmt(page.pageWidthPt)} ${fmt(page.pageHeightPt)}]")
                append(" /Resources << /XObject << /Im0 ${img.num} 0 R >>")
                append(" /Font << /F1 ${helvetica.num} 0 R")
                if (type0 != null) append(" /F2 ${type0.num} 0 R")
                append(" >> >>")
                append(" /Contents ${content.num} 0 R >>")
            }
        }

        pagesNode.dict =
            "<< /Type /Pages /Count ${pages.size} /Kids [${pageRefs.joinToString(" ") { "$it 0 R" }}] >>"
        catalog.dict = "<< /Type /Catalog /Pages ${pagesNode.num} 0 R >>"

        val fileId = md5(("ScanForge" + System.nanoTime() + pages.size).toByteArray())
        val encryptor = security?.let { sec ->
            val handler = PdfEncryptor.create(sec, fileId)
            val enc = reserve()
            enc.dict = handler.encryptDictionary()
            enc.exemptFromEncryption = true
            handler
        }

        return serialize(objects, catalog.num, encryptor, fileId)
    }

    // ── content stream ─────────────────────────────────────────────────────────────────────────

    private fun buildContentStream(page: PdfPageSpec, hasEmbedded: Boolean): ByteArray {
        val rect = page.imageRect ?: PdfRect(0f, 0f, page.pageWidthPt, page.pageHeightPt)
        val sb = StringBuilder()
        sb.append("q\n")
        sb.append("${fmt(rect.widthPt)} 0 0 ${fmt(rect.heightPt)} ${fmt(rect.xPt)} ${fmt(rect.yPt)} cm\n")
        sb.append("/Im0 Do\n")
        sb.append("Q\n")

        val runs = page.textRuns.filter { it.text.isNotBlank() }
        if (runs.isNotEmpty()) {
            sb.append("BT\n")
            sb.append("3 Tr\n") // invisible: neither fill nor stroke
            for (run in runs) {
                val useEmbedded = hasEmbedded && embeddedFont != null &&
                    embeddedFont.canRender(run.text) && run.text.any { !isWinAnsi(it) }
                val fontTag = if (useEmbedded) "/F2" else "/F1"
                val tz = ((run.widthPt / naturalWidth(run, useEmbedded)) * 100f).coerceIn(10f, 1000f)
                sb.append("$fontTag ${fmt(run.fontSizePt)} Tf\n")
                sb.append("${fmt(tz)} Tz\n")
                sb.append("1 0 0 1 ${fmt(run.xPt)} ${fmt(run.baselineYPt)} Tm\n")
                if (useEmbedded) {
                    sb.append("<${embeddedFont!!.hexGlyphs(run.text)}> Tj\n")
                } else {
                    sb.append("(${escapeLatin(run.text)}) Tj\n")
                }
            }
            sb.append("ET\n")
        }
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun naturalWidth(run: PdfTextRun, embedded: Boolean): Float {
        val perChar = if (embedded) 1f else 0.5f // crude em width; only affects horizontal stretch
        return run.text.length.coerceAtLeast(1) * perChar * run.fontSizePt
    }

    private fun appendEmbeddedFont(font: EmbeddedTrueTypeFont, reserve: () -> PdfObject): PdfObject {
        val type0 = reserve()
        val cidFont = reserve()
        val descriptor = reserve()
        val fontFile = reserve()
        val toUnicode = reserve()

        val ttf = font.programBytes
        val deflated = deflate(ttf)
        fontFile.dict = "<< /Length ${deflated.size} /Length1 ${ttf.size} /Filter /FlateDecode >>"
        fontFile.stream = deflated

        descriptor.dict = buildString {
            append("<< /Type /FontDescriptor /FontName /${font.postScriptName} /Flags 4")
            append(" /FontBBox [${font.bbox.joinToString(" ")}]")
            append(" /ItalicAngle 0 /Ascent ${font.ascent} /Descent ${font.descent}")
            append(" /CapHeight ${font.capHeight} /StemV 80 /FontFile2 ${fontFile.num} 0 R >>")
        }
        cidFont.dict = buildString {
            append("<< /Type /Font /Subtype /CIDFontType2 /BaseFont /${font.postScriptName}")
            append(" /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >>")
            append(" /FontDescriptor ${descriptor.num} 0 R /CIDToGIDMap /Identity /DW 1000 >>")
        }
        val cmapDeflated = deflate(font.toUnicodeCMap().toByteArray(Charsets.ISO_8859_1))
        toUnicode.dict = "<< /Length ${cmapDeflated.size} /Filter /FlateDecode >>"
        toUnicode.stream = cmapDeflated

        type0.dict = buildString {
            append("<< /Type /Font /Subtype /Type0 /BaseFont /${font.postScriptName}")
            append(" /Encoding /Identity-H /DescendantFonts [${cidFont.num} 0 R]")
            append(" /ToUnicode ${toUnicode.num} 0 R >>")
        }
        return type0
    }

    // ── serialization ──────────────────────────────────────────────────────────────────────────

    private fun serialize(
        objects: List<PdfObject>,
        rootNum: Int,
        encryptor: PdfEncryptor?,
        fileId: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        fun write(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))

        write("%PDF-1.7\n")
        out.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte()))
        write("\n")

        val offsets = IntArray(objects.size + 1)
        for (obj in objects) {
            offsets[obj.num] = out.size()
            write("${obj.num} 0 obj\n")
            write(obj.dict)
            val stream = obj.stream
            if (stream != null) {
                val data = if (encryptor != null && !obj.exemptFromEncryption) {
                    encryptor.encrypt(obj.num, 0, stream)
                } else {
                    stream
                }
                write("\nstream\n")
                out.write(data)
                write("\nendstream")
            }
            write("\nendobj\n")
        }

        val xrefOffset = out.size()
        val size = objects.size + 1
        write("xref\n")
        write("0 $size\n")
        write("0000000000 65535 f \n")
        for (i in 1 until size) write(String.format(Locale.US, "%010d 00000 n \n", offsets[i]))
        write("trailer\n")
        val idHex = fileId.toHex()
        val encRef = if (encryptor != null) " /Encrypt ${objects.last().num} 0 R" else ""
        write("<< /Size $size /Root $rootNum 0 R$encRef /ID [<$idHex> <$idHex>] >>\n")
        write("startxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }

    private class PdfObject(val num: Int) {
        var dict: String = ""
        var stream: ByteArray? = null
        var exemptFromEncryption: Boolean = false
    }

    companion object {
        private fun deflate(data: ByteArray): ByteArray {
            val deflater = Deflater(Deflater.BEST_COMPRESSION)
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(data.size / 2 + 64)
            val buf = ByteArray(16 * 1024)
            while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
            deflater.end()
            return out.toByteArray()
        }

        private fun md5(data: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(data)

        private fun fmt(v: Float): String {
            val s = String.format(Locale.US, "%.2f", v)
            return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
        }

        private fun isWinAnsi(c: Char): Boolean = c.code in 0x20..0x7E || c.code in 0xA0..0xFF

        private fun escapeLatin(text: String): String = buildString {
            for (c in text) when (c) {
                '\\' -> append("\\\\")
                '(' -> append("\\(")
                ')' -> append("\\)")
                '\r' -> append("\\r")
                '\n' -> append("\\n")
                else -> if (isWinAnsi(c)) append(c) else append(' ')
            }
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
