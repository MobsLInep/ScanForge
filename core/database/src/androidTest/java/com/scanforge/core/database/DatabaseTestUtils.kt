package com.scanforge.core.database

import com.scanforge.core.database.entity.DocumentEntity
import com.scanforge.core.database.entity.PageEntity
import com.scanforge.core.database.entity.TagEntity
import java.time.Instant

internal val FIXED_TIME: Instant = Instant.ofEpochMilli(1_700_000_000_000)

internal fun documentEntity(
    title: String,
    ocrStatus: String = "NotStarted",
    createdAt: Instant = FIXED_TIME,
    updatedAt: Instant = FIXED_TIME,
) = DocumentEntity(
    title = title,
    ocrStatus = ocrStatus,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun pageEntity(
    documentId: Long,
    pageOrder: Int,
    originalImagePath: String = "page_$pageOrder.jpg",
    extractedText: String? = null,
) = PageEntity(
    documentId = documentId,
    pageOrder = pageOrder,
    originalImagePath = originalImagePath,
    extractedText = extractedText,
    ocrStatus = "NotStarted",
    createdAt = FIXED_TIME,
    updatedAt = FIXED_TIME,
)

internal fun tagEntity(name: String, colorHex: String? = null) =
    TagEntity(name = name, colorHex = colorHex)
