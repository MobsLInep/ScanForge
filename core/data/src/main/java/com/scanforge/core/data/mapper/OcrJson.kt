package com.scanforge.core.data.mapper

import com.scanforge.core.domain.ocr.OcrDocument
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Converts an [OcrDocument] (block/line/word boxes + confidence) to/from the JSON stored in
 * `pages.ocr_blocks`. Lenient + defaults-tolerant so results written before a field existed still
 * decode, and a malformed row degrades to `null` rather than crashing reads.
 */
internal object OcrJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(document: OcrDocument): String = json.encodeToString(document)

    fun decode(raw: String?): OcrDocument? =
        raw?.let { runCatching { json.decodeFromString<OcrDocument>(it) }.getOrNull() }
}
