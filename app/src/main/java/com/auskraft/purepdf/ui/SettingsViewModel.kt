package com.auskraft.purepdf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auskraft.purepdf.data.settings.AccentPreset
import com.auskraft.purepdf.data.settings.AppSettings
import com.auskraft.purepdf.data.settings.Density
import com.auskraft.purepdf.data.settings.LibraryView
import com.auskraft.purepdf.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    // null = not loaded yet (keeps the splash navy on screen instead of flashing the consent gate).
    val settings: StateFlow<AppSettings?> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun setDarkMode(value: Boolean) = viewModelScope.launch { repository.setDarkMode(value) }
    fun setKeepPosition(value: Boolean) = viewModelScope.launch { repository.setKeepPosition(value) }
    fun setAccent(value: AccentPreset) = viewModelScope.launch { repository.setAccent(value) }
    fun setLibraryView(value: LibraryView) = viewModelScope.launch { repository.setLibraryView(value) }
    fun setDensity(value: Density) = viewModelScope.launch { repository.setDensity(value) }
    fun setConsentAccepted(value: Boolean) = viewModelScope.launch { repository.setConsentAccepted(value) }
}
