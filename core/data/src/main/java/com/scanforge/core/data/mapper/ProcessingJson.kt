package com.scanforge.core.data.mapper

import com.scanforge.core.domain.imaging.PageProcessing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Converts a [PageProcessing] edit recipe to/from the JSON stored in `pages.processing_params`.
 * Lenient + defaults-tolerant so older rows (or recipes saved before a field existed) still decode.
 */
internal object ProcessingJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(processing: PageProcessing): String = json.encodeToString(processing)

    fun decode(raw: String?): PageProcessing? =
        raw?.let { runCatching { json.decodeFromString<PageProcessing>(it) }.getOrNull() }
}
