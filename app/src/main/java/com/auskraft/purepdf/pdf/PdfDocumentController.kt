package com.auskraft.purepdf.pdf

import android.content.Context
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/** Page dimensions in PDF points (1/72"). aspectRatio = height / width. */
data class PageSize(val widthPoints: Int, val heightPoints: Int) {
    val aspectRatio: Float get() = if (widthPoints <= 0) 1.3f else heightPoints.toFloat() / widthPoints.toFloat()
}

/**
 * Owns an open PDF (PdfiumCore + PdfDocument + file descriptor) and renders pages to bitmaps
 * off the main thread. All native access is serialized onto a single thread because Pdfium is
 * not safe for concurrent use. Page text is extracted lazily and cached for search.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PdfDocumentController private constructor(
    private val pfd: ParcelFileDescriptor,
    private val document: PdfDocument,
) : Closeable {

    private val native: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    @Volatile private var closed = false

    val pageCount: Int = document.getPageCount()

    private val sizes = arrayOfNulls<PageSize>(pageCount)
    private val textCache = arrayOfNulls<String>(pageCount)

    private val bitmapCache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun pageSize(index: Int): PageSize = withContext(native) {
        checkOpen()
        sizes[index] ?: run {
            val page = document.openPage(index)
            val size = try {
                PageSize(page.getPageWidthPoint(), page.getPageHeightPoint())
            } finally {
                page.close()
            }
            sizes[index] = size
            size
        }
    }

    /** Render a page to a bitmap [widthPx] wide (height from aspect). Cached by page+width. */
    suspend fun renderPage(index: Int, widthPx: Int): Bitmap = withContext(native) {
        checkOpen()
        val w = widthPx.coerceIn(1, MAX_WIDTH_PX)
        val size = pageSize(index)
        val h = (w * size.aspectRatio).toInt().coerceIn(1, MAX_HEIGHT_PX)
        val key = "$index@$w"
        bitmapCache.get(key)?.let { return@withContext it }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val page = document.openPage(index)
        try {
            page.renderPageBitmap(bitmap, 0, 0, w, h, true, false)
        } finally {
            page.close()
        }
        bitmapCache.put(key, bitmap)
        bitmap
    }

    suspend fun pageText(index: Int): String = withContext(native) {
        checkOpen()
        textCache[index] ?: run {
            val text = runCatching {
                val page = document.openPage(index)
                try {
                    val textPage = page.openTextPage()
                    try {
                        val count = textPage.textPageCountChars().coerceAtLeast(0)
                        if (count > 0) (textPage.textPageGetText(0, count) ?: "") else ""
                    } finally {
                        textPage.close()
                    }
                } finally {
                    page.close()
                }
            }.getOrDefault("")
            textCache[index] = text
            text
        }
    }

    /**
     * Highlight rectangles for a char range on [index], in the coordinate space of a bitmap
     * [widthPx] wide (same transform [renderPage] uses), ready to scale onto the displayed page.
     */
    suspend fun highlightRects(index: Int, charStart: Int, charLen: Int, widthPx: Int): List<RectF> =
        withContext(native) {
            checkOpen()
            if (charLen <= 0) return@withContext emptyList()
            val w = widthPx.coerceIn(1, MAX_WIDTH_PX)
            val size = pageSize(index)
            val h = (w * size.aspectRatio).toInt().coerceIn(1, MAX_HEIGHT_PX)
            runCatching {
                val page = document.openPage(index)
                try {
                    val textPage = page.openTextPage()
                    try {
                        val count = textPage.textPageCountRects(charStart, charLen)
                        val rects = ArrayList<RectF>(count.coerceAtLeast(0))
                        for (i in 0 until count) {
                            val pageRect = textPage.textPageGetRect(i) ?: continue
                            rects.add(RectF(page.mapRectToDevice(0, 0, w, h, 0, pageRect)))
                        }
                        rects
                    } finally {
                        textPage.close()
                    }
                } finally {
                    page.close()
                }
            }.getOrDefault(emptyList())
        }

    override fun close() {
        if (closed) return
        runBlocking(native) {
            if (closed) return@runBlocking
            closed = true
            runCatching { document.close() }
            runCatching { pfd.close() }
            bitmapCache.evictAll()
        }
    }

    private fun checkOpen() {
        check(!closed) { "Document is closed" }
    }

    companion object {
        private const val MAX_WIDTH_PX = 2600
        private const val MAX_HEIGHT_PX = 6400

        private fun cacheBytes(): Int {
            val max = Runtime.getRuntime().maxMemory()
            return (max / 4L).coerceAtMost(96L * 1024 * 1024).toInt().coerceAtLeast(8 * 1024 * 1024)
        }

        suspend fun open(context: Context, uri: Uri): PdfDocumentController = withContext(Dispatchers.IO) {
            val pfd = openDescriptor(context, uri)
            val core = PdfiumCore(context)
            runCatching {
                PdfDocumentController(pfd, core.newDocument(pfd))
            }.getOrElse { error ->
                runCatching { pfd.close() }
                throw error
            }
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
}
