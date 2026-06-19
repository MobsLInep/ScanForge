package com.scanforge.core.ocr

import android.graphics.BitmapFactory
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.scanforge.core.common.dispatchers.Dispatcher
import com.scanforge.core.common.dispatchers.ScanForgeDispatcher
import com.scanforge.core.domain.ocr.OcrBlock
import com.scanforge.core.domain.ocr.OcrDocument
import com.scanforge.core.domain.ocr.OcrEngine
import com.scanforge.core.domain.ocr.OcrLanguage
import com.scanforge.core.domain.ocr.OcrLanguageMode
import com.scanforge.core.domain.ocr.OcrLine
import com.scanforge.core.domain.ocr.OcrWord
import com.scanforge.core.domain.ocr.TextBox
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit Text Recognition v2 implementation of [OcrEngine]. All models are bundled on-device, so
 * recognition runs fully offline — nothing leaves the phone.
 *
 * One recognizer maps to one script, and each script's recognizer also reads Latin, which is what
 * lets a single Devanagari pass capture a mixed Hindi/English page. In [OcrLanguageMode.Auto] the
 * engine runs the likely scripts (Latin + Devanagari) and keeps whichever recovered more text — so
 * pure-English stays on Latin while anything with Devanagari wins with the script model.
 */
@Singleton
class MlKitOcrEngine @Inject constructor(
    @Dispatcher(ScanForgeDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : OcrEngine {

    // Recognizers are heavyweight; create each lazily and reuse for the engine's lifetime.
    private val recognizers = ConcurrentHashMap<OcrLanguage, TextRecognizer>()

    override suspend fun recognize(
        imagePath: String,
        mode: OcrLanguageMode,
    ): OcrDocument = withContext(ioDispatcher) {
        val bitmap = decodeCapped(imagePath) ?: return@withContext OcrDocument.EMPTY
        val width = bitmap.width
        val height = bitmap.height
        try {
            val candidates = when (mode) {
                is OcrLanguageMode.Manual -> listOf(mode.language)
                OcrLanguageMode.Auto -> AUTO_CANDIDATES
            }
            val image = InputImage.fromBitmap(bitmap, 0)
            // Run each candidate script; keep the result that recovered the most text.
            candidates
                .map { script -> script to process(script, image, width, height) }
                .maxByOrNull { (_, doc) -> doc.fullText.count { !it.isWhitespace() } }
                ?.second
                ?: OcrDocument.EMPTY
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun process(
        script: OcrLanguage,
        image: InputImage,
        width: Int,
        height: Int,
    ): OcrDocument {
        val text = recognizer(script).await(image)
        return OcrDocument.from(
            blocks = text.textBlocks.map { it.toDomain(width, height) },
            imageWidth = width,
            imageHeight = height,
            script = script,
        )
    }

    private fun recognizer(script: OcrLanguage): TextRecognizer = recognizers.getOrPut(script) {
        TextRecognition.getClient(
            when (script) {
                OcrLanguage.Latin -> TextRecognizerOptions.DEFAULT_OPTIONS
                OcrLanguage.Devanagari -> DevanagariTextRecognizerOptions.Builder().build()
                OcrLanguage.Chinese -> ChineseTextRecognizerOptions.Builder().build()
                OcrLanguage.Japanese -> JapaneseTextRecognizerOptions.Builder().build()
                OcrLanguage.Korean -> KoreanTextRecognizerOptions.Builder().build()
            },
        )
    }

    /** Awaits an ML Kit [com.google.android.gms.tasks.Task] without pulling in extra coroutine libs. */
    private suspend fun TextRecognizer.await(image: InputImage): Text =
        suspendCancellableCoroutine { cont ->
            process(image)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

    /** Decodes the image, downsampled so the longest edge is ≤ [MAX_EDGE] to bound memory. */
    private fun decodeCapped(path: String): android.graphics.Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun Text.TextBlock.toDomain(w: Int, h: Int) = OcrBlock(
        box = boundingBox.normalized(w, h),
        lines = lines.map { line ->
            OcrLine(
                text = line.text,
                box = line.boundingBox.normalized(w, h),
                confidence = line.confidence.sanitizeConfidence(),
                language = line.recognizedLanguage?.takeIf { it.isNotBlank() && it != "und" },
                words = line.elements.map { el ->
                    OcrWord(
                        text = el.text,
                        box = el.boundingBox.normalized(w, h),
                        confidence = el.confidence.sanitizeConfidence(),
                    )
                },
            )
        },
    )

    private fun Rect?.normalized(w: Int, h: Int): TextBox {
        if (this == null || w <= 0 || h <= 0) return TextBox.ZERO
        return TextBox(
            left = (left.toFloat() / w).coerceIn(0f, 1f),
            top = (top.toFloat() / h).coerceIn(0f, 1f),
            right = (right.toFloat() / w).coerceIn(0f, 1f),
            bottom = (bottom.toFloat() / h).coerceIn(0f, 1f),
        )
    }

    /** ML Kit confidence can be unset/NaN for some scripts; surface only a valid `0..1` value. */
    private fun Float?.sanitizeConfidence(): Float? =
        this?.takeIf { !it.isNaN() && it in 0f..1f }

    private companion object {
        const val MAX_EDGE = 2600
        val AUTO_CANDIDATES = listOf(OcrLanguage.Latin, OcrLanguage.Devanagari)
    }
}
