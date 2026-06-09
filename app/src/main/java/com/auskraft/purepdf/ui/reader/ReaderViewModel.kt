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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
        viewModelScope.launch {
            val opened = runCatching {
                PdfDocumentController.open(getApplication(), uri).also {
                    controller = it
                    searchEngine = PdfSearchEngine(it)
                }
            }
            loadState = opened.fold(
                onSuccess = {
                    libraryRepository.savePageCount(docKey, it.pageCount)
                    ReaderLoad.Ready(it.pageCount)
                },
                onFailure = { ReaderLoad.Failed(it.message ?: "Не удалось открыть документ") },
            )
        }
    }

    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? =
        controller?.renderPage(index, widthPx)

    suspend fun pageAspectRatio(index: Int): Float =
        controller?.pageSize(index)?.aspectRatio ?: DEFAULT_ASPECT

    suspend fun highlightRects(index: Int, charStart: Int, charLen: Int, widthPx: Int): List<RectF> =
        controller?.highlightRects(index, charStart, charLen, widthPx) ?: emptyList()

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

    override fun onCleared() {
        controller?.close()
        controller = null
        super.onCleared()
    }

    companion object {
        const val DEFAULT_ASPECT = 1.3f
    }
}
