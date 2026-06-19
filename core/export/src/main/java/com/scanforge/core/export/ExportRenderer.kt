package com.scanforge.core.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.scanforge.core.domain.export.ExportColorMode
import com.scanforge.core.domain.export.ExportKind
import com.scanforge.core.domain.export.ExportOptions
import com.scanforge.core.domain.model.Page
import com.scanforge.core.domain.repository.DocumentRepository
import com.scanforge.core.export.pdf.EmbeddedTrueTypeFont
import com.scanforge.core.export.pdf.PdfImage
import com.scanforge.core.export.pdf.PdfPageSpec
import com.scanforge.core.export.pdf.PdfSecurity
import com.scanforge.core.export.pdf.SearchablePdfWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/** The produced export artifact. */
data class ExportArtifact(val file: File, val mimeType: String, val sizeBytes: Long)

/**
 * Turns a document's pages into a PDF (searchable or image-only) or a plain-text file. Pages are
 * rendered one at a time and their bitmaps recycled immediately, so large documents export without
 * holding every decoded page in memory at once. Colour treatment and JPEG compression are applied per
 * the [ExportOptions]; the invisible OCR layer reuses each page's stored normalized word boxes.
 */
class ExportRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
) {

    private val devanagariFont: EmbeddedTrueTypeFont? by lazy {
        runCatching {
            context.assets.open("fonts/NotoSansDevanagari-Regular.ttf").use {
                EmbeddedTrueTypeFont.parse(it.readBytes(), "NotoSansDevanagari")
            }
        }.getOrNull()
    }

    /** Renders [documentId] under [options], reporting progress as each page completes. */
    suspend fun render(
        documentId: Long,
        options: ExportOptions,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): ExportArtifact {
        val pages = orderedPages(documentId, options)
        require(pages.isNotEmpty()) { "no pages to export" }
        val title = repository.observeDocument(documentId).firstOrNull()?.title ?: "ScanForge"
        val baseName = sanitize(title)

        return if (options.kind == ExportKind.PlainText) {
            renderText(pages, baseName, onProgress)
        } else {
            renderPdf(pages, options, baseName, onProgress)
        }
    }

    /** Cheap pre-export size estimate that decodes only image bounds, never full bitmaps. */
    suspend fun estimateSize(documentId: Long, options: ExportOptions): Long {
        val pages = orderedPages(documentId, options)
        if (pages.isEmpty()) return 0L
        if (options.kind == ExportKind.PlainText) {
            return pages.sumOf { (pageText(it).toByteArray().size + 2).toLong() }
        }
        val areas = ArrayList<Long>(pages.size)
        val edges = ArrayList<Int>(pages.size)
        for (page in pages) {
            val (w, h) = imageBounds(sourcePath(page))
            areas.add(w.toLong() * h.toLong())
            edges.add(maxOf(w, h))
        }
        return options.compression.estimateBytes(areas, edges)
    }

    // ── PDF ────────────────────────────────────────────────────────────────────────────────────

    private fun renderPdf(
        pages: List<Page>,
        options: ExportOptions,
        baseName: String,
        onProgress: (Int, Int) -> Unit,
    ): ExportArtifact {
        val specs = ArrayList<PdfPageSpec>(pages.size)
        pages.forEachIndexed { index, page ->
            val bitmap = decodeScaled(sourcePath(page), options.compression.maxEdgePx)
                ?: return@forEachIndexed
            val recolored = applyColor(bitmap, options.colorMode)
            val jpeg = recolored.toJpeg(options.compression.jpegQuality)
            val widthPx = recolored.width
            val heightPx = recolored.height
            if (recolored !== bitmap) bitmap.recycle()
            recolored.recycle()

            val box = ExportLayout.pageBox(options.pageSize, widthPx, heightPx)
            val runs = if (options.kind == ExportKind.SearchablePdf) {
                ExportLayout.textRuns(page.ocrData?.words.orEmpty(), box.imageRect)
            } else {
                emptyList()
            }
            specs.add(
                PdfPageSpec(
                    image = PdfImage(jpeg, widthPx, heightPx, grayscale = false),
                    pageWidthPt = box.widthPt,
                    pageHeightPt = box.heightPt,
                    textRuns = runs,
                    imageRect = box.imageRect,
                ),
            )
            onProgress(index + 1, pages.size)
        }

        val writer = SearchablePdfWriter(devanagariFont)
        val security = options.userPassword?.takeIf { it.isNotBlank() }?.let { PdfSecurity(it) }
        val bytes = writer.write(specs, security)

        val file = outputFile("$baseName.pdf")
        file.writeBytes(bytes)
        return ExportArtifact(file, "application/pdf", file.length())
    }

    private fun renderText(
        pages: List<Page>,
        baseName: String,
        onProgress: (Int, Int) -> Unit,
    ): ExportArtifact {
        val sb = StringBuilder()
        pages.forEachIndexed { index, page ->
            if (index > 0) sb.append("\n\n") // form feed between pages
            sb.append(pageText(page))
            onProgress(index + 1, pages.size)
        }
        val file = outputFile("$baseName.txt")
        file.writeText(sb.toString())
        return ExportArtifact(file, "text/plain", file.length())
    }

    // ── image processing ─────────────────────────────────────────────────────────────────────────

    private fun decodeScaled(path: String, maxEdge: Int): Bitmap? {
        val (w, h) = imageBounds(path)
        if (w <= 0 || h <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(w, h, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, opts) ?: return null
        val longer = maxOf(decoded.width, decoded.height)
        if (longer <= maxEdge) return decoded
        val scale = maxEdge.toFloat() / longer
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun applyColor(src: Bitmap, mode: ExportColorMode): Bitmap = when (mode) {
        ExportColorMode.Original -> src
        ExportColorMode.Grayscale -> src.desaturated()
        ExportColorMode.BlackAndWhite -> src.thresholded()
    }

    private fun Bitmap.desaturated(): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        Canvas(out).drawBitmap(this, 0f, 0f, paint)
        return out
    }

    private fun Bitmap.thresholded(): Bitmap {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val luma = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            val v = if (luma >= 128) 0xFF else 0x00
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray = ByteArrayOutputStream().use {
        compress(Bitmap.CompressFormat.JPEG, quality, it)
        it.toByteArray()
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private suspend fun orderedPages(documentId: Long, options: ExportOptions): List<Page> {
        val all = repository.getDocumentPages(documentId)
        val ids = options.pageIds
        if (ids.isNullOrEmpty()) return all
        val byId = all.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    private fun pageText(page: Page): String =
        page.extractedText?.takeIf { it.isNotBlank() } ?: page.ocrData?.fullText.orEmpty()

    private fun sourcePath(page: Page): String = page.processedImagePath ?: page.originalImagePath

    private fun imageBounds(path: String): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        return opts.outWidth to opts.outHeight
    }

    private fun sampleSizeFor(w: Int, h: Int, maxEdge: Int): Int {
        var sample = 1
        var longer = maxOf(w, h)
        while (longer / 2 >= maxEdge) {
            longer /= 2
            sample *= 2
        }
        return sample
    }

    private fun outputFile(name: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, name)
    }

    private fun sanitize(title: String): String =
        title.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").replace(Regex("\\s+"), "_")
            .ifBlank { "ScanForge" }.take(60)
}
