package com.scanforge.core.imaging

import android.graphics.BitmapFactory
import android.util.Log
import com.scanforge.core.common.dispatchers.Dispatcher
import com.scanforge.core.common.dispatchers.ScanForgeDispatcher
import com.scanforge.core.domain.imaging.EnhancementFilter
import com.scanforge.core.domain.imaging.EnhancementSettings
import com.scanforge.core.domain.imaging.ImagePipeline
import com.scanforge.core.domain.imaging.PageProcessing
import com.scanforge.core.domain.scanning.DetectedQuad
import com.scanforge.core.imaging.geometry.Point2
import com.scanforge.core.imaging.geometry.QuadGeometry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * OpenCV-backed [ImagePipeline]: perspective warp → rotation → de-skew → enhancement filter →
 * brightness/contrast → sharpen → denoise → JPEG. The native library is initialized lazily once via
 * [OpenCVLoader.initLocal]; everything runs on the Default dispatcher and the source file is never
 * mutated (non-destructive editing). Previews decode with an [android.graphics.BitmapFactory] sample
 * size so memory stays low; full-resolution renders decode at native size.
 */
@Singleton
class OpenCvImagePipeline @Inject constructor(
    @Dispatcher(ScanForgeDispatcher.Default) private val defaultDispatcher: CoroutineDispatcher,
) : ImagePipeline {

    @Volatile private var initialized = false

    private fun ensureInitialized(): Boolean {
        if (initialized) return true
        synchronized(this) {
            if (!initialized) {
                initialized = OpenCVLoader.initLocal()
                if (!initialized) Log.w(TAG, "OpenCVLoader.initLocal() failed; imaging disabled")
            }
        }
        return initialized
    }

    override suspend fun render(
        sourcePath: String,
        processing: PageProcessing,
        maxEdge: Int?,
    ): ByteArray? = withContext(defaultDispatcher) {
        if (!ensureInitialized()) return@withContext null
        val source = decodeDownscaled(sourcePath, maxEdge) ?: run {
            Log.w(TAG, "Could not decode image: $sourcePath")
            return@withContext null
        }
        try {
            val processed = process(source, processing)
            encodeJpeg(processed, quality = if (maxEdge == null) FULL_QUALITY else PREVIEW_QUALITY)
                .also { processed.release() }
        } catch (t: Throwable) {
            Log.w(TAG, "Image processing failed", t)
            null
        } finally {
            source.release()
        }
    }

    /** Runs the full enhancement chain on a BGR [source] Mat and returns a new result Mat. */
    private fun process(source: Mat, processing: PageProcessing): Mat {
        val settings = processing.enhancement

        // 1. Perspective warp to flatten the detected page.
        var working = warp(source, processing.cropQuad)

        // 2. Coarse 90° rotation requested by the user.
        working = rotate90(working, settings.rotationDegrees)

        // 3. Straighten small residual skew.
        if (settings.deskew) working = deskew(working)

        // 4. Tonal/colour filter (may also remove shadows), returns a new Mat.
        var result = applyFilter(working, settings)
        working.release()

        // 5. Manual brightness/contrast trim.
        if (settings.brightness != 0f || settings.contrast != 0f) {
            adjustBrightnessContrast(result, settings.brightness, settings.contrast)
        }

        // 6. Optional sharpening (unsharp mask).
        if (settings.sharpness > 0f) result = sharpen(result, settings.sharpness)

        // 7. Optional denoise.
        if (settings.denoise) result = denoise(result)

        return result
    }

    // ── Decode ───────────────────────────────────────────────────────────────────────────────────

    private fun decodeDownscaled(path: String, maxEdge: Int?): Mat? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = if (maxEdge == null) 1 else sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        bitmap.recycle()
        val bgr = Mat()
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
        rgba.release()
        return bgr
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

    // ── Geometry ───────────────────────────────────────────────────────────────────────────────--

    /** Warps [source] so [quad] becomes a flat rectangle. Returns [source] unchanged when null/full. */
    private fun warp(source: Mat, quad: DetectedQuad?): Mat {
        if (quad == null || quad == DetectedQuad.FULL_FRAME) return source.clone()

        val w = source.width()
        val h = source.height()
        val srcCorners = QuadGeometry.cornersInPixels(quad, w, h)
        val size = QuadGeometry.outputSize(srcCorners)
        val dstCorners = QuadGeometry.destinationCorners(size)

        val srcMat = MatOfPoint2f(*srcCorners.map { Point(it.x, it.y) }.toTypedArray())
        val dstMat = MatOfPoint2f(*dstCorners.map { Point(it.x, it.y) }.toTypedArray())
        val transform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        val out = Mat()
        Imgproc.warpPerspective(source, out, transform, Size(size.width.toDouble(), size.height.toDouble()))
        srcMat.release(); dstMat.release(); transform.release()
        return out
    }

    /** Coarse rotation by a multiple of 90°. Returns a new Mat and releases [src] when it rotates. */
    private fun rotate90(src: Mat, degrees: Int): Mat {
        val code = when (degrees.mod(360)) {
            90 -> Core.ROTATE_90_CLOCKWISE
            180 -> Core.ROTATE_180
            270 -> Core.ROTATE_90_COUNTERCLOCKWISE
            else -> return src
        }
        val out = Mat()
        Core.rotate(src, out, code)
        src.release()
        return out
    }

    /** Estimates and corrects small page skew (±[MAX_DESKEW]°). Returns a new Mat, releases [src]. */
    private fun deskew(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        Core.bitwise_not(gray, gray)
        Imgproc.threshold(gray, gray, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        val points = Mat()
        Core.findNonZero(gray, points) // CV_32SC2
        gray.release()
        if (points.empty()) {
            points.release()
            return src
        }
        // minAreaRect needs floating-point points; findNonZero yields integer points.
        val pointsF = Mat()
        points.convertTo(pointsF, CvType.CV_32FC2)
        points.release()
        val mp = MatOfPoint2f(pointsF)
        val rect = Imgproc.minAreaRect(mp)
        pointsF.release(); mp.release()

        var angle = rect.angle
        if (angle < -45) angle += 90.0
        if (abs(angle) < 0.3 || abs(angle) > MAX_DESKEW) return src // nothing meaningful to fix

        val center = Point(src.width() / 2.0, src.height() / 2.0)
        val m = Imgproc.getRotationMatrix2D(center, angle, 1.0)
        val out = Mat()
        Imgproc.warpAffine(
            src, out, m, src.size(),
            Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE, Scalar(255.0, 255.0, 255.0),
        )
        m.release(); src.release()
        return out
    }

    // ── Filters ────────────────────────────────────────────────────────────────────────────────--

    /** Applies the chosen [EnhancementFilter]; returns a NEW Mat (caller keeps owning [bgr]). */
    private fun applyFilter(bgr: Mat, settings: EnhancementSettings): Mat = when (settings.filter) {
        EnhancementFilter.Original -> bgr.clone()
        EnhancementFilter.MagicColor -> magicColor(bgr)
        EnhancementFilter.Grayscale -> grayDocument(bgr, settings.removeShadows, binarize = false)
        EnhancementFilter.Auto -> grayDocument(bgr, settings.removeShadows, binarize = false, clahe = true)
        EnhancementFilter.BlackAndWhite -> grayDocument(bgr, settings.removeShadows, binarize = true)
    }

    /**
     * Greyscale document treatment: optional shadow flattening, adaptive contrast (CLAHE), and
     * optional adaptive-threshold binarisation for the crisp B&W document look.
     */
    private fun grayDocument(
        bgr: Mat,
        removeShadows: Boolean,
        binarize: Boolean,
        clahe: Boolean = false,
    ): Mat {
        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)

        if (removeShadows) flattenShadows(gray)

        if (clahe) {
            val c = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            c.apply(gray, gray)
        }

        if (binarize) {
            Imgproc.adaptiveThreshold(
                gray, gray, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 25, 12.0,
            )
        } else {
            Core.normalize(gray, gray, 0.0, 255.0, Core.NORM_MINMAX)
        }
        return gray
    }

    /** In-place shadow removal: divide the channel by its smoothed background estimate. */
    private fun flattenShadows(gray: Mat) {
        val background = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(17.0, 17.0))
        Imgproc.morphologyEx(gray, background, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.medianBlur(background, background, 21)
        // normalized = gray / background * 255, clamped to 8-bit.
        Core.divide(gray, background, gray, 255.0)
        kernel.release(); background.release()
    }

    /** Vivid colour preset: boosted saturation + a gentle contrast lift. Returns a new BGR Mat. */
    private fun magicColor(bgr: Mat): Mat {
        val hsv = Mat()
        Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV)
        val channels = ArrayList<Mat>()
        Core.split(hsv, channels)
        channels[1].convertTo(channels[1], CvType.CV_8U, 1.35, 0.0) // saturation ×1.35
        Core.merge(channels, hsv)
        channels.forEach { it.release() }
        val out = Mat()
        Imgproc.cvtColor(hsv, out, Imgproc.COLOR_HSV2BGR)
        hsv.release()
        out.convertTo(out, -1, 1.12, 6.0) // slight contrast/brightness pop
        return out
    }

    // ── Adjustments ──────────────────────────────────────────────────────────────────────────────

    /** In-place linear adjustment: [brightness]/[contrast] in `-1f..1f` (0 = unchanged). */
    private fun adjustBrightnessContrast(mat: Mat, brightness: Float, contrast: Float) {
        val alpha = 1.0 + contrast.coerceIn(-1f, 1f) // contrast gain 0..2
        val beta = brightness.coerceIn(-1f, 1f) * 100.0 // brightness shift ±100
        mat.convertTo(mat, -1, alpha, beta)
    }

    /** Unsharp mask; [amount] in `0f..1f`. Returns a new Mat and releases [src]. */
    private fun sharpen(src: Mat, amount: Float): Mat {
        val blurred = Mat()
        Imgproc.GaussianBlur(src, blurred, Size(0.0, 0.0), 3.0)
        val out = Mat()
        val w = amount.coerceIn(0f, 1f) * 1.5
        Core.addWeighted(src, 1.0 + w, blurred, -w, 0.0, out)
        blurred.release(); src.release()
        return out
    }

    /** Edge-preserving denoise (channel-count aware). Returns a new Mat and releases [src]. */
    private fun denoise(src: Mat): Mat {
        val out = Mat()
        if (src.channels() == 1) {
            org.opencv.photo.Photo.fastNlMeansDenoising(src, out, 7f, 7, 21)
        } else {
            org.opencv.photo.Photo.fastNlMeansDenoisingColored(src, out, 7f, 7f, 7, 21)
        }
        src.release()
        return out
    }

    // ── Encode ───────────────────────────────────────────────────────────────────────────────────

    private fun encodeJpeg(mat: Mat, quality: Int): ByteArray {
        val buffer = MatOfByte()
        val params = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, quality)
        Imgcodecs.imencode(".jpg", mat, buffer, params)
        val bytes = buffer.toArray()
        buffer.release(); params.release()
        return bytes
    }

    private companion object {
        const val TAG = "OpenCvImagePipeline"
        const val PREVIEW_QUALITY = 85
        const val FULL_QUALITY = 92
        const val MAX_DESKEW = 15.0
    }
}
