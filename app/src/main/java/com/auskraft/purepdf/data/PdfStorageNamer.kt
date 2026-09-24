package com.auskraft.purepdf.data

import java.security.MessageDigest

/** Builds stable, filesystem-safe names for imported PDFs. */
object PdfStorageNamer {
    private const val MAX_BASE_CHARS = 72
    private val invalidFileChars = Regex("""[\\/:*?"<>|]|\p{Cntrl}""")
    private val whitespace = Regex("""\s+""")

    fun importedFileName(sourceKey: String, displayName: String): String {
        val cleanName = displayName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
            .ifBlank { "document.pdf" }
            .let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }
            .let { invalidFileChars.replace(it, "_") }
            .let { whitespace.replace(it, " ") }
            .trim('.', ' ')
            .ifBlank { "document.pdf" }

        val base = cleanName.substringBeforeLast('.', missingDelimiterValue = cleanName)
            .take(MAX_BASE_CHARS)
            .trim('.', ' ')
            .ifBlank { "document" }
        val hash = sourceKey.sha256Prefix()
        return "$base-$hash.pdf"
    }

    private fun String.sha256Prefix(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
