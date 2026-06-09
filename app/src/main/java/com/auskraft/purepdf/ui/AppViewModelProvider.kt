package com.auskraft.purepdf.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auskraft.purepdf.PurePdfApplication
import com.auskraft.purepdf.ui.library.LibraryViewModel

/** Factories for the app-scoped ViewModels, wired to the Application's [AppContainer]. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { SettingsViewModel(purePdfApplication().container.settingsRepository) }
        initializer {
            val app = purePdfApplication()
            LibraryViewModel(app.container.libraryRepository, app.container.thumbnailCache, app)
        }
    }
}

fun CreationExtras.purePdfApplication(): PurePdfApplication =
    this[APPLICATION_KEY] as PurePdfApplication
