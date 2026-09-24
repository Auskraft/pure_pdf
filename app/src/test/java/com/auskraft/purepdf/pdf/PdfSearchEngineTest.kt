package com.auskraft.purepdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfSearchEngineTest {

    @Test
    fun `finds matches case-insensitively using original text indexes`() {
        val matches = PdfSearchEngine.findMatchesOnPage(
            text = "Alpha beta ALPHA",
            needle = "alpha",
            page = 3,
        )

        assertEquals(
            listOf(
                SearchMatch(page = 3, charStart = 0, charLen = 5),
                SearchMatch(page = 3, charStart = 11, charLen = 5),
            ),
            matches,
        )
    }

    @Test
    fun `does not return overlapping matches`() {
        val matches = PdfSearchEngine.findMatchesOnPage(
            text = "aaaa",
            needle = "aa",
            page = 0,
        )

        assertEquals(
            listOf(
                SearchMatch(page = 0, charStart = 0, charLen = 2),
                SearchMatch(page = 0, charStart = 2, charLen = 2),
            ),
            matches,
        )
    }
}
