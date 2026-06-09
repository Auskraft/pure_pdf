package com.auskraft.purepdf.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auskraft.purepdf.data.LibraryRepository
import com.auskraft.purepdf.data.db.RecentDocEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {

    val recentDocs: StateFlow<List<RecentDocEntity>> = repository.recentDocs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** Records the document as just-opened (taking a persistable grant) and returns its entry. */
    suspend fun open(uri: Uri): RecentDocEntity = repository.recordOpen(uri)

    fun remove(docKey: String) = viewModelScope.launch { repository.removeDoc(docKey) }
}
