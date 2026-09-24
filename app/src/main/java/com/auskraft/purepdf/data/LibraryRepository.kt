package com.auskraft.purepdf.data

import android.content.ContentResolver
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
    private val localStore = LocalPdfStore(context)

    /**
     * Record (or refresh) a document as just-opened. Tries to take a persistable read grant so
     * the file can be reopened later. Non-persistable URIs (Telegram, MediaStore shares, etc.)
     * are imported into private app storage immediately while the temporary grant is still alive.
     * Returns the stored entity (with its remembered position).
     */
    suspend fun recordOpen(uri: Uri, docKeyOverride: String = uri.toString()): RecentDocEntity {
        val key = docKeyOverride
        val existing = recentDao.get(key)
        val (queriedName, size) = queryNameSize(uri)
        val name = if (existing != null && localStore.isLocalCopy(uri)) existing.name else queriedName
        val persistable = tryTakePersistablePermission(uri)
        val stored = readableUriFor(uri, name, key, persistable)
        val now = System.currentTimeMillis()
        val entity = (existing ?: RecentDocEntity(
            docKey = key,
            uri = stored.uri.toString(),
            name = name,
            sizeBytes = stored.sizeBytes.takeIf { it > 0L } ?: size,
            lastOpened = now,
            lastPage = 1,
            zoom = 1f,
            persistable = persistable,
        )).copy(
            uri = stored.uri.toString(),
            name = name,
            sizeBytes = when {
                stored.sizeBytes > 0L -> stored.sizeBytes
                size > 0L -> size
                else -> existing?.sizeBytes ?: 0L
            },
            lastOpened = now,
            persistable = persistable,
        )
        recentDao.upsert(entity)
        return entity
    }

    suspend fun getDoc(key: String): RecentDocEntity? = recentDao.get(key)

    suspend fun savePosition(key: String, page: Int, zoom: Float) =
        recentDao.updatePosition(key, page, zoom)

    suspend fun savePageCount(key: String, count: Int) {
        if (count > 0) recentDao.updatePageCount(key, count)
    }

    suspend fun removeDoc(key: String) {
        recentDao.get(key)?.let { localStore.deleteIfLocalCopy(Uri.parse(it.uri)) }
        bookmarkDao.deleteForDoc(key)
        recentDao.deleteByKey(key)
    }

    fun bookmarks(key: String): Flow<List<BookmarkEntity>> = bookmarkDao.observeForDoc(key)

    suspend fun addBookmark(key: String, page: Int, label: String) =
        bookmarkDao.upsert(BookmarkEntity(key, page, label, System.currentTimeMillis()))

    suspend fun removeBookmark(key: String, page: Int) = bookmarkDao.delete(key, page)

    private suspend fun readableUriFor(
        uri: Uri,
        name: String,
        key: String,
        persistable: Boolean,
    ): StoredPdf {
        if (localStore.isLocalCopy(uri)) {
            return StoredPdf(uri = uri, sizeBytes = fileSize(uri))
        }
        if (persistable) {
            return StoredPdf(uri = uri, sizeBytes = 0L)
        }
        return localStore.importPdf(uri, name, key)
    }

    private fun tryTakePersistablePermission(uri: Uri): Boolean {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        return runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        }.getOrDefault(false)
    }

    private fun fileSize(uri: Uri): Long {
        if (uri.scheme != ContentResolver.SCHEME_FILE) return 0L
        return runCatching { java.io.File(uri.path.orEmpty()).length() }.getOrDefault(0L)
    }

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
