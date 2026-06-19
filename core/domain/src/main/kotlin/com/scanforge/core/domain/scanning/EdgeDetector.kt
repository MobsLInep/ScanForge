package com.scanforge.core.domain.scanning

/**
 * Finds the document page within a captured image. The primary implementation uses OpenCV contour
 * detection; an ML Kit Document Scanner path can be swapped in behind this same interface. Kept in
 * the domain (operating only on file paths and [DetectedQuad]) so the presentation layer never
 * depends on OpenCV or Android imaging types directly.
 */
interface EdgeDetector {
    /**
     * Detects the most likely page quad in the image at [imagePath].
     *
     * @return the detected quad in normalized coordinates, or `null` when no confident
     *   quadrilateral is found — callers fall back to [DetectedQuad.FULL_FRAME] / manual crop.
     */
    suspend fun detectQuad(imagePath: String): DetectedQuad?
}
