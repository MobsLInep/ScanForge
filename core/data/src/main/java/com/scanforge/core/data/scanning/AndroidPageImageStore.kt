package com.scanforge.core.data.scanning

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.scanforge.core.common.dispatchers.Dispatcher
import com.scanforge.core.common.dispatchers.ScanForgeDispatcher
import com.scanforge.core.domain.scanning.PageImageStore
import com.scanforge.core.domain.scanning.StoredPageImages
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores page images under app-private `filesDir` — nothing leaves the device. Full-res JPEGs go to
 * `pages/`, derived thumbnails to `thumbnails/`. Privacy-first: no external storage, no media-store,
 * no broad permissions.
 */
@Singleton
class AndroidPageImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(ScanForgeDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : PageImageStore {

    private val pagesDir: File get() = File(context.filesDir, "pages").apply { mkdirs() }
    private val thumbsDir: File get() = File(context.filesDir, "thumbnails").apply { mkdirs() }

    override suspend fun savePage(jpegBytes: ByteArray): StoredPageImages = withContext(ioDispatcher) {
        val id = UUID.randomUUID().toString()
        val original = File(pagesDir, "$id.jpg")
        original.writeBytes(jpegBytes)

        val thumb = File(thumbsDir, "$id.jpg")
        writeThumbnail(jpegBytes, thumb)

        StoredPageImages(
            originalImagePath = original.absolutePath,
            thumbnailPath = thumb.absolutePath,
        )
    }

    override suspend fun deletePageImages(vararg paths: String?) = withContext(ioDispatcher) {
        paths.filterNotNull().forEach { path -> runCatching { File(path).delete() } }
    }

    /** Decodes [jpegBytes] downsampled to ~[THUMB_MAX_EDGE]px and writes a compact JPEG. */
    private fun writeThumbnail(jpegBytes: ByteArray, dest: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)
        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, THUMB_MAX_EDGE)

        val decoded = BitmapFactory.decodeByteArray(
            jpegBytes, 0, jpegBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return

        try {
            dest.outputStream().use { out ->
                decoded.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
            }
        } finally {
            decoded.recycle()
        }
    }

    private fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (maxOf(w, h) / 2 >= maxEdge) {
            w /= 2; h /= 2; sample *= 2
        }
        return sample
    }

    private companion object {
        const val THUMB_MAX_EDGE = 320
        const val THUMB_QUALITY = 80
    }
}
