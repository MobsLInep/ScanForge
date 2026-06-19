package com.scanforge.core.domain.library

import com.scanforge.core.domain.model.Document
import com.scanforge.core.domain.model.OcrStatus
import com.scanforge.core.domain.model.Page
import com.scanforge.core.domain.model.Tag
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchRankerTest {

    private var nextId = 1L

    private fun page(text: String, order: Int): Page {
        val id = nextId++
        return Page(
            id = id,
            documentId = 0,
            pageOrder = order,
            originalImagePath = "p$id.jpg",
            extractedText = text,
            ocrStatus = OcrStatus.Completed,
            createdAt = EPOCH,
            updatedAt = EPOCH,
        )
    }

    private fun doc(
        id: Long,
        title: String,
        pages: List<Page> = emptyList(),
        tags: List<Tag> = emptyList(),
        updatedAt: Instant = EPOCH,
    ) = Document(
        id = id,
        title = title,
        createdAt = EPOCH,
        updatedAt = updatedAt,
        pages = pages,
        pageCount = pages.size,
        tags = tags,
    )

    @Test
    fun `blank query yields no results`() {
        val results = SearchRanker.rank("   ", listOf(doc(1, "Invoice")))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `title match outranks body match`() {
        val titleHit = doc(1, "Quarterly Invoice")
        val bodyHit = doc(2, "Random Notes", pages = listOf(page("the invoice is attached", 0)))

        val results = SearchRanker.rank("invoice", listOf(bodyHit, titleHit))

        assertEquals(2, results.size)
        assertEquals(1L, results.first().document.id)
        assertTrue(results.first().titleMatched)
    }

    @Test
    fun `tag match ranks between title and body`() {
        val titleHit = doc(1, "Tax invoice 2026")
        val tagHit = doc(2, "Untitled", tags = listOf(Tag(9, "invoice")))
        val bodyHit = doc(3, "Untitled", pages = listOf(page("see the invoice below", 0)))

        val ids = SearchRanker.rank("invoice", listOf(bodyHit, tagHit, titleHit)).map { it.document.id }

        assertEquals(listOf(1L, 2L, 3L), ids)
    }

    @Test
    fun `covering more query terms scores higher`() {
        val both = doc(1, "Untitled", pages = listOf(page("annual financial report summary", 0)))
        val one = doc(2, "Untitled", pages = listOf(page("annual annual annual numbers", 1)))

        val results = SearchRanker.rank("annual report", listOf(one, both))

        assertEquals(1L, results.first().document.id)
    }

    @Test
    fun `snippet highlights the matched term and points at the right page`() {
        val document = doc(
            1,
            "Report",
            pages = listOf(
                page("nothing relevant here", 0),
                page("the secret password is hidden on this page", 1),
            ),
        )

        val result = SearchRanker.rank("password", listOf(document)).single()
        val snippet = result.snippet!!

        assertEquals(1, snippet.pageIndex)
        assertEquals(listOf(1), result.matchingPageIndices)
        assertTrue(snippet.highlights.isNotEmpty())
        val (start, end) = snippet.highlights.first().let { it.start to it.endExclusive }
        assertEquals("password", snippet.text.substring(start, end).lowercase())
    }

    @Test
    fun `non-matching document is dropped`() {
        val results = SearchRanker.rank("invoice", listOf(doc(1, "Holiday photos")))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `purely title match has no snippet`() {
        val result = SearchRanker.rank("invoice", listOf(doc(1, "Invoice"))).single()
        assertNull(result.snippet)
        assertTrue(result.titleMatched)
    }

    @Test
    fun `tokenize splits punctuation and dedupes`() {
        assertEquals(listOf("annual", "report"), SearchRanker.tokenize("Annual, report; annual!"))
    }

    companion object {
        private val EPOCH: Instant = Instant.ofEpochMilli(0)
    }
}
