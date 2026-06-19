package com.scanforge.core.imaging.geometry

import com.scanforge.core.domain.scanning.DetectedQuad
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure-Kotlin helpers that turn a normalized [DetectedQuad] into the pixel-space geometry the warp
 * needs: the four source corners and the size of the flattened output rectangle. No OpenCV/Android,
 * so it is fully unit-testable.
 */
object QuadGeometry {

    /** The flattened output dimensions for a warped page, in pixels (always ≥ 1). */
    data class OutputSize(val width: Int, val height: Int)

    /** Projects a normalized quad onto a [srcWidth] x [srcHeight] image, ordered TL, TR, BR, BL. */
    fun cornersInPixels(quad: DetectedQuad, srcWidth: Int, srcHeight: Int): List<Point2> =
        quad.corners.map { Point2(it.x.toDouble() * srcWidth, it.y.toDouble() * srcHeight) }

    /**
     * Estimates the size of the rectangle the [corners] (TL, TR, BR, BL pixel points) should warp
     * onto. Width is the longer of the two horizontal edges, height the longer of the two vertical
     * edges — this preserves as much detail as possible without stretching.
     */
    fun outputSize(corners: List<Point2>): OutputSize {
        require(corners.size == 4) { "outputSize needs exactly 4 corners" }
        val (tl, tr, br, bl) = corners
        val widthTop = distance(tl, tr)
        val widthBottom = distance(bl, br)
        val heightLeft = distance(tl, bl)
        val heightRight = distance(tr, br)
        return OutputSize(
            width = max(widthTop, widthBottom).roundToInt().coerceAtLeast(1),
            height = max(heightLeft, heightRight).roundToInt().coerceAtLeast(1),
        )
    }

    /** The destination rectangle corners for an [OutputSize], ordered TL, TR, BR, BL. */
    fun destinationCorners(size: OutputSize): List<Point2> {
        val w = (size.width - 1).toDouble()
        val h = (size.height - 1).toDouble()
        return listOf(
            Point2(0.0, 0.0),
            Point2(w, 0.0),
            Point2(w, h),
            Point2(0.0, h),
        )
    }

    private fun distance(a: Point2, b: Point2): Double = hypot(a.x - b.x, a.y - b.y)
}
