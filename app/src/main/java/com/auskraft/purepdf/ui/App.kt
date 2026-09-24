package com.auskraft.purepdf.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auskraft.purepdf.PurePdfApplication
import com.auskraft.purepdf.data.db.RecentDocEntity
import com.auskraft.purepdf.data.settings.AppSettings
import com.auskraft.purepdf.data.settings.LibraryView
import com.auskraft.purepdf.ui.docs.ConsentScreen
import com.auskraft.purepdf.ui.docs.DocsScreen
import com.auskraft.purepdf.ui.library.LibraryScreen
import com.auskraft.purepdf.ui.library.LibraryViewModel
import com.auskraft.purepdf.ui.rate.RateSheet
import com.auskraft.purepdf.ui.reader.ReaderScreen
import com.auskraft.purepdf.ui.settings.SettingsScreen
import com.auskraft.purepdf.ui.support.SupportPrompt
import com.auskraft.purepdf.ui.support.SupportScreen
import com.auskraft.purepdf.data.SupportManager
import com.auskraft.purepdf.ui.theme.PurePdfTheme
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppTab { Library, Settings }

/** Everything needed to open the reader for one document. */
data class OpenDoc(
    val uri: Uri,
    val docKey: String,
    val readerKey: String,
    val name: String,
    val initialPage: Int,
    val initialZoom: Float,
)

private val OpenDocSaver = Saver<OpenDoc?, List<String>>(
    save = { doc ->
        doc?.let {
            listOf(
                it.uri.toString(),
                it.docKey,
                it.readerKey,
                it.name,
                it.initialPage.toString(),
                it.initialZoom.toString(),
            )
        }
    },
    restore = { values ->
        if (values.size < 6) {
            null
        } else {
            OpenDoc(
                uri = Uri.parse(values[0]),
                docKey = values[1],
                readerKey = UUID.randomUUID().toString(),
                name = values[3],
                initialPage = values[4].toIntOrNull() ?: 1,
                initialZoom = values[5].toFloatOrNull() ?: 1f,
            )
        }
    },
)

