package com.scanforge.core.data.mapper

import com.scanforge.core.database.entity.DocumentEntity
import com.scanforge.core.database.entity.PageEntity
import com.scanforge.core.database.entity.PopulatedDocument
import com.scanforge.core.database.entity.TagEntity
import com.scanforge.core.domain.model.OcrStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class EntityMappersTest {

    private val now: Instant = Instant.ofEpochMilli(1_700_000_000_000)

    @Test
    fun `unknown ocr status name falls back to NotStarted`() {
        assertEquals(OcrStatus.NotStarted, "garbage".toOcrStatus())
        assertEquals(OcrStatus.Completed, "Completed".toOcrStatus())
    }

    @Test
    fun `populated document maps pages sorted by order and counts them`() {
        val populated = PopulatedDocument(
            document = DocumentEntity(
                id = 5,
                title = "Notes",
                ocrStatus = OcrStatus.Completed.name,
                createdAt = now,
                updatedAt = now,
            ),
            pages = listOf(
                PageEntity(id = 2, documentId = 5, pageOrder = 1, originalImagePath = "b.jpg", ocrStatus = "NotStarted", createdAt = now, updatedAt = now),
                PageEntity(id = 1, documentId = 5, pageOrder = 0, originalImagePath = "a.jpg", ocrStatus = "NotStarted", createdAt = now, updatedAt = now),
            ),
            tags = listOf(
                TagEntity(id = 9, name = "Work"),
                TagEntity(id = 8, name = "Archive"),
            ),
        )

        val document = populated.toDomain()

        assertEquals(5, document.id)
        assertEquals(OcrStatus.Completed, document.ocrStatus)
        assertEquals(2, document.pageCount)
        assertEquals(listOf(0, 1), document.pages.map { it.pageOrder })
        assertEquals(listOf("Archive", "Work"), document.tags.map { it.name })
    }
}
