package com.scanforge.core.imaging.geometry

import com.scanforge.core.domain.scanning.DetectedQuad
import com.scanforge.core.domain.scanning.NormalizedPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QuadGeometryTest {

    @Test
    fun `normalized corners project to pixel space in TL TR BR BL order`() {
        val quad = DetectedQuad(
            topLeft = NormalizedPoint(0.1f, 0.2f),
            topRight = NormalizedPoint(0.9f, 0.2f),
            bottomRight = NormalizedPoint(0.9f, 0.8f),
            bottomLeft = NormalizedPoint(0.1f, 0.8f),
        )
        val corners = QuadGeometry.cornersInPixels(quad, srcWidth = 1000, srcHeight = 500)
        // Normalized coords are Float, so compare in pixels with a sub-pixel tolerance.
        assertCorner(100.0, 100.0, corners[0])
        assertCorner(900.0, 100.0, corners[1])
        assertCorner(900.0, 400.0, corners[2])
        assertCorner(100.0, 400.0, corners[3])
    }

    @Test
    fun `output size of axis-aligned rectangle equals its dimensions`() {
        val corners = listOf(
            Point2(0.0, 0.0),
            Point2(300.0, 0.0),
            Point2(300.0, 200.0),
            Point2(0.0, 200.0),
        )
        val size = QuadGeometry.outputSize(corners)
        assertEquals(300, size.width)
        assertEquals(200, size.height)
    }

    @Test
    fun `output size takes the longer of opposing edges`() {
        // Bottom edge longer than top, right edge longer than left → those win.
        val corners = listOf(
            Point2(50.0, 0.0),    // TL
            Point2(250.0, 0.0),   // TR  (top width 200)
            Point2(300.0, 400.0), // BR
            Point2(0.0, 360.0),   // BL
        )
        val size = QuadGeometry.outputSize(corners)
        // width  = max(top 200, bottom √(300²+40²)=302.65) → 303
        // height = max(left √(50²+360²)=363.46, right √(50²+400²)=403.11) → 403
        assertEquals(303, size.width)
        assertEquals(403, size.height)
    }

    private fun assertCorner(expectedX: Double, expectedY: Double, actual: Point2) {
        assertEquals(expectedX, actual.x, 0.01)
        assertEquals(expectedY, actual.y, 0.01)
    }

    @Test
    fun `full frame quad maps to the whole image`() {
        val corners = QuadGeometry.cornersInPixels(DetectedQuad.FULL_FRAME, 800, 600)
        val size = QuadGeometry.outputSize(corners)
        assertEquals(800, size.width)
        assertEquals(600, size.height)
    }

    @Test
    fun `destination corners form the output rectangle`() {
        val dst = QuadGeometry.destinationCorners(QuadGeometry.OutputSize(100, 50))
        assertEquals(Point2(0.0, 0.0), dst[0])
        assertEquals(Point2(99.0, 0.0), dst[1])
        assertEquals(Point2(99.0, 49.0), dst[2])
        assertEquals(Point2(0.0, 49.0), dst[3])
    }
}
