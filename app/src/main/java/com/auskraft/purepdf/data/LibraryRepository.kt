package com.auskraft.purepdf.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.auskraft.purepdf.data.db.BookmarkDao
import com.auskraft.purepdf.data.db.BookmarkEntity
import com.auskraft.purepdf.data.db.RecentDocDao
import com.auskraft.purepdf.data.db.RecentDocEntity
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the library: recent documents (with remembered position) and
 * bookmarks. Resolves document metadata via the ContentResolver and persists SAF grants so
 * recents survive restarts.
 */
class LibraryRepository(
    private val context: Context,
    private val recentDao: RecentDocDao,
    private val bookmarkDao: BookmarkDao,
) {
    val recentDocs: Flow<List<RecentDocEntity>> = recentDao.observeAll()

    /**
     * Record (or refresh) a document as just-opened. Tries to take a persistable read grant so
     * the file can be reopened later. Returns the stored entity (with its remembered position).
     */
    suspend fun recordOpen(uri: Uri): RecentDocEntity {
        val persistable = tryTakePersistablePermission(uri)
        val (name, size) = queryNameSize(uri)
        val key = uri.toString()
        val now = System.currentTimeMillis()
        val existing = recentDao.get(key)
        val entity = (existing ?: RecentDocEntity(
            docKey = key,
            uri = key,
            name = name,
            sizeBytes = size,
            lastOpened = now,
            lastPage = 1,
            zoom = 1f,
            persistable = persistable,
        )).copy(
            name = name,
            sizeBytes = if (size > 0) size else (existing?.sizeBytes ?: 0L),
            lastOpened = now,
            persistable = persistable || (existing?.persistable ?: false),
        )
        recentDao.upsert(entity)
        return entity
    }

    suspend fun getDoc(key: String): RecentDocEntity? = recentDao.get(key)

    suspend fun savePosition(key: String, page: Int, zoom: Float) =
        recentDao.updatePosition(key, page, zoom)

    suspend fun removeDoc(key: String) = recentDao.deleteByKey(key)

    fun bookmarks(key: String): Flow<List<BookmarkEntity>> = bookmarkDao.observeForDoc(key)

    suspend fun addBookmark(key: String, page: Int, label: String) =
        bookmarkDao.upsert(BookmarkEntity(key, page, label, System.currentTimeMillis()))

    suspend fun removeBookmark(key: String, page: Int) = bookmarkDao.delete(key, page)

    private fun tryTakePersistablePermission(uri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)

    private fun queryNameSize(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Документ.pdf"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && !c.isNull(nameIdx)) name = c.getString(nameIdx)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }
}
