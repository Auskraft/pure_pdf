package com.auskraft.purepdf.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val MAX_ZOOM = 3f
private const val DOUBLE_TAP_ZOOM = 1.75f
private const val MAX_RENDER_SCALE = 2.5f
private const val BUTTON_ZOOM_STEP = 0.5f

/**
 * How the current zoom was applied. Gesture zoom keeps the existing navigation (two-finger pan)
 * and hides the zoom buttons; button zoom disables pinch and pans photo-style with one finger.
 */
private enum class ZoomMode { None, Gesture, Buttons }

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
    var zoomMode by remember { mutableStateOf(ZoomMode.None) }
    val readerHaze = remember { HazeState() }

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
    // Scroll indicator lingers briefly after scrolling stops.
    var scrollBarLinger by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            scrollBarLinger = true
        } else {
            delay(1500)
            scrollBarLinger = false
        }
    }
    // Follow the active search result.
    LaunchedEffect(vm.activeResultIndex, vm.searchResults.size, searchOpen) {
        if (searchOpen) vm.activeResult?.let { goToPage(it.page + 1) }
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

        // Set zoom keeping the viewport centre anchored (used by double-tap and the buttons).
        fun setZoomCentered(target: Float, mode: ZoomMode) {
            val t = target.coerceIn(1f, MAX_ZOOM)
            val k = t / zoom
            if (k != 1f) {
                val info = listState.layoutInfo
                val centerY = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                val firstTop = info.visibleItemsInfo.firstOrNull()?.offset ?: 0
                listState.dispatchRawDelta((centerY - firstTop) * (k - 1f))
            }
            val extra = viewportWidthPx * (t - 1f) / 2f
            panX = if (t <= 1.01f) 0f else (panX * k).coerceIn(-extra, extra)
            zoom = t
            zoomMode = if (t > 1.01f) mode else ZoomMode.None
        }

        fun toggleZoom() {
            val zoomedIn = zoom > 1.01f
            setZoomCentered(if (zoomedIn) 1f else DOUBLE_TAP_ZOOM, ZoomMode.Gesture)
            showSnackbar(if (zoomedIn) "Масштаб 100%" else "Масштаб ${(DOUBLE_TAP_ZOOM * 100).roundToInt()}%")
        }

        fun buttonZoom(delta: Float) {
            val target = (zoom + delta).coerceIn(1f, MAX_ZOOM)
            if (target == zoom) return
            // No snackbar here: it would overlap (and steal taps from) the zoom buttons,
            // and the zoom change itself is the feedback.
            setZoomCentered(target, ZoomMode.Buttons)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .haze(readerHaze)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { chromeVisible = !chromeVisible },
                        onDoubleTap = {
                            if (zoomMode == ZoomMode.Buttons) {
                                setZoomCentered(1f, ZoomMode.None)
                                showSnackbar("Масштаб 100%")
                            } else {
                                toggleZoom()
                            }
                        },
                    )
                }
                .pointerInput(viewportWidthPx) {
                    // Handled on the Initial pass so the list's own scrollable never fights the
                    // pinch (the old handler let LazyColumn scroll under two fingers too, which
                    // dragged the document towards a corner while zooming).
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var dragStarted = false
                        var prevPos = down.position
                        var prevId = down.id
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2 && zoomMode != ZoomMode.Buttons) {
                                // ── Pinch: zoom anchored at the finger centroid ──
                                val zoomChange = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                if (zoomChange != 1f || pan != Offset.Zero) {
                                    val oldZoom = zoom
                                    val newZoom = (oldZoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                                    val k = newZoom / oldZoom
                                    val v = viewportWidthPx.toFloat()
                                    // Keep the content point under the centroid fixed horizontally…
                                    val contentX = (centroid.x - v * (1f - oldZoom) / 2f - panX) / oldZoom
                                    val targetPanX =
                                        centroid.x - v * (1f - newZoom) / 2f - contentX * newZoom + pan.x
                                    val extra = v * (newZoom - 1f) / 2f
                                    panX = if (newZoom <= 1.01f) 0f else targetPanX.coerceIn(-extra, extra)
                                    // …and vertically (the first visible item's top is the layout's
                                    // stable anchor when item heights change).
                                    val firstTop =
                                        listState.layoutInfo.visibleItemsInfo.firstOrNull()?.offset ?: 0
                                    listState.dispatchRawDelta((centroid.y - firstTop) * (k - 1f) - pan.y)
                                    zoom = newZoom
                                    zoomMode = if (newZoom > 1.01f) ZoomMode.Gesture else ZoomMode.None
                                }
                                event.changes.forEach { it.consume() }
                                dragStarted = false
                            } else if (pressed.size == 1 && zoomMode == ZoomMode.Buttons && zoom > 1.01f) {
                                // ── Button mode: one-finger pan in both axes, photo-style ──
                                val change = pressed.first()
                                if (change.id != prevId) {
                                    prevId = change.id
                                    prevPos = change.position
                                }
                                val pos = change.position
                                if (!dragStarted) {
                                    if ((pos - down.position).getDistance() > viewConfiguration.touchSlop) {
                                        dragStarted = true
                                        prevPos = pos
                                        change.consume()
                                    }
                                } else {
                                    val dx = pos.x - prevPos.x
                                    val dy = pos.y - prevPos.y
                                    prevPos = pos
                                    val extra = viewportWidthPx * (zoom - 1f) / 2f
                                    panX = (panX + dx).coerceIn(-extra, extra)
                                    listState.dispatchRawDelta(-dy)
                                    change.consume()
                                }
                            }
                        }
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

        // ── Glass zoom buttons (hidden while gesture-zoomed) ──
        val zoomButtonsBottom by animateDpAsState(
            targetValue = if (chromeVisible && !searchOpen) 96.dp else 24.dp,
            label = "zoomButtonsBottom",
        )
        AnimatedVisibility(
            visible = zoomMode != ZoomMode.Gesture,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 14.dp)
                .padding(bottom = zoomButtonsBottom),
        ) {
            ZoomButtons(
                hazeState = readerHaze,
                canZoomIn = zoom < MAX_ZOOM - 0.01f,
                canZoomOut = zoom > 1.01f,
                onZoomIn = { buttonZoom(BUTTON_ZOOM_STEP) },
                onZoomOut = { buttonZoom(-BUTTON_ZOOM_STEP) },
            )
        }

        // ── Minimap-style scroll indicator (right edge) ──
        ReaderScrollBar(
            listState = listState,
            pageCount = pageCount,
            currentPage = currentPage,
            visible = chromeVisible || scrollBarLinger,
            hazeState = readerHaze,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = topInset + 72.dp, bottom = bottomInset + 210.dp),
        )
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

/** Liquid-glass zoom-in / zoom-out buttons, styled like the floating nav pill. */
@Composable
private fun ZoomButtons(
    hazeState: HazeState,
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    val style = HazeStyle(
        backgroundColor = colors.surface,
        tint = HazeTint(colors.surface.copy(alpha = 0.30f)),
        blurRadius = 20.dp,
    )
    Column(
        Modifier
            .shadow(10.dp, shape, clip = false)
            .clip(shape)
            .hazeChild(hazeState, shape = shape, style = style)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), shape),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassIconButton(Icons.Rounded.ZoomIn, "Увеличить", canZoomIn, onZoomIn)
        Box(
            Modifier
                .width(26.dp)
                .height(1.dp)
                .background(colors.outlineVariant.copy(alpha = 0.5f)),
        )
        GlassIconButton(Icons.Rounded.ZoomOut, "Уменьшить", canZoomOut, onZoomOut)
    }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = colors.onSurface.copy(alpha = if (enabled) 1f else 0.35f),
        )
    }
}
