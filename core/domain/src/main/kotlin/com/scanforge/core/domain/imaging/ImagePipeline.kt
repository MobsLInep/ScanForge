package com.scanforge.core.domain.imaging

/**
 * Applies a [PageProcessing] recipe (perspective crop + enhancement) to a source image and returns
 * the result as JPEG bytes. Kept in the domain — operating only on file paths, [PageProcessing], and
 * raw bytes — so the presentation layer never depends on OpenCV/Android imaging types.
 *
 * The same recipe drives both a downscaled live preview and the full-resolution export, which is
 * what makes editing non-destructive: the original on disk is never modified.
 */
interface ImagePipeline {
    /**
     * Renders [sourcePath] through [processing].
     *
     * @param maxEdge when non-null, the longer output edge is capped at this many pixels (downscale
     *   for a fast, memory-cheap preview). `null` renders at full resolution for export.
     * @return processed image encoded as JPEG, or `null` if the source could not be decoded or the
     *   imaging backend is unavailable.
     */
    suspend fun render(sourcePath: String, processing: PageProcessing, maxEdge: Int? = null): ByteArray?
}
