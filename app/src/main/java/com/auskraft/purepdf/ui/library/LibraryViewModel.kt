package com.auskraft.purepdf.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auskraft.purepdf.data.LibraryRepository
import com.auskraft.purepdf.data.db.RecentDocEntity
import com.auskraft.purepdf.pdf.DocPreview
import com.auskraft.purepdf.pdf.PdfThumbnailCache
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val thumbnailCache: PdfThumbnailCache,
    private val appContext: Context,
) : ViewModel() {

    val recentDocs: StateFlow<List<RecentDocEntity>> = repository.recentDocs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** Records the document as just-opened (taking a persistable grant) and returns its entry. */
    suspend fun open(uri: Uri): RecentDocEntity = repository.recordOpen(uri)

    /** First-page thumbnail + page count for a recent document. */
    suspend fun preview(uri: Uri, docKey: String, widthPx: Int): DocPreview =
        thumbnailCache.preview(appContext, uri, docKey, widthPx)

    fun setPageCount(docKey: String, count: Int) =
        viewModelScope.launch { repository.savePageCount(docKey, count) }

    fun remove(docKey: String) = viewModelScope.launch { repository.removeDoc(docKey) }
}
