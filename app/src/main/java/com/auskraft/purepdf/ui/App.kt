package com.auskraft.purepdf.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auskraft.purepdf.data.settings.AppSettings
import com.auskraft.purepdf.data.settings.LibraryView
import com.auskraft.purepdf.ui.library.LibraryScreen
import com.auskraft.purepdf.ui.library.LibraryViewModel
import com.auskraft.purepdf.ui.reader.ReaderScreen
import com.auskraft.purepdf.ui.settings.SettingsScreen
import com.auskraft.purepdf.ui.theme.PurePdfTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.auskraft.purepdf.PurePdfApplication
import com.auskraft.purepdf.ui.docs.DocsScreen
import com.auskraft.purepdf.ui.rate.RateSheet

enum class AppTab { Library, Settings }

/** Everything needed to open the reader for one document. */
data class OpenDoc(
    val uri: Uri,
    val docKey: String,
    val name: String,
    val initialPage: Int,
    val initialZoom: Float,
)

@Composable
fun App(pendingUri: Uri?, onUriConsumed: () -> Unit) {
    val settingsVm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val settings by settingsVm.settings.collectAsStateWithLifecycle()

    PurePdfTheme(accent = settings.accent, darkTheme = settings.darkMode) {
        AppContent(settings, settingsVm, pendingUri, onUriConsumed)
    }
}

@Composable
private fun AppContent(
    settings: AppSettings,
    settingsVm: SettingsViewModel,
    pendingUri: Uri?,
    onUriConsumed: () -> Unit,
) {
    val libraryVm: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val recents by libraryVm.recentDocs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(AppTab.Library) }
    var openDoc by remember { mutableStateOf<OpenDoc?>(null) }
    var showDocs by remember { mutableStateOf(false) }
    var showRate by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val application = LocalContext.current.applicationContext as PurePdfApplication
    val rating = application.container.ratingManager

    fun showSnackbar(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun openUri(uri: Uri) {
        scope.launch {
            val entity = runCatching { libraryVm.open(uri) }.getOrNull()
            if (entity == null) {
                showSnackbar("Не удалось открыть файл")
                return@launch
            }
            openDoc = OpenDoc(
                uri = uri,
                docKey = entity.docKey,
                name = entity.name,
                initialPage = if (settings.keepPosition) entity.lastPage else 1,
                initialZoom = if (settings.keepPosition) entity.zoom else 1f,
            )
        }
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(::openUri) }

    androidx.compose.runtime.LaunchedEffect(pendingUri) {
        if (pendingUri != null) {
            openUri(pendingUri)
            onUriConsumed()
        }
    }

    // Ask for a rating once, after a few launches.
    LaunchedEffect(Unit) {
        if (rating.shouldAutoPrompt()) {
            delay(1500)
            rating.markPrompted()
            showRate = true
        }
    }

    // On the Settings tab, system/gesture back returns to Library instead of exiting.
    BackHandler(enabled = openDoc == null && !showDocs && tab == AppTab.Settings) { tab = AppTab.Library }

    Box(Modifier.fillMaxSize().background(colors.surface)) {
        val current = openDoc
        if (current != null) {
            ReaderScreen(
                open = current,
                darkMode = settings.darkMode,
                keepPosition = settings.keepPosition,
                onToggleDark = { settingsVm.setDarkMode(!settings.darkMode) },
                onBack = { openDoc = null },
                showSnackbar = ::showSnackbar,
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        AppTab.Library -> LibraryScreen(
                            recents = recents,
                            view = settings.libraryView,
                            density = settings.density,
                            onToggleView = {
                                settingsVm.setLibraryView(
                                    if (settings.libraryView == LibraryView.List) LibraryView.Grid else LibraryView.List,
                                )
                            },
                            onOpenDoc = { openUri(Uri.parse(it.uri)) },
                            onOpenFile = { openFileLauncher.launch(arrayOf("application/pdf")) },
                        )

                        AppTab.Settings -> SettingsScreen(
                            settings = settings,
                            onToggleDark = settingsVm::setDarkMode,
                            onToggleKeep = settingsVm::setKeepPosition,
                            onAccent = settingsVm::setAccent,
                            onView = settingsVm::setLibraryView,
                            onDensity = settingsVm::setDensity,
                            onRate = { showRate = true },
                            onOpenDocs = { showDocs = true },
                        )
                    }
                }
                BottomNav(tab) { tab = it }
            }
        }

        if (showDocs && current == null) {
            DocsScreen(onClose = { showDocs = false })
        }

        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = if (current == null) 96.dp else 80.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(10.dp),
                containerColor = colors.onSurface,
                contentColor = colors.surface,
            )
        }

        if (showRate) {
            RateSheet(
                onPick = { stars ->
                    scope.launch { rating.markDone() }
                    if (stars >= 4) rating.openStore() else rating.openFeedbackEmail()
                    showRate = false
                },
                onDismiss = { showRate = false },
            )
        }
    }
}

@Composable
private fun BottomNav(tab: AppTab, onTab: (AppTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        HorizontalDivider(color = colors.outlineVariant)
        NavigationBar(containerColor = colors.surfaceContainer) {
            NavigationBarItem(
                selected = tab == AppTab.Library,
                onClick = { onTab(AppTab.Library) },
                icon = { Icon(Icons.Outlined.ChromeReaderMode, contentDescription = null) },
                label = { Text("Библиотека") },
            )
            NavigationBarItem(
                selected = tab == AppTab.Settings,
                onClick = { onTab(AppTab.Settings) },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                label = { Text("Настройки") },
            )
        }
    }
}
