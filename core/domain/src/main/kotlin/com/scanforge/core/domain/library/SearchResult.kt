package com.scanforge.core.domain.library

import com.scanforge.core.domain.model.Document

/** A contiguous match highlight within [SearchSnippet.text], as a `[start, endExclusive)` range. */
data class Highlight(val start: Int, val endExclusive: Int)

/**
 * A short excerpt of matching page text with the query terms marked for highlighting, plus enough
 * location to jump straight to the page.
 */
data class SearchSnippet(
    val pageId: Long,
    /** Zero-based page position within the document, for "jump to page". */
    val pageIndex: Int,
    val text: String,
    val highlights: List<Highlight>,
)

/**
 * One ranked search hit: the [document], its relevance [score], the best [snippet] (or `null` when
 * the match was purely on the title/tags), and every page index that contained a match.
 */
data class SearchResult(
    val document: Document,
    val score: Int,
    val snippet: SearchSnippet?,
    val matchingPageIndices: List<Int>,
    val titleMatched: Boolean,
    val tagMatched: Boolean,
)
