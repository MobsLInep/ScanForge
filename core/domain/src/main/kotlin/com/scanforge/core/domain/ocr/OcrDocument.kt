package com.scanforge.core.domain.ocr

import kotlinx.serialization.Serializable

/**
 * An axis-aligned bounding box in *normalized* image coordinates (`0f..1f` of the source image's
 * width/height). Normalizing makes boxes resolution-independent so the OCR results screen can map
 * them onto any displayed image size, and so a searchable-PDF layer can re-scale them to PDF points.
 */
@Serializable
data class TextBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    companion object {
        val ZERO = TextBox(0f, 0f, 0f, 0f)
    }
}

/** A single recognised word (ML Kit "element"), the unit used for tap-to-highlight and heatmaps. */
@Serializable
data class OcrWord(
    val text: String,
    val box: TextBox,
    /** `0f..1f` confidence, or `null` when the engine does not report one for this word. */
    val confidence: Float? = null,
)

/** A line of text — an ordered run of [words] sharing a baseline. */
@Serializable
data class OcrLine(
    val text: String,
    val box: TextBox,
    val confidence: Float? = null,
    /** BCP-47-ish tag the engine attributed to this line, e.g. `und-Latn`/`und-Deva`. */
    val language: String? = null,
    val words: List<OcrWord> = emptyList(),
)

/** A block — a paragraph-like cluster of [lines]. */
@Serializable
data class OcrBlock(
    val box: TextBox,
    val lines: List<OcrLine> = emptyList(),
)

/**
 * The full structured OCR output for one page: the concatenated [fullText] plus the block/line/word
 * tree with bounding boxes and per-word confidence. This is the unit persisted (as JSON) and is what
 * powers the text view, the confidence heatmap, tap-to-copy, and — in a later phase — the invisible
 * searchable-PDF text layer.
 */
@Serializable
data class OcrDocument(
    val fullText: String,
    val blocks: List<OcrBlock> = emptyList(),
    /** Distinct languages the engine attributed across lines, most-frequent first. */
    val recognizedLanguages: List<String> = emptyList(),
    /** Mean word confidence in `0f..1f`, or `null` when no word reported a confidence. */
    val confidence: Float? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    /** Which script model produced this result (the chosen one when running in Auto mode). */
    val script: OcrLanguage = OcrLanguage.Latin,
) {
    val lines: List<OcrLine> get() = blocks.flatMap { it.lines }
    val words: List<OcrWord> get() = lines.flatMap { it.words }
    val isEmpty: Boolean get() = fullText.isBlank()

    companion object {
        val EMPTY = OcrDocument(fullText = "")

        /** Builds an [OcrDocument] from blocks, deriving [fullText], [confidence] and languages. */
        fun from(
            blocks: List<OcrBlock>,
            imageWidth: Int,
            imageHeight: Int,
            script: OcrLanguage,
        ): OcrDocument {
            val lines = blocks.flatMap { it.lines }
            val fullText = lines.joinToString("\n") { it.text }
            val confidences = lines.flatMap { it.words }.mapNotNull { it.confidence }
            val languages = lines.mapNotNull { it.language?.takeIf(String::isNotBlank) }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
            return OcrDocument(
                fullText = fullText,
                blocks = blocks,
                recognizedLanguages = languages,
                confidence = if (confidences.isEmpty()) null else confidences.average().toFloat(),
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                script = script,
            )
        }
    }
}
