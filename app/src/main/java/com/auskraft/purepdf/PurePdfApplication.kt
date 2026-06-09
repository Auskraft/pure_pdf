package com.auskraft.purepdf

import android.app.Application
import android.content.Context
import com.auskraft.purepdf.data.LibraryRepository
import com.auskraft.purepdf.data.RatingManager
import com.auskraft.purepdf.data.db.AppDatabase
import com.auskraft.purepdf.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds app-wide singletons. Kept tiny — no DI framework needed for an MVP. */
class AppContainer(context: Context) {
    private val database = AppDatabase.build(context)
    val settingsRepository = SettingsRepository(context.applicationContext)
    val libraryRepository = LibraryRepository(
        context.applicationContext,
        database.recentDocDao(),
        database.bookmarkDao(),
    )
    val ratingManager = RatingManager(context.applicationContext)
}

class PurePdfApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch { container.ratingManager.registerLaunch() }
    }
}
