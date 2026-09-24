package com.auskraft.purepdf.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File

/** First-page bitmap + total page count for a library document. */
data class DocPreview(val bitmap: Bitmap?, val pageCount: Int)

/**
 * Renders and caches first-page thumbnails for the library. All Pdfium access is serialized on a
 * single thread. Returns a null bitmap gracefully when a document can't be opened (e.g. a lost
 * URI permission), so the caller can fall back to a placeholder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PdfThumbnailCache {

    private val native: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val bitmaps = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val pageCounts = HashMap<String, Int>()

    suspend fun preview(context: Context, uri: Uri, docKey: String, widthPx: Int): DocPreview =
        withContext(native) {
            val w = widthPx.coerceIn(1, 1000)
            val key = "$docKey@$w"
            bitmaps.get(key)?.let { return@withContext DocPreview(it, pageCounts[docKey] ?: 0) }

            runCatching {
                openDescriptor(context, uri).use { pfd ->
                    val document = PdfiumCore(context).newDocument(pfd)
                    try {
                        val count = document.getPageCount()
                        pageCounts[docKey] = count
                        val page = document.openPage(0)
                        try {
                            val aspect = if (page.getPageWidthPoint() <= 0) 1.3f
                            else page.getPageHeightPoint().toFloat() / page.getPageWidthPoint()
                            val h = (w * aspect).toInt().coerceIn(1, 2000)
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.renderPageBitmap(bitmap, 0, 0, w, h, true, false)
                            bitmaps.put(key, bitmap)
                            DocPreview(bitmap, count)
                        } finally {
                            page.close()
                        }
                    } finally {
                        document.close()
                    }
                }
            }.getOrElse { DocPreview(null, pageCounts[docKey] ?: 0) }
        }

    private fun openDescriptor(context: Context, uri: Uri): ParcelFileDescriptor {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val path = uri.path ?: throw IllegalStateException("Не удалось открыть документ")
            return ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
        }
        return context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Не удалось открыть документ")
    }
}
