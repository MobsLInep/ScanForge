package com.scanforge.core.domain.ocr

import kotlinx.serialization.Serializable

/**
 * A text script ScanForge can recognise. Each value maps to one ML Kit Text Recognition v2 script
 * model. A single recognizer also handles Latin alongside its own script (e.g. the Devanagari model
 * reads both Devanagari and Latin), which is what makes mixed Hindi/English pages work.
 *
 * Kept in the domain (zero Android) and `@Serializable` so a page's chosen script survives in the
 * stored OCR result and can drive a re-run.
 */
@Serializable
enum class OcrLanguage(val tag: String) {
    Latin("Latin"),
    Devanagari("Devanagari"),
    Chinese("Chinese"),
    Japanese("Japanese"),
    Korean("Korean"),
    ;

    companion object {
        fun fromTag(tag: String?): OcrLanguage? =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
    }
}

/**
 * How OCR should choose a script for a page: [Auto] lets the engine try the likely scripts and keep
 * the strongest result; [Manual] pins one script the user picked. Persisted per page (as a tag) so
 * "Re-run OCR" after changing the language reuses the selection.
 */
sealed interface OcrLanguageMode {
    data object Auto : OcrLanguageMode
    data class Manual(val language: OcrLanguage) : OcrLanguageMode

    /** Stable string stored in `pages.language`, decoded back via [fromTag]. */
    fun storageTag(): String = when (this) {
        Auto -> AUTO_TAG
        is Manual -> language.tag
    }

    companion object {
        const val AUTO_TAG = "Auto"
        val DEFAULT: OcrLanguageMode = Auto

        fun fromTag(tag: String?): OcrLanguageMode = when {
            tag == null || tag.equals(AUTO_TAG, ignoreCase = true) -> Auto
            else -> OcrLanguage.fromTag(tag)?.let(::Manual) ?: Auto
        }
    }
}
