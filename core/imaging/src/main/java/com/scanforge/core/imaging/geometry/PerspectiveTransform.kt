package com.scanforge.core.imaging.geometry

/** A 2D point in pixel space. Doubles keep the homography solve numerically stable. */
data class Point2(val x: Double, val y: Double)

/**
 * A 3x3 perspective (homography) transform, stored row-major in [coefficients] with `h22` fixed to
 * `1`. This is the pure-math heart of the document warp: given four source corners and four
 * destination corners it solves the mapping that flattens an angled page into a rectangle.
 *
 * Deliberately free of OpenCV and Android so it can be unit-tested on the JVM. The OpenCV pipeline
 * uses this same math for output-size estimation and as a verifiable reference for its native warp.
 */
class PerspectiveTransform private constructor(private val h: DoubleArray) {

    /** The 9 row-major homography coefficients (`h22 == 1`). */
    val coefficients: DoubleArray get() = h.copyOf()

    /** Maps [p] through the homography (perspective divide included). */
    fun map(p: Point2): Point2 {
        val w = h[6] * p.x + h[7] * p.y + h[8]
        return Point2(
            (h[0] * p.x + h[1] * p.y + h[2]) / w,
            (h[3] * p.x + h[4] * p.y + h[5]) / w,
        )
    }

    companion object {
        /**
         * Solves the homography mapping the four [src] corners onto the four [dst] corners, in the
         * same order. Sets up the standard 8x8 linear system (8 unknowns, `h22 = 1`) and solves it
         * with Gauss–Jordan elimination + partial pivoting.
         *
         * @throws IllegalArgumentException if either list isn't exactly four points, or the corners
         *   are degenerate (collinear) so no unique transform exists.
         */
        fun from(src: List<Point2>, dst: List<Point2>): PerspectiveTransform {
            require(src.size == 4 && dst.size == 4) {
                "Perspective transform needs exactly 4 source and 4 destination points"
            }

            // For each (x,y)->(u,v): two rows enforcing the projective mapping with h22 = 1.
            val a = Array(8) { DoubleArray(9) } // augmented 8x9 (8 unknowns + rhs)
            for (i in 0 until 4) {
                val (x, y) = src[i]
                val (u, v) = dst[i]
                val r0 = i * 2
                val r1 = r0 + 1
                a[r0] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -x * u, -y * u, u)
                a[r1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -x * v, -y * v, v)
            }

            val solution = solve(a)
            return PerspectiveTransform(
                doubleArrayOf(
                    solution[0], solution[1], solution[2],
                    solution[3], solution[4], solution[5],
                    solution[6], solution[7], 1.0,
                ),
            )
        }

        /** Gauss–Jordan elimination with partial pivoting on an 8x9 augmented matrix. */
        private fun solve(a: Array<DoubleArray>): DoubleArray {
            val n = 8
            for (col in 0 until n) {
                // Partial pivot: largest magnitude in this column at or below the diagonal.
                var pivot = col
                for (row in col + 1 until n) {
                    if (kotlin.math.abs(a[row][col]) > kotlin.math.abs(a[pivot][col])) pivot = row
                }
                require(kotlin.math.abs(a[pivot][col]) > 1e-9) {
                    "Degenerate (collinear) corners — no unique perspective transform"
                }
                val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp

                // Normalize pivot row, then eliminate the column from every other row.
                val pv = a[col][col]
                for (k in col..n) a[col][k] /= pv
                for (row in 0 until n) {
                    if (row == col) continue
                    val factor = a[row][col]
                    if (factor == 0.0) continue
                    for (k in col..n) a[row][k] -= factor * a[col][k]
                }
            }
            return DoubleArray(n) { a[it][n] }
        }
    }
}
