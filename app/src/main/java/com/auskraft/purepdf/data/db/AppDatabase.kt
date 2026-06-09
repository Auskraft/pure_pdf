package com.auskraft.purepdf.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RecentDocEntity::class, BookmarkEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentDocDao(): RecentDocDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "purepdf.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
