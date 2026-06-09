package com.auskraft.purepdf.data.settings

/**
 * User-selectable appearance settings. Persisted in DataStore (see SettingsRepository).
 */

/** Accent presets from the design. Each seeds a full M3 tonal scheme. */
enum class AccentPreset(val seedArgb: Long, val label: String) {
    Blue(0xFF1A73E8, "Синий"),
    Teal(0xFF0F766E, "Изумруд"),
    Terra(0xFFC2410C, "Терракота"),
    Mono(0xFF3F3F46, "Монохром"),
}

/** Library layout: a vertical list of rows, or a 2-column grid of cards. */
enum class LibraryView { List, Grid }

/** Row density for the library list — vertical padding and thumbnail size in dp. */
enum class Density(val rowPaddingDp: Int, val thumbDp: Int, val label: String) {
    Compact(8, 36, "Компактно"),
    Regular(12, 44, "Обычно"),
    Comfortable(16, 44, "Просторно"),
}
