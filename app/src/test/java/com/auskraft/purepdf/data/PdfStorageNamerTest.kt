package com.auskraft.purepdf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfStorageNamerTest {

    @Test
    fun `adds pdf extension and stable hash`() {
        val first = PdfStorageNamer.importedFileName(
            sourceKey = "content://provider/doc/42",
            displayName = "Report",
        )
        val second = PdfStorageNamer.importedFileName(
            sourceKey = "content://provider/doc/42",
            displayName = "Report",
        )

        assertEquals(first, second)
        assertTrue(first.startsWith("Report-"))
        assertTrue(first.endsWith(".pdf"))
    }

    @Test
    fun `removes path and invalid filename characters`() {
        val name = PdfStorageNamer.importedFileName(
            sourceKey = "content://provider/doc/unsafe",
            displayName = """folder\bad:name?.pdf""",
        )

        assertTrue(name.startsWith("bad_name_-"))
        assertTrue(name.endsWith(".pdf"))
        assertTrue("""[\\/:*?"<>|]""".toRegex().find(name) == null)
    }
}
