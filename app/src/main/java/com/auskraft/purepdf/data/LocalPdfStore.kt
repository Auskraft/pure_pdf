package com.auskraft.purepdf.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class StoredPdf(
    val uri: Uri,
    val sizeBytes: Long,
)

/** Private app storage for PDFs received through non-persistable content grants. */
class LocalPdfStore(private val context: Context) {
    private val importDir: File
        get() = File(context.filesDir, IMPORT_DIR)

    suspend fun importPdf(source: Uri, displayName: String, sourceKey: String): StoredPdf =
        withContext(Dispatchers.IO) {
            val dir = importDir.apply { mkdirs() }
            val fileName = PdfStorageNamer.importedFileName(sourceKey, displayName)
            val target = File(dir, fileName)
            val temp = File.createTempFile("$fileName-", ".tmp", dir)

            val copied = try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    FileOutputStream(temp).use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Не удалось открыть файл")
            } catch (error: Throwable) {
                temp.delete()
                throw error
            }

            if (copied <= 0L || temp.length() <= 0L) {
                temp.delete()
                throw IllegalStateException("Не удалось открыть файл")
            }

            try {
                moveReplacing(temp, target)
            } catch (error: Throwable) {
                temp.delete()
                throw error
            }
            StoredPdf(uri = Uri.fromFile(target), sizeBytes = target.length())
        }

    fun isLocalCopy(uri: Uri): Boolean {
        if (uri.scheme != ContentResolver.SCHEME_FILE) return false
        val path = uri.path ?: return false
        return runCatching {
            val filePath = File(path).canonicalPath
            val dirPath = importDir.canonicalPath.trimEnd(File.separatorChar) + File.separator
            filePath.startsWith(dirPath)
        }.getOrDefault(false)
    }

    fun deleteIfLocalCopy(uri: Uri) {
        if (!isLocalCopy(uri)) return
        runCatching { File(uri.path.orEmpty()).delete() }
    }

    private fun moveReplacing(source: File, target: File) {
        runCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val IMPORT_DIR = "imported_pdfs"
    }
}
