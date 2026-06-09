package com.auskraft.purepdf.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auskraft.purepdf.ui.OpenDoc
import com.auskraft.purepdf.ui.purePdfApplication
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val MAX_ZOOM = 3f
private const val DOUBLE_TAP_ZOOM = 1.75f
private const val MAX_RENDER_SCALE = 2.5f

private fun readerViewModelFactory(open: OpenDoc, keepPosition: Boolean): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val app = purePdfApplication()
            ReaderViewModel(
                application = app,
                libraryRepository = app.container.libraryRepository,
                uri = open.uri,
                docKey = open.docKey,
                name = open.name,
                initialPage = open.initialPage,
                initialZoom = open.initialZoom,
                keepPosition = keepPosition,
            )
        }
    }

@Composable
fun ReaderScreen(
    open: OpenDoc,
    darkMode: Boolean,
    keepPosition: Boolean,
    onToggleDark: () -> Unit,
    onBack: () -> Unit,
    showSnackbar: (String) -> Unit,
) {
    val vm: ReaderViewModel = viewModel(
        key = open.docKey,
        factory = remember(open.docKey, keepPosition) { readerViewModelFactory(open, keepPosition) },
    )
    val colors = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize().background(colors.surfaceDim)) {
        when (val state = vm.loadState) {
            is ReaderLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is ReaderLoad.Failed -> Text(
                state.message,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            is ReaderLoad.Ready -> ReaderContent(
                vm = vm,
                pageCount = state.pageCount,
                open = open,
                darkMode = darkMode,
                keepPosition = keepPosition,
                onToggleDark = onToggleDark,
                onBack = onBack,
                showSnackbar = showSnackbar,
            )
        }

        // Always-available back affordance while loading/failed.
        if (vm.loadState !is ReaderLoad.Ready) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = colors.onSurface)
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun ReaderContent(
    vm: ReaderViewModel,
    pageCount: Int,
    open: OpenDoc,
    darkMode: Boolean,
    keepPosition: Boolean,
    onToggleDark: () -> Unit,
    onBack: () -> Unit,
    showSnackbar: (String) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (open.initialPage - 1).coerceIn(0, pageCount - 1),
    )
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()

    var chromeVisible by remember { mutableStateOf(true) }
    var searchOpen by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(open.initialZoom.coerceIn(1f, MAX_ZOOM)) }
    var panX by remember { mutableFloatStateOf(0f) }
    var renderScale by remember { mutableFloatStateOf(zoom.coerceIn(1f, MAX_RENDER_SCALE)) }

    val currentPage by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            val item = layout.visibleItemsInfo.firstOrNull { it.offset + it.size > center }
                ?: layout.visibleItemsInfo.firstOrNull()
            (item?.index ?: 0) + 1
        }
    }
    val currentBookmarked = bookmarks.any { it.page == currentPage }

    fun goToPage(page: Int, animate: Boolean = true) {
        val target = (page - 1).coerceIn(0, pageCount - 1)
        scope.launch { if (animate) listState.animateScrollToItem(target) else listState.scrollToItem(target) }
    }

    // Re-render crisply once a pinch settles. Round the render resolution UP to the next
    // half-step so the rendered bitmap is always >= the displayed size — it gets downscaled
    // (sharp), never upscaled (the stretched/blurry look).
    LaunchedEffect(Unit) {
        snapshotFlow { zoom }.debounce(90).collect { z ->
            renderScale = (ceil(z * 2f) / 2f).coerceIn(1f, MAX_RENDER_SCALE)
        }
    }
    // Persist reading position (debounced).
    LaunchedEffect(Unit) {
        snapshotFlow { currentPage to zoom }.debounce(400).collect { (page, z) -> vm.savePosition(page, z) }
    }
    // Restore-position hint.
    LaunchedEffect(Unit) {
        if (keepPosition && open.initialPage > 1) showSnackbar("Продолжаем со страницы ${open.initialPage}")
    }
    // Follow the active search result.
    LaunchedEffect(vm.activeResultIndex, vm.searchResults.size, searchOpen) {
        if (searchOpen) vm.activeResult?.let { goToPage(it.page + 1) }
    }

    fun toggleZoom() {
        val zoomedIn = zoom > 1.01f
        zoom = if (zoomedIn) 1f else DOUBLE_TAP_ZOOM
        panX = 0f
        showSnackbar(if (zoomedIn) "Масштаб 100%" else "Масштаб ${(DOUBLE_TAP_ZOOM * 100).roundToInt()}%")
    }

    // Gesture/system back: close search first, otherwise leave the reader.
    BackHandler {
        if (searchOpen) {
            searchOpen = false
            vm.clearSearch()
        } else {
            onBack()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullWidthPx = with(density) { maxWidth.roundToPx() }
        val sidePx = with(density) { 8.dp.roundToPx() }
        val viewportWidthPx = (fullWidthPx - 2 * sidePx).coerceAtLeast(1)
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { chromeVisible = !chromeVisible },
                        onDoubleTap = { toggleZoom() },
                    )
                }
                .pointerInput(viewportWidthPx) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val zoomChange = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoomChange != 1f || pan != Offset.Zero) {
                                    val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                                    zoom = newZoom
                                    val extra = viewportWidthPx * (newZoom - 1f) / 2f
                                    panX = if (newZoom <= 1.01f) 0f else (panX + pan.x).coerceIn(-extra, extra)
                                    if (pan.y != 0f) scope.launch { listState.scrollBy(-pan.y) }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentPadding = PaddingValues(
                top = topInset + 60.dp,
                bottom = bottomInset + 64.dp,
                start = 8.dp,
                end = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(pageCount, key = { it }) { index ->
                PageView(
                    index = index,
                    pageNumber = index + 1,
                    total = pageCount,
                    viewportWidthPx = viewportWidthPx,
                    zoom = zoom,
                    renderScale = renderScale,
                    panX = panX,
                    darkMode = darkMode,
                    bookmarked = bookmarks.any { it.page == index + 1 },
                    activeMatch = if (searchOpen) vm.activeResult else null,
                    renderPage = vm::renderPage,
                    pageAspectRatio = vm::pageAspectRatio,
                    highlightRects = vm::highlightRects,
                )
            }
        }

        // ── Top chrome: search bar (when open) or app bar ──
        if (searchOpen) {
            Box(Modifier.align(Alignment.TopCenter)) {
                ReaderSearchBar(
                    query = vm.searchQuery,
                    resultCount = vm.searchResults.size,
                    activeIndex = vm.activeResultIndex,
                    onQueryChange = vm::onSearchQueryChange,
                    onClose = { searchOpen = false; vm.clearSearch() },
                    onPrev = vm::prevResult,
                    onNext = vm::nextResult,
                )
            }
        } else {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                ReaderTopBar(
                    title = vm.name,
                    isBookmarked = currentBookmarked,
                    darkMode = darkMode,
                    onBack = onBack,
                    onSearch = { searchOpen = true },
                    onBookmarks = { showBookmarks = true },
                    onToggleDark = onToggleDark,
                )
            }
        }

        // ── Bottom chrome ──
        AnimatedVisibility(
            visible = chromeVisible && !searchOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomBar(
                currentPage = currentPage,
                pageCount = pageCount,
                onSeek = { goToPage(it, animate = false) },
                onOpenJump = { showJump = true },
            )
        }

        // ── Floating page indicator when chrome hidden ──
        AnimatedVisibility(
            visible = !chromeVisible && !searchOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            ) {
                Text(
                    "$currentPage / $pageCount",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    if (showJump) {
        PageJumpDialog(
            total = pageCount,
            current = currentPage,
            onJump = { showJump = false; goToPage(it) },
            onDismiss = { showJump = false },
        )
    }

    if (showBookmarks) {
        BookmarksSheet(
            bookmarks = bookmarks,
            currentPage = currentPage,
            isCurrentBookmarked = currentBookmarked,
            onToggleCurrent = {
                vm.toggleBookmark(currentPage)
                showSnackbar(if (currentBookmarked) "Закладка удалена" else "Закладка: стр. $currentPage")
            },
            onJump = { showBookmarks = false; goToPage(it) },
            onDelete = { vm.removeBookmark(it) },
            onDismiss = { showBookmarks = false },
        )
    }
}
