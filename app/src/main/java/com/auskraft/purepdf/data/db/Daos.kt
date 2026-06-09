package com.auskraft.purepdf.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDocDao {
    @Query("SELECT * FROM recent_docs ORDER BY lastOpened DESC")
    fun observeAll(): Flow<List<RecentDocEntity>>

    @Query("SELECT * FROM recent_docs WHERE docKey = :key")
    suspend fun get(key: String): RecentDocEntity?

    @Upsert
    suspend fun upsert(doc: RecentDocEntity)

    @Query("UPDATE recent_docs SET lastPage = :page, zoom = :zoom WHERE docKey = :key")
    suspend fun updatePosition(key: String, page: Int, zoom: Float)

    @Query("DELETE FROM recent_docs WHERE docKey = :key")
    suspend fun deleteByKey(key: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE docKey = :key ORDER BY page")
    fun observeForDoc(key: String): Flow<List<BookmarkEntity>>

    @Upsert
    suspend fun upsert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE docKey = :key AND page = :page")
    suspend fun delete(key: String, page: Int)
}
