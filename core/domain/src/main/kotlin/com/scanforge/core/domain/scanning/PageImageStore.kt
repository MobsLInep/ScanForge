package com.scanforge.core.domain.scanning

/** The full-resolution image plus its derived thumbnail for one persisted page. */
data class StoredPageImages(
    val originalImagePath: String,
    val thumbnailPath: String,
)

/**
 * Persists captured/imported page images to app-private storage and derives thumbnails. Works only
 * in terms of raw JPEG bytes and file paths so the domain stays Android-free; the data-layer
 * implementation handles `filesDir`, `Bitmap`, and compression.
 */
interface PageImageStore {
    /**
     * Writes full-resolution [jpegBytes] to private storage and generates a thumbnail.
     *
     * @return the stored original + thumbnail paths.
     */
    suspend fun savePage(jpegBytes: ByteArray): StoredPageImages

    /** Deletes the files behind a page (original, thumbnail, and any processed variant). */
    suspend fun deletePageImages(vararg paths: String?)
}
