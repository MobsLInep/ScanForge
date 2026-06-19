package com.scanforge.core.data.scanning

import android.util.Log
import com.scanforge.core.common.dispatchers.Dispatcher
import com.scanforge.core.common.dispatchers.ScanForgeDispatcher
import com.scanforge.core.domain.scanning.DetectedQuad
import com.scanforge.core.domain.scanning.EdgeDetector
import com.scanforge.core.domain.scanning.NormalizedPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Primary [EdgeDetector]: OpenCV contour detection over the captured frame (capture-time, not
 * per-frame). The native library is initialized lazily and once via [OpenCVLoader.initLocal];
 * if that fails (or no confident quad is found) detection returns `null` and the UI falls back to
 * a full-frame manual crop.
 */
@Singleton
class OpenCvEdgeDetector @Inject constructor(
    @Dispatcher(ScanForgeDispatcher.Default) private val defaultDispatcher: CoroutineDispatcher,
) : EdgeDetector {

    @Volatile private var initialized = false

    private fun ensureInitialized(): Boolean {
        if (initialized) return true
        synchronized(this) {
            if (!initialized) {
                initialized = OpenCVLoader.initLocal()
                if (!initialized) Log.w(TAG, "OpenCVLoader.initLocal() failed; edge detection disabled")
            }
        }
        return initialized
    }

    override suspend fun detectQuad(imagePath: String): DetectedQuad? = withContext(defaultDispatcher) {
        if (!ensureInitialized()) return@withContext null

        val source = Imgcodecs.imread(imagePath)
        if (source.empty()) {
            Log.w(TAG, "Could not read image for edge detection: $imagePath")
            return@withContext null
        }

        try {
            detectInternal(source)
        } catch (t: Throwable) {
            Log.w(TAG, "Edge detection failed", t)
            null
        } finally {
            source.release()
        }
    }

    private fun detectInternal(source: Mat): DetectedQuad? {
        // Downscale for speed; normalized output makes the scale factor irrelevant.
        val longest = maxOf(source.width(), source.height()).toDouble()
        val scale = if (longest > ANALYSIS_LONG_EDGE) ANALYSIS_LONG_EDGE / longest else 1.0
        val work = Mat()
        Imgproc.resize(source, work, Size(source.width() * scale, source.height() * scale))
        val w = work.width().toFloat()
        val h = work.height().toFloat()
        val frameArea = (w * h).toDouble()

        val gray = Mat()
        Imgproc.cvtColor(work, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        // Close small gaps so page borders form a single contour.
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.dilate(edges, edges, kernel)

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        var best: List<Point>? = null
        var bestArea = frameArea * MIN_AREA_RATIO // reject quads smaller than this
        for (contour in contours) {
            val c2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
            val pts = approx.toArray()
            if (pts.size == 4 && Imgproc.isContourConvex(MatOfPoint(*pts))) {
                val area = Imgproc.contourArea(approx)
                if (area > bestArea) {
                    bestArea = area
                    best = pts.toList()
                }
            }
            c2f.release(); approx.release()
        }

        work.release(); gray.release(); edges.release(); kernel.release()
        val ordered = best?.let(::orderCorners) ?: return null
        return DetectedQuad(
            topLeft = ordered[0].normalize(w, h),
            topRight = ordered[1].normalize(w, h),
            bottomRight = ordered[2].normalize(w, h),
            bottomLeft = ordered[3].normalize(w, h),
        )
    }

    /** Orders 4 points to [TL, TR, BR, BL] using the sum/difference heuristic. */
    private fun orderCorners(pts: List<Point>): List<Point> {
        val tl = pts.minBy { it.x + it.y }
        val br = pts.maxBy { it.x + it.y }
        val tr = pts.minBy { it.y - it.x }
        val bl = pts.maxBy { it.y - it.x }
        return listOf(tl, tr, br, bl)
    }

    private fun Point.normalize(w: Float, h: Float) =
        NormalizedPoint((x / w).toFloat().coerceIn(0f, 1f), (y / h).toFloat().coerceIn(0f, 1f))

    private companion object {
        const val TAG = "OpenCvEdgeDetector"
        const val ANALYSIS_LONG_EDGE = 600.0
        const val MIN_AREA_RATIO = 0.18
    }
}
