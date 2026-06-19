package com.scanforge.core.export.pdf

/**
 * A parsed TrueType font, embedded whole into the PDF as a Type0/Identity-H CID font so non-Latin
 * recognised text (Hindi/Marathi Devanagari, etc.) becomes selectable and searchable. Only the bits
 * needed for an invisible text layer are read: the `cmap` (Unicode→glyph id), `head`/`hhea` metrics,
 * and the raw program bytes for `/FontFile2`. No glyph shaping is needed — the layer is invisible, so
 * a base-glyph mapping plus a ToUnicode CMap is enough to make extraction faithful.
 *
 * Pure JVM (byte parsing only), so the embedding is unit-testable against the bundled font.
 */
class EmbeddedTrueTypeFont private constructor(
    val programBytes: ByteArray,
    val postScriptName: String,
    private val unicodeToGid: Map<Int, Int>,
    val ascent: Int,
    val descent: Int,
    val capHeight: Int,
    val bbox: List<Int>,
) {

    /** True when this font supplies glyphs for the non-ASCII characters in [text]. */
    fun canRender(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp > 0x7F && unicodeToGid.containsKey(cp)) return true
        }
        return false
    }

    /** Encodes [text] as a hex string of 2-byte glyph ids for an Identity-H `<....> Tj` show. */
    fun hexGlyphs(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            append("%04X".format(unicodeToGid[cp] ?: 0))
        }
    }

    /** A ToUnicode CMap mapping every mapped glyph id back to its Unicode code point. */
    fun toUnicodeCMap(): String {
        val gidToCp = LinkedHashMap<Int, Int>()
        for ((cp, gid) in unicodeToGid) if (gid != 0) gidToCp.putIfAbsent(gid, cp)
        val entries = gidToCp.entries.toList()
        val sb = StringBuilder()
        sb.append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
        sb.append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
        sb.append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
        sb.append("1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n")
        entries.chunked(100).forEach { chunk ->
            sb.append("${chunk.size} beginbfchar\n")
            for ((gid, cp) in chunk) sb.append("<%04X> <%s>\n".format(gid, cpHex(cp)))
            sb.append("endbfchar\n")
        }
        sb.append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
        return sb.toString()
    }

    companion object {
        fun parse(bytes: ByteArray, postScriptName: String): EmbeddedTrueTypeFont {
            val r = Reader(bytes)
            r.seek(4)
            val numTables = r.u16()
            r.seek(12)
            val tables = HashMap<String, Int>() // tag -> offset
            repeat(numTables) {
                val tag = r.tag()
                r.u32() // checksum
                val offset = r.u32()
                r.u32() // length
                tables[tag] = offset
            }

            val unitsPerEm = tables["head"]?.let { r.seek(it + 18); r.u16() } ?: 1000
            fun scale(v: Int) = Math.round(v * 1000.0 / unitsPerEm).toInt()

            val bbox = tables["head"]?.let {
                r.seek(it + 36)
                listOf(scale(r.s16()), scale(r.s16()), scale(r.s16()), scale(r.s16()))
            } ?: listOf(0, -200, 1000, 800)

            val ascent = tables["hhea"]?.let { r.seek(it + 4); scale(r.s16()) } ?: 800
            val descent = tables["hhea"]?.let { r.seek(it + 6); scale(r.s16()) } ?: -200
            val capHeight = tables["OS/2"]?.let { r.seek(it + 88); scale(r.s16()) }?.takeIf { it > 0 }
                ?: (ascent * 7 / 10)

            val cmap = tables["cmap"]?.let { parseCmap(r, it) } ?: emptyMap()
            return EmbeddedTrueTypeFont(bytes, postScriptName, cmap, ascent, descent, capHeight, bbox)
        }

        private fun parseCmap(r: Reader, cmapStart: Int): Map<Int, Int> {
            r.seek(cmapStart + 2)
            val n = r.u16()
            val subtableOffsets = ArrayList<Int>(n)
            repeat(n) {
                r.u16() // platformID
                r.u16() // encodingID
                subtableOffsets.add(cmapStart + r.u32())
            }
            // Merge every subtable format we understand; richer formats (12) take precedence.
            val map = HashMap<Int, Int>()
            for (off in subtableOffsets.sortedByDescending { r.apply { seek(it) }.u16() }) {
                r.seek(off)
                when (r.u16()) {
                    4 -> parseFormat4(r, off, map)
                    12 -> parseFormat12(r, off, map)
                    6 -> parseFormat6(r, off, map)
                    0 -> parseFormat0(r, off, map)
                }
            }
            return map
        }

        private fun parseFormat4(r: Reader, off: Int, out: MutableMap<Int, Int>) {
            r.seek(off + 6)
            val segX2 = r.u16()
            val segCount = segX2 / 2
            val endBase = off + 14
            val startBase = endBase + segX2 + 2
            val deltaBase = startBase + segX2
            val rangeBase = deltaBase + segX2
            for (s in 0 until segCount) {
                r.seek(endBase + s * 2); val end = r.u16()
                r.seek(startBase + s * 2); val start = r.u16()
                r.seek(deltaBase + s * 2); val delta = r.s16()
                r.seek(rangeBase + s * 2); val rangeOffset = r.u16()
                if (start > end) continue
                for (c in start..end) {
                    if (c == 0xFFFF) continue
                    val gid = if (rangeOffset == 0) {
                        (c + delta) and 0xFFFF
                    } else {
                        val glyphAddr = rangeBase + s * 2 + rangeOffset + (c - start) * 2
                        r.seek(glyphAddr)
                        val g = r.u16()
                        if (g == 0) 0 else (g + delta) and 0xFFFF
                    }
                    if (gid != 0) out.putIfAbsent(c, gid)
                }
            }
        }

        private fun parseFormat12(r: Reader, off: Int, out: MutableMap<Int, Int>) {
            r.seek(off + 12)
            val groups = r.u32()
            for (g in 0 until groups) {
                val start = r.u32(); val end = r.u32(); val startGid = r.u32()
                var c = start
                while (c <= end) {
                    out.putIfAbsent(c, startGid + (c - start))
                    c++
                }
            }
        }

        private fun parseFormat6(r: Reader, off: Int, out: MutableMap<Int, Int>) {
            r.seek(off + 6)
            val first = r.u16(); val count = r.u16()
            for (i in 0 until count) {
                val gid = r.u16()
                if (gid != 0) out.putIfAbsent(first + i, gid)
            }
        }

        private fun parseFormat0(r: Reader, off: Int, out: MutableMap<Int, Int>) {
            r.seek(off + 6)
            for (c in 0..255) {
                val gid = r.u8()
                if (gid != 0) out.putIfAbsent(c, gid)
            }
        }

        private fun cpHex(cp: Int): String =
            if (cp <= 0xFFFF) "%04X".format(cp) else {
                val v = cp - 0x10000
                "%04X%04X".format(0xD800 + (v shr 10), 0xDC00 + (v and 0x3FF))
            }
    }

    /** Minimal big-endian byte reader over the font program. */
    private class Reader(private val b: ByteArray) {
        private var p = 0
        fun seek(offset: Int) { p = offset }
        fun u8(): Int = b[p++].toInt() and 0xFF
        fun u16(): Int = (u8() shl 8) or u8()
        fun s16(): Int = u16().let { if (it >= 0x8000) it - 0x10000 else it }
        fun u32(): Int = (u16() shl 16) or u16()
        fun tag(): String = String(byteArrayOf(b[p++], b[p++], b[p++], b[p++]), Charsets.ISO_8859_1)
    }
}
