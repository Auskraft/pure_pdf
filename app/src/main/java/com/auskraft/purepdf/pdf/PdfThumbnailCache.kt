package com.auskraft.purepdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.LruCache
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

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
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val document = PdfiumCore(context).newDocument(pfd)
                    val count = document.getPageCount()
                    pageCounts[docKey] = count
                    val page = document.openPage(0)
                    val aspect = if (page.getPageWidthPoint() <= 0) 1.3f
                    else page.getPageHeightPoint().toFloat() / page.getPageWidthPoint()
                    val h = (w * aspect).toInt().coerceIn(1, 2000)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.renderPageBitmap(bitmap, 0, 0, w, h, true, false)
                    page.close()
                    document.close()
                    bitmaps.put(key, bitmap)
                    DocPreview(bitmap, count)
                } ?: DocPreview(null, pageCounts[docKey] ?: 0)
            }.getOrElse { DocPreview(null, pageCounts[docKey] ?: 0) }
        }
}
