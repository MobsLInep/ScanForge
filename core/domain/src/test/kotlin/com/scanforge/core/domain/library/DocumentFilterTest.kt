package com.scanforge.core.domain.library

import com.scanforge.core.domain.model.Document
import com.scanforge.core.domain.model.OcrStatus
import com.scanforge.core.domain.model.OcrResult
import com.scanforge.core.domain.model.Page
import com.scanforge.core.domain.model.Tag
import com.scanforge.core.domain.ocr.OcrDocument
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocumentFilterTest {

    private fun doc(
        id: Long,
        title: String = "Doc",
        tags: List<Tag> = emptyList(),
        folderId: Long? = null,
        updatedAt: Instant = T(100),
        ocrStatus: OcrStatus = OcrStatus.Completed,
        sizeBytes: Long = 0,
        languages: List<String> = emptyList(),
    ): Document {
        val pages = if (languages.isEmpty()) {
            emptyList()
        } else {
            listOf(
                Page(
                    id = id * 1000,
                    documentId = id,
                    pageOrder = 0,
                    originalImagePath = "x.jpg",
                    ocrStatus = OcrStatus.Completed,
                    ocrData = OcrDocument(
                        fullText = "text",
                        recognizedLanguages = languages,
                    ),
                    createdAt = T(0),
                    updatedAt = T(0),
                ),
            )
        }
        return Document(
            id = id,
            title = title,
            createdAt = T(0),
            updatedAt = updatedAt,
            ocrStatus = ocrStatus,
            pages = pages,
            pageCount = pages.size,
            tags = tags,
            folderId = folderId,
            sizeBytes = sizeBytes,
        )
    }

    @Test
    fun `none matches everything`() {
        assertTrue(DocumentFilter.NONE.matches(doc(1)))
        assertFalse(DocumentFilter.NONE.isActive)
    }

    @Test
    fun `tag filter requires all tags present`() {
        val filter = DocumentFilter(tagIds = setOf(1, 2))
        assertTrue(filter.matches(doc(1, tags = listOf(Tag(1, "a"), Tag(2, "b"), Tag(3, "c")))))
        assertFalse(filter.matches(doc(2, tags = listOf(Tag(1, "a")))))
    }

    @Test
    fun `root folder scope keeps only unfiled documents`() {
        val filter = DocumentFilter(folder = FolderScope.Root)
        assertTrue(filter.matches(doc(1, folderId = null)))
        assertFalse(filter.matches(doc(2, folderId = 5)))
    }

    @Test
    fun `folder id scope keeps only that folder`() {
        val filter = DocumentFilter(folder = FolderScope.Id(5))
        assertTrue(filter.matches(doc(1, folderId = 5)))
        assertFalse(filter.matches(doc(2, folderId = 6)))
    }

    @Test
    fun `date range is inclusive`() {
        val filter = DocumentFilter(dateFrom = T(100), dateTo = T(200))
        assertTrue(filter.matches(doc(1, updatedAt = T(100))))
        assertTrue(filter.matches(doc(2, updatedAt = T(200))))
        assertFalse(filter.matches(doc(3, updatedAt = T(99))))
        assertFalse(filter.matches(doc(4, updatedAt = T(201))))
    }

    @Test
    fun `language and ocr status filters apply`() {
        assertTrue(DocumentFilter(language = "Latin").matches(doc(1, languages = listOf("Latin"))))
        assertFalse(DocumentFilter(language = "Devanagari").matches(doc(2, languages = listOf("Latin"))))
        assertTrue(DocumentFilter(ocrStatus = OcrStatus.Completed).matches(doc(3, ocrStatus = OcrStatus.Completed)))
        assertFalse(DocumentFilter(ocrStatus = OcrStatus.Failed).matches(doc(4, ocrStatus = OcrStatus.Completed)))
    }

    @Test
    fun `active count reflects distinct constraints`() {
        val filter = DocumentFilter(
            tagIds = setOf(1),
            folder = FolderScope.Root,
            dateFrom = T(1),
            ocrStatus = OcrStatus.Completed,
        )
        assertEquals(4, filter.activeCount)
        assertTrue(filter.isActive)
    }

    @Test
    fun `sort orders by name and size`() {
        val a = doc(1, title = "Beta", sizeBytes = 10)
        val b = doc(2, title = "alpha", sizeBytes = 30)
        val c = doc(3, title = "Gamma", sizeBytes = 20)
        val list = listOf(a, b, c)

        assertEquals(listOf(2L, 1L, 3L), list.sortedBy(DocumentSort.NameAZ).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), list.sortedBy(DocumentSort.SizeLargest).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), list.sortedBy(DocumentSort.SizeSmallest).map { it.id })
    }

    private companion object {
        fun T(millis: Long): Instant = Instant.ofEpochMilli(millis)
    }
}
