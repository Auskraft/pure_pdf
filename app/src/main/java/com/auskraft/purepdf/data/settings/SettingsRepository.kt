package com.auskraft.purepdf.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Snapshot of all persisted user settings. */
data class AppSettings(
    val darkMode: Boolean = false,
    val keepPosition: Boolean = true,
    val accent: AccentPreset = AccentPreset.Blue,
    val libraryView: LibraryView = LibraryView.List,
    val density: Density = Density.Regular,
    val consentAccepted: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DARK = booleanPreferencesKey("dark_mode")
        val KEEP = booleanPreferencesKey("keep_position")
        val ACCENT = stringPreferencesKey("accent")
        val VIEW = stringPreferencesKey("library_view")
        val DENSITY = stringPreferencesKey("density")
        val CONSENT = booleanPreferencesKey("consent_accepted")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            darkMode = p[Keys.DARK] ?: false,
            keepPosition = p[Keys.KEEP] ?: true,
            accent = p[Keys.ACCENT]?.let { runCatching { AccentPreset.valueOf(it) }.getOrNull() } ?: AccentPreset.Blue,
            libraryView = p[Keys.VIEW]?.let { runCatching { LibraryView.valueOf(it) }.getOrNull() } ?: LibraryView.List,
            density = p[Keys.DENSITY]?.let { runCatching { Density.valueOf(it) }.getOrNull() } ?: Density.Regular,
            consentAccepted = p[Keys.CONSENT] ?: false,
        )
    }

    suspend fun setDarkMode(value: Boolean) = context.settingsDataStore.edit { it[Keys.DARK] = value }
    suspend fun setKeepPosition(value: Boolean) = context.settingsDataStore.edit { it[Keys.KEEP] = value }
    suspend fun setAccent(value: AccentPreset) = context.settingsDataStore.edit { it[Keys.ACCENT] = value.name }
    suspend fun setLibraryView(value: LibraryView) = context.settingsDataStore.edit { it[Keys.VIEW] = value.name }
    suspend fun setDensity(value: Density) = context.settingsDataStore.edit { it[Keys.DENSITY] = value.name }
    suspend fun setConsentAccepted(value: Boolean) = context.settingsDataStore.edit { it[Keys.CONSENT] = value }
}
