package com.scanforge.core.domain.library

import com.scanforge.core.domain.model.Document
import com.scanforge.core.domain.model.Page

/**
 * Pure, deterministic relevance ranking for document search. The data layer uses Room FTS to narrow
 * the candidate set quickly; this ranker then scores and orders those candidates and builds the
 * highlighted, jump-to-page snippets. Keeping it free of Android/Room makes search ranking unit
 * testable in isolation.
 *
 * Scoring favours where the match occurs: a hit in the **title** outweighs a **tag** hit, which
 * outweighs **body** (page) text. Each additional occurrence adds a smaller amount, and matching
 * more of the distinct query terms is rewarded so multi-word queries prefer documents that contain
 * all of them.
 */
object SearchRanker {

    private const val TITLE_WEIGHT = 100
    private const val TAG_WEIGHT = 40
    private const val BODY_WEIGHT = 10
    private const val TERM_COVERAGE_BONUS = 25
    private const val SNIPPET_RADIUS = 48

    /** Splits a raw query into normalised lowercase search terms. */
    fun tokenize(query: String): List<String> =
        query.lowercase().split(NON_WORD).filter { it.isNotBlank() }.distinct()

    /**
     * Scores and orders [candidates] for [query]. Documents that match nothing are dropped. Ties on
     * score fall back to most-recently-updated so the ordering is stable and sensible.
     */
    fun rank(query: String, candidates: List<Document>): List<SearchResult> {
        val terms = tokenize(query)
        if (terms.isEmpty()) return emptyList()
        return candidates
            .mapNotNull { document -> score(terms, document) }
            .sortedWith(compareByDescending<SearchResult> { it.score }.thenByDescending { it.document.updatedAt })
    }

    private fun score(terms: List<String>, document: Document): SearchResult? {
        val title = document.title.lowercase()
        val tagText = document.tags.joinToString(" ") { it.name }.lowercase()

        var score = 0
        val matchedTerms = mutableSetOf<String>()
        var titleMatched = false
        var tagMatched = false

        terms.forEach { term ->
            val inTitle = countOccurrences(title, term)
            if (inTitle > 0) {
                score += TITLE_WEIGHT + (inTitle - 1) * (TITLE_WEIGHT / 4)
                titleMatched = true
                matchedTerms += term
            }
            val inTags = countOccurrences(tagText, term)
            if (inTags > 0) {
                score += TAG_WEIGHT
                tagMatched = true
                matchedTerms += term
            }
        }

        // Per-page body scoring, also locating the best snippet and all matching pages.
        var bestSnippet: SearchSnippet? = null
        var bestSnippetMatches = 0
        val matchingPageIndices = mutableListOf<Int>()

        document.pages.forEachIndexed { index, page ->
            val text = page.searchText()
            if (text.isBlank()) return@forEachIndexed
            val lower = text.lowercase()
            var pageMatches = 0
            terms.forEach { term ->
                val n = countOccurrences(lower, term)
                if (n > 0) {
                    pageMatches += n
                    score += BODY_WEIGHT + (n - 1) * (BODY_WEIGHT / 5)
                    matchedTerms += term
                }
            }
            if (pageMatches > 0) {
                matchingPageIndices += index
                if (pageMatches > bestSnippetMatches) {
                    bestSnippetMatches = pageMatches
                    bestSnippet = buildSnippet(page.id, index, text, terms)
                }
            }
        }

        if (matchedTerms.isEmpty()) return null
        // Reward covering more of the query (all words present beats one repeated word).
        score += matchedTerms.size * TERM_COVERAGE_BONUS

        return SearchResult(
            document = document,
            score = score,
            snippet = bestSnippet,
            matchingPageIndices = matchingPageIndices,
            titleMatched = titleMatched,
            tagMatched = tagMatched,
        )
    }

    /**
     * Builds a single-line excerpt of [text] centred on the first matched term, with every term
     * occurrence inside the window recorded as a [Highlight] (offsets relative to the returned text,
     * including any leading ellipsis).
     */
    fun buildSnippet(pageId: Long, pageIndex: Int, text: String, terms: List<String>): SearchSnippet {
        val flat = text.replace(WHITESPACE, " ").trim()
        val lower = flat.lowercase()
        val firstMatch = terms.mapNotNull { term -> lower.indexOf(term).takeIf { it >= 0 } }.minOrNull() ?: 0

        val rawStart = (firstMatch - SNIPPET_RADIUS).coerceAtLeast(0)
        val rawEnd = (firstMatch + SNIPPET_RADIUS).coerceAtMost(flat.length)
        val leadingEllipsis = rawStart > 0
        val trailingEllipsis = rawEnd < flat.length
        val prefix = if (leadingEllipsis) "… " else ""
        val core = flat.substring(rawStart, rawEnd)
        val snippetText = prefix + core + if (trailingEllipsis) " …" else ""

        val coreLower = core.lowercase()
        val offset = prefix.length
        val highlights = mutableListOf<Highlight>()
        terms.forEach { term ->
            var from = 0
            while (true) {
                val at = coreLower.indexOf(term, from)
                if (at < 0) break
                highlights += Highlight(offset + at, offset + at + term.length)
                from = at + term.length
            }
        }
        return SearchSnippet(pageId, pageIndex, snippetText, highlights.sortedBy { it.start })
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var from = 0
        while (true) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) break
            count++
            from = at + needle.length
        }
        return count
    }

    /** The page's user-corrected text if present, otherwise its OCR full text. */
    private fun Page.searchText(): String = extractedText ?: ocrData?.fullText ?: ""

    private val NON_WORD = Regex("[^\\p{L}\\p{Nd}]+")
    private val WHITESPACE = Regex("\\s+")
}
