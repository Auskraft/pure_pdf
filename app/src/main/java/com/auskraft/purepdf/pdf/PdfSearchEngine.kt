package com.auskraft.purepdf.pdf

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** A single text match: 0-based page and the char range within that page's text. */
data class SearchMatch(
    val page: Int,
    val charStart: Int,
    val charLen: Int,
)

/**
 * Whole-document, case-insensitive text search over the lazily-extracted page text. Indices are
 * taken from the original page text (not a lowercased copy) so they stay aligned with Pdfium's
 * character positions used to compute highlight rectangles.
 */
class PdfSearchEngine(private val controller: PdfDocumentController) {

    suspend fun search(query: String): List<SearchMatch> = coroutineScope {
        val needle = query.trim()
        if (needle.length < MIN_QUERY) return@coroutineScope emptyList()

        val results = ArrayList<SearchMatch>()
        for (page in 0 until controller.pageCount) {
            coroutineContext.ensureActive() // cancel promptly when the query changes
            val text = controller.pageText(page)
            if (text.isEmpty()) continue
            var idx = text.indexOf(needle, 0, ignoreCase = true)
            while (idx >= 0) {
                results.add(SearchMatch(page, idx, needle.length))
                idx = text.indexOf(needle, idx + needle.length, ignoreCase = true)
            }
        }
        results
    }

    companion object {
        const val MIN_QUERY = 2
    }
}
