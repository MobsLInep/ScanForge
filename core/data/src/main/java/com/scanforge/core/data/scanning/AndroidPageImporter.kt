package com.scanforge.core.data.scanning

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.scanforge.core.common.dispatchers.Dispatcher
import com.scanforge.core.common.dispatchers.ScanForgeDispatcher
import com.scanforge.core.domain.model.NewPage
import com.scanforge.core.domain.scanning.PageImageStore
import com.scanforge.core.domain.scanning.PageImporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports images and PDFs picked through the Storage Access Framework (no storage permission). Each
 * resolved page is normalized to a JPEG and persisted via [PageImageStore], yielding [NewPage]s the
 * repository can turn into a document.
 */
@Singleton
class AndroidPageImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageStore: PageImageStore,
    @Dispatcher(ScanForgeDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : PageImporter {

    override suspend fun importImages(uris: List<String>): List<NewPage> = withContext(ioDispatcher) {
        uris.mapNotNull { uriString ->
            runCatching {
                val bytes = context.contentResolver.openInputStream(Uri.parse(uriString))
                    ?.use { it.readBytes() }
                    ?: return@runCatching null
                imageStore.savePage(bytes).toNewPage()
            }.onFailure { Log.w(TAG, "Failed to import image $uriString", it) }.getOrNull()
        }
    }

    override suspend fun importPdf(uri: String): List<NewPage> = withContext(ioDispatcher) {
        val pages = mutableListOf<NewPage>()
        runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page ->
                            val bytes = page.renderToJpegBytes()
                            pages += imageStore.savePage(bytes).toNewPage()
                        }
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Failed to import PDF $uri", it) }
        pages
    }

    /** Renders a PDF page onto an opaque white bitmap at [PDF_RENDER_SCALE]x and JPEG-encodes it. */
    private fun PdfRenderer.Page.renderToJpegBytes(): ByteArray {
        val width = (width * PDF_RENDER_SCALE).toInt().coerceAtLeast(1)
        val height = (height * PDF_RENDER_SCALE).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE) // PDFs render with transparency; flatten to white paper
        render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun com.scanforge.core.domain.scanning.StoredPageImages.toNewPage() = NewPage(
        originalImagePath = originalImagePath,
        thumbnailPath = thumbnailPath,
    )

    private companion object {
        const val TAG = "AndroidPageImporter"
        const val PDF_RENDER_SCALE = 2f
        const val JPEG_QUALITY = 90
    }
}