@Composable
fun App(pendingUri: Uri?, onUriConsumed: () -> Unit) {
    val settingsVm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val settings by settingsVm.settings.collectAsStateWithLifecycle()

    val current = settings
    if (current == null) {
        // Settings not loaded yet — hold the navy splash colour to avoid a flash.
        Box(Modifier.fillMaxSize().background(Color(0xFF293244)))
        return
    }

    PurePdfTheme(accent = current.accent, darkTheme = current.darkMode) {
        if (!current.consentAccepted) {
            ConsentScreen(onAccept = { settingsVm.setConsentAccepted(true) })
        } else {
            AppContent(current, settingsVm, pendingUri, onUriConsumed)
        }
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
    val hazeState = remember { HazeState() }
    var tab by rememberSaveable { mutableStateOf(AppTab.Library) }
    var openDoc by rememberSaveable(stateSaver = OpenDocSaver) { mutableStateOf<OpenDoc?>(null) }
    var showDocs by remember { mutableStateOf(false) }
    var showRate by remember { mutableStateOf(false) }
    var showSupport by rememberSaveable { mutableStateOf(false) }
    var showSupportPrompt by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val application = LocalContext.current.applicationContext as PurePdfApplication
    val rating = application.container.ratingManager
    val support = application.container.supportManager

    fun showSnackbar(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun openSupportLink(url: String, payment: Boolean = false) {
        if (support.openLink(Uri.parse(url))) {
            if (payment) {
                showSupportPrompt = false
                scope.launch { support.markPaymentOpened() }
            }
        } else {
            showSupportPrompt = false
            showSnackbar("Не удалось открыть ссылку. Проверьте браузер")
        }
    }

    fun openEntity(entity: RecentDocEntity) {
        openDoc = OpenDoc(
            uri = Uri.parse(entity.uri),
            docKey = entity.docKey,
            readerKey = UUID.randomUUID().toString(),
            name = entity.name,
            initialPage = if (settings.keepPosition) entity.lastPage else 1,
            initialZoom = if (settings.keepPosition) entity.zoom else 1f,
        )
    }

    fun openUri(uri: Uri) {
        scope.launch {
            val entity = runCatching { libraryVm.open(uri) }.getOrNull()
            if (entity == null) {
                showSnackbar("Файл недоступен. Откройте его заново через «Открыть файл» или «Поделиться».")
                return@launch
            }
            openEntity(entity)
        }
    }

    fun reopenRecent(doc: RecentDocEntity) {
        scope.launch {
            val entity = runCatching { libraryVm.reopen(doc) }.getOrNull()
            if (entity == null) {
                showSnackbar("Файл недоступен. Откройте его заново через «Открыть файл» или «Поделиться».")
                return@launch
            }
            openEntity(entity)
        }
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(::openUri) }

    LaunchedEffect(pendingUri) {
        if (pendingUri != null) {
            openUri(pendingUri)
            onUriConsumed()
        }
    }

    LaunchedEffect(Unit) {
        val supportDue = support.shouldPrompt()
        val ratingDue = rating.shouldAutoPrompt()
        if (ratingDue || supportDue) {
            delay(1500)
            if (openDoc == null && tab == AppTab.Library && !showDocs && !showSupport) {
                if (ratingDue) {
                    rating.markPrompted()
                    showRate = true
                } else if (supportDue) {
                    support.markShown()
                    showSupportPrompt = true
                }
            }
        }
    }

    // On the Settings tab, system/gesture back returns to Library instead of exiting.
    BackHandler(enabled = openDoc == null && !showDocs && !showSupport && tab == AppTab.Settings) { tab = AppTab.Library }

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
            Box(Modifier.fillMaxSize()) {
                // Tab content is the haze source: the floating nav blurs whatever scrolls behind it.
                Box(Modifier.fillMaxSize().haze(hazeState)) {
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
                            onSupport = { showSupport = true },
                            onOpenDoc = ::reopenRecent,
                            onOpenFile = { openFileLauncher.launch(arrayOf("application/pdf")) },
                            loadPreview = { uriStr, key, w -> libraryVm.preview(Uri.parse(uriStr), key, w) },
                            onPageCount = libraryVm::setPageCount,
                        )

                        AppTab.Settings -> SettingsScreen(
                            settings = settings,
                            onToggleDark = settingsVm::setDarkMode,
                            onToggleKeep = settingsVm::setKeepPosition,
                            onAccent = settingsVm::setAccent,
                            onView = settingsVm::setLibraryView,
                            onDensity = settingsVm::setDensity,
                            onRate = { showRate = true },
                            onSupport = { showSupport = true },
                            onOpenDocs = { showDocs = true },
                        )
                    }
                }
                FloatingNav(
                    tab = tab,
                    hazeState = hazeState,
                    onTab = { tab = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        if (showDocs && current == null) {
            DocsScreen(onClose = { showDocs = false })
        }

        if (showSupport && current == null) {
            SupportScreen(
                onBack = { showSupport = false },
                onPayment = { openSupportLink(SupportManager.PAYMENT_URL, payment = true) },
                onTerms = { openSupportLink(SupportManager.TERMS_URL) },
                onAbout = { openSupportLink(SupportManager.ABOUT_URL) },
            )
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
                    when {
                        stars >= 4 && rating.hasStore -> rating.openStore()
                        stars >= 4 -> showSnackbar("Спасибо за оценку!")
                        else -> rating.openFeedbackEmail()
                    }
                    showRate = false
                },
                onDismiss = { showRate = false },
            )
        }
        if (showSupportPrompt) {
            SupportPrompt(
                onDismiss = { showSupportPrompt = false },
                onPayment = { openSupportLink(SupportManager.PAYMENT_URL, payment = true) },
            )
        }
    }
}

/** Floating frosted-glass pill nav: centred (icons close together), translucent, blurred backdrop. */
@Composable
private fun FloatingNav(
    tab: AppTab,
    hazeState: HazeState,
    onTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(30.dp)
    val style = HazeStyle(
        backgroundColor = colors.surface,
        tint = HazeTint(colors.surface.copy(alpha = 0.30f)),
        blurRadius = 20.dp,
    )
    Box(modifier.navigationBarsPadding().padding(bottom = 10.dp)) {
        Row(
            Modifier
                .shadow(10.dp, shape, clip = false)
                .clip(shape)
                .hazeChild(hazeState, shape = shape, style = style)
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), shape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavPillItem(Icons.Outlined.ChromeReaderMode, "Библиотека", tab == AppTab.Library) { onTab(AppTab.Library) }
            NavPillItem(Icons.Outlined.Settings, "Настройки", tab == AppTab.Settings) { onTab(AppTab.Settings) }
        }
    }
}

@Composable
private fun NavPillItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val color = if (selected) colors.primary else colors.onSurfaceVariant
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = color,
        )
    }
}
