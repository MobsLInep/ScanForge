package com.scanforge.core.imaging.geometry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

private const val EPS = 1e-6

class PerspectiveTransformTest {

    private fun assertMaps(t: PerspectiveTransform, from: Point2, toX: Double, toY: Double) {
        val mapped = t.map(from)
        assertEquals(toX, mapped.x, 1e-4, "x of $from")
        assertEquals(toY, mapped.y, 1e-4, "y of $from")
    }

    @Test
    fun `identity correspondences yield identity mapping`() {
        val square = listOf(Point2(0.0, 0.0), Point2(1.0, 0.0), Point2(1.0, 1.0), Point2(0.0, 1.0))
        val t = PerspectiveTransform.from(square, square)
        assertMaps(t, Point2(0.5, 0.5), 0.5, 0.5)
        assertMaps(t, Point2(0.25, 0.75), 0.25, 0.75)
    }

    @Test
    fun `affine scale by two maps interior point and corners`() {
        val src = listOf(Point2(0.0, 0.0), Point2(1.0, 0.0), Point2(1.0, 1.0), Point2(0.0, 1.0))
        val dst = listOf(Point2(0.0, 0.0), Point2(2.0, 0.0), Point2(2.0, 2.0), Point2(0.0, 2.0))
        val t = PerspectiveTransform.from(src, dst)
        assertMaps(t, Point2(0.5, 0.5), 1.0, 1.0)
        // Corners land exactly on their destinations.
        src.forEachIndexed { i, p ->
            val m = t.map(p)
            assertEquals(dst[i].x, m.x, EPS)
            assertEquals(dst[i].y, m.y, EPS)
        }
    }

    @Test
    fun `true perspective warp maps all four corners to destination`() {
        // A trapezoid (angled page) flattened onto a 100x200 rectangle.
        val src = listOf(
            Point2(20.0, 30.0),   // TL
            Point2(180.0, 50.0),  // TR
            Point2(160.0, 250.0), // BR
            Point2(40.0, 240.0),  // BL
        )
        val dst = listOf(
            Point2(0.0, 0.0),
            Point2(100.0, 0.0),
            Point2(100.0, 200.0),
            Point2(0.0, 200.0),
        )
        val t = PerspectiveTransform.from(src, dst)
        src.forEachIndexed { i, p ->
            val m = t.map(p)
            assertEquals(dst[i].x, m.x, 1e-4, "corner $i x")
            assertEquals(dst[i].y, m.y, 1e-4, "corner $i y")
        }
        // Interior point stays strictly inside the destination rectangle.
        val center = t.map(Point2(100.0, 140.0))
        assert(center.x in 0.0..100.0 && center.y in 0.0..200.0) { "center mapped outside: $center" }
    }

    @Test
    fun `wrong number of points is rejected`() {
        val three = listOf(Point2(0.0, 0.0), Point2(1.0, 0.0), Point2(1.0, 1.0))
        val four = listOf(Point2(0.0, 0.0), Point2(1.0, 0.0), Point2(1.0, 1.0), Point2(0.0, 1.0))
        assertThrows(IllegalArgumentException::class.java) { PerspectiveTransform.from(three, four) }
    }

    @Test
    fun `collinear source corners are rejected as degenerate`() {
        val collinear = listOf(Point2(0.0, 0.0), Point2(1.0, 1.0), Point2(2.0, 2.0), Point2(3.0, 3.0))
        val dst = listOf(Point2(0.0, 0.0), Point2(1.0, 0.0), Point2(1.0, 1.0), Point2(0.0, 1.0))
        assertThrows(IllegalArgumentException::class.java) { PerspectiveTransform.from(collinear, dst) }
    }
}
