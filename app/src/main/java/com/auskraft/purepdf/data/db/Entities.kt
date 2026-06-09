package com.auskraft.purepdf.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A recently opened document, including its remembered reading position. */
@Entity(tableName = "recent_docs")
data class RecentDocEntity(
    @PrimaryKey val docKey: String,
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val lastOpened: Long,
    val lastPage: Int = 1,
    val zoom: Float = 1f,
    /** True if we hold a persistable URI grant (so it can be reopened after restart). */
    val persistable: Boolean = true,
)

/** A bookmark on a page of a document, keyed by (docKey, page). */
@Entity(tableName = "bookmarks", primaryKeys = ["docKey", "page"])
data class BookmarkEntity(
    val docKey: String,
    val page: Int,
    val label: String,
    val createdAt: Long,
)
