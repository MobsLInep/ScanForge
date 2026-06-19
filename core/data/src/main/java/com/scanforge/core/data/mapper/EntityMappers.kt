package com.scanforge.core.data.mapper

import com.scanforge.core.database.entity.FolderWithCount
import com.scanforge.core.database.entity.PageEntity
import com.scanforge.core.database.entity.PopulatedDocument
import com.scanforge.core.database.entity.TagEntity
import com.scanforge.core.domain.model.Document
import com.scanforge.core.domain.model.Folder
import com.scanforge.core.domain.model.OcrStatus
import com.scanforge.core.domain.model.Page
import com.scanforge.core.domain.model.Tag

/** Maps a stored OCR-status name to the domain enum, defaulting to [OcrStatus.NotStarted]. */
fun String.toOcrStatus(): OcrStatus =
    runCatching { OcrStatus.valueOf(this) }.getOrDefault(OcrStatus.NotStarted)

fun TagEntity.toDomain(): Tag = Tag(id = id, name = name, colorHex = colorHex)

fun PageEntity.toDomain(): Page = Page(
    id = id,
    documentId = documentId,
    pageOrder = pageOrder,
    originalImagePath = originalImagePath,
    processedImagePath = processedImagePath,
    thumbnailPath = thumbnailPath,
    extractedText = extractedText,
    language = language,
    ocrStatus = ocrStatus.toOcrStatus(),
    processing = ProcessingJson.decode(processingParams),
    ocrData = OcrJson.decode(ocrBlocks),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PopulatedDocument.toDomain(): Document = Document(
    id = document.id,
    title = document.title,
    createdAt = document.createdAt,
    updatedAt = document.updatedAt,
    ocrStatus = document.ocrStatus.toOcrStatus(),
    pageCount = pages.size,
    pages = pages.sortedBy { it.pageOrder }.map(PageEntity::toDomain),
    tags = tags.sortedBy { it.name }.map(TagEntity::toDomain),
    isFavorite = document.isFavorite,
    folderId = document.folderId,
    sizeBytes = document.sizeBytes,
    deletedAt = document.deletedAt,
    thumbnailPath = document.thumbnailPath,
)

fun FolderWithCount.toDomain(): Folder = Folder(
    id = folder.id,
    name = folder.name,
    parentId = folder.parentId,
    colorHex = folder.colorHex,
    documentCount = docCount,
    createdAt = folder.createdAt,
)
