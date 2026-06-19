package com.scanforge.core.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scanforge.core.domain.ocr.OcrLanguage
import com.scanforge.core.domain.ocr.OcrLanguageMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real ML Kit Text Recognition v2 on a rendered sample image. Verifies the bundled Devanagari model
 * reads a mixed Hindi + English page (the headline multi-script requirement) fully on-device.
 */
@RunWith(AndroidJUnit4::class)
class MlKitOcrEngineTest {

    private val engine = MlKitOcrEngine(Dispatchers.Default)

    @Test
    fun recognizesHindiAndEnglishOnTheSamePage() = runBlocking {
        val path = renderSample("Hello World", "नमस्ते दुनिया")

        val auto = engine.recognize(path, OcrLanguageMode.Auto)
        val deva = engine.recognize(path, OcrLanguageMode.Manual(OcrLanguage.Devanagari))

        // English survives in both passes.
        assertTrue("Expected English in: ${auto.fullText}", auto.fullText.contains("Hello", ignoreCase = true))
        // Devanagari is recovered by the script model (and chosen by Auto since it wins on text length).
        assertTrue("Expected Devanagari in: ${deva.fullText}", deva.fullText.any { it in 'ऀ'..'ॿ' })
        assertTrue("Auto should keep the Devanagari pass", auto.fullText.any { it in 'ऀ'..'ॿ' })
        assertTrue("Boxes should be present", deva.words.isNotEmpty())
    }

    /** Draws two lines of text on a white bitmap and writes it to a cache JPEG, returning its path. */
    private fun renderSample(english: String, hindi: String): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(1000, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 80f
                isAntiAlias = true
            }
            drawText(english, 60f, 150f, paint)
            drawText(hindi, 60f, 290f, paint)
        }
        val file = File(context.cacheDir, "ocr_sample.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        return file.absolutePath
    }
}
