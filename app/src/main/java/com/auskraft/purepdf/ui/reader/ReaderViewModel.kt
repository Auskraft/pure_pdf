package com.auskraft.purepdf.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.auskraft.purepdf.data.LibraryRepository
import com.auskraft.purepdf.data.db.BookmarkEntity
import com.auskraft.purepdf.pdf.PdfDocumentController
import com.auskraft.purepdf.pdf.PdfSearchEngine
import com.auskraft.purepdf.pdf.SearchMatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

sealed interface ReaderLoad {
    data object Loading : ReaderLoad
    data class Ready(val pageCount: Int) : ReaderLoad
    data class Failed(val message: String) : ReaderLoad
}

class ReaderViewModel(
    application: Application,
    private val libraryRepository: LibraryRepository,
    private val uri: Uri,
    val docKey: String,
    val name: String,
    val initialPage: Int,
    val initialZoom: Float,
    private val keepPosition: Boolean,
) : AndroidViewModel(application) {

    var loadState by mutableStateOf<ReaderLoad>(ReaderLoad.Loading)
        private set

    private var controller: PdfDocumentController? = null
    private var searchEngine: PdfSearchEngine? = null
    private var openJob: Job? = null

    val bookmarks: StateFlow<List<BookmarkEntity>> =
        libraryRepository.bookmarks(docKey).stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
        )

    // ── Search state (observed by Compose) ──
    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<SearchMatch>>(emptyList())
        private set
    var activeResultIndex by mutableStateOf(0)
        private set
    private var searchJob: Job? = null

    init {
        openJob = viewModelScope.launch {
            var opened: PdfDocumentController? = null
            try {
                opened = PdfDocumentController.open(getApplication(), uri)
                coroutineContext.ensureActive()
                controller = opened
                searchEngine = PdfSearchEngine(opened)
                val count = opened.pageCount
                runCatching { libraryRepository.savePageCount(docKey, count) }
                loadState = ReaderLoad.Ready(count)
                opened = null
            } catch (error: CancellationException) {
                opened?.close()
                throw error
            } catch (error: Throwable) {
                opened?.close()
                loadState = ReaderLoad.Failed("Файл недоступен. Откройте его заново через «Открыть файл» или «Поделиться».")
            }
        }
    }

    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? {
        val current = controller ?: return null
        return try {
            current.renderPage(index, widthPx)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            null
        }
    }

    suspend fun pageAspectRatio(index: Int): Float {
        val current = controller ?: return DEFAULT_ASPECT
        return try {
            current.pageSize(index).aspectRatio
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DEFAULT_ASPECT
        }
    }

    suspend fun highlightRects(index: Int, charStart: Int, charLen: Int, widthPx: Int): List<RectF> {
        val current = controller ?: return emptyList()
        return try {
            current.highlightRects(index, charStart, charLen, widthPx)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            emptyList()
        }
    }

    fun savePosition(page: Int, zoom: Float) {
        if (!keepPosition) return
        viewModelScope.launch { libraryRepository.savePosition(docKey, page, zoom) }
    }

    fun toggleBookmark(page: Int) {
        viewModelScope.launch {
            val existing = bookmarks.value
            if (existing.any { it.page == page }) {
                libraryRepository.removeBookmark(docKey, page)
            } else {
                libraryRepository.addBookmark(docKey, page, labelForPage(page))
            }
        }
    }

    fun removeBookmark(page: Int) {
        viewModelScope.launch { libraryRepository.removeBookmark(docKey, page) }
    }

    private suspend fun labelForPage(page: Int): String {
        val text = controller?.pageText(page - 1)?.trim().orEmpty()
        val firstLine = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        return firstLine?.take(60) ?: "Страница $page"
    }

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        activeResultIndex = 0
        searchJob?.cancel()
        if (query.trim().length < PdfSearchEngine.MIN_QUERY) {
            searchResults = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            searchResults = searchEngine?.search(query) ?: emptyList()
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchQuery = ""
        searchResults = emptyList()
        activeResultIndex = 0
    }

    fun nextResult() {
        if (searchResults.isNotEmpty()) {
            activeResultIndex = (activeResultIndex + 1) % searchResults.size
        }
    }

    fun prevResult() {
        if (searchResults.isNotEmpty()) {
            activeResultIndex = (activeResultIndex - 1 + searchResults.size) % searchResults.size
        }
    }

    val activeResult: SearchMatch?
        get() = searchResults.getOrNull(activeResultIndex)

    fun closeDocument() {
        openJob?.cancel()
        openJob = null
        searchJob?.cancel()
        searchJob = null
        searchEngine = null
        controller?.close()
        controller = null
    }

    override fun onCleared() {
        closeDocument()
        super.onCleared()
    }

    companion object {
        const val DEFAULT_ASPECT = 1.3f
    }
}
