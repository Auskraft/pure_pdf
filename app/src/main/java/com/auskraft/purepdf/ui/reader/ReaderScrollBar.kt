package com.auskraft.purepdf.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Don't bother with an indicator for documents this short. */
private const val MIN_PAGES_FOR_BAR = 4

/**
 * Minimap-style scroll indicator: a thin thumb at the right edge whose position and size mirror
 * the viewport within the document (the mobile take on VS Code's minimap). Dragging it
 * fast-scrolls; while dragging a glass bubble shows the current page.
 */
@Composable
fun ReaderScrollBar(
    listState: LazyListState,
    pageCount: Int,
    currentPage: Int,
    visible: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    if (pageCount < MIN_PAGES_FOR_BAR) return
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current

    AnimatedVisibility(
        visible = visible || dragging,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // Wider than the touch strip so the drag bubble has room; only the strip takes input.
        BoxWithConstraints(Modifier.fillMaxHeight().width(180.dp)) {
            val trackHeightPx = with(density) { maxHeight.toPx() }
            val minThumbPx = with(density) { 32.dp.toPx() }

            // topFraction = how far the viewport top is through the content (0..1),
            // coverage = what share of the content the viewport shows.
            val metrics by remember(pageCount) {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val items = info.visibleItemsInfo
                    if (items.isEmpty()) return@derivedStateOf null
                    val first = items.first()
                    if (first.size <= 0) return@derivedStateOf null
                    val viewportH = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                    val avgItem = items.sumOf { it.size }.toFloat() / items.size
                    val coverage = (viewportH / (avgItem * pageCount)).coerceIn(0.02f, 1f)
                    val scrolledInFirst =
                        (info.viewportStartOffset - first.offset).coerceAtLeast(0) / first.size.toFloat()
                    val topFraction = ((first.index + scrolledInFirst) / pageCount).coerceIn(0f, 1f)
                    Triple(topFraction, coverage, avgItem)
                }
            }

            val m = metrics
            if (m != null && m.second < 0.999f) {
                val (topFraction, coverage, avgItem) = m
                val thumbHeightPx = (trackHeightPx * coverage).coerceAtLeast(minThumbPx)
                val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
                val thumbY = travelPx * (topFraction / (1f - coverage)).coerceIn(0f, 1f)

                fun jumpTo(yPx: Float) {
                    val t = ((yPx - thumbHeightPx / 2f) / travelPx).coerceIn(0f, 1f)
                    val absoluteItem = t * (1f - coverage) * pageCount
                    val index = absoluteItem.toInt().coerceIn(0, pageCount - 1)
                    val offsetPx = ((absoluteItem - index) * avgItem).roundToInt()
                    scope.launch { listState.scrollToItem(index, offsetPx) }
                }

                // Touch strip: drag to fast-scroll, tap to jump.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxHeight()
                        .width(36.dp)
                        .pointerInput(pageCount) {
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    dragging = true
                                    jumpTo(offset.y)
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                                onVerticalDrag = { change, _ ->
                                    change.consume()
                                    jumpTo(change.position.y)
                                },
                            )
                        }
                        .pointerInput(pageCount) {
                            detectTapGestures { offset -> jumpTo(offset.y) }
                        },
                )

                // Thumb.
                val thumbWidth by animateDpAsState(if (dragging) 8.dp else 5.dp, label = "thumbWidth")
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, thumbY.roundToInt()) }
                        .padding(end = 4.dp)
                        .width(thumbWidth)
                        .height(with(density) { thumbHeightPx.toDp() })
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (dragging) colors.primary
                            else colors.onSurfaceVariant.copy(alpha = 0.45f),
                        ),
                )

                // Glass page bubble while dragging.
                if (dragging) {
                    val bubbleShape = RoundedCornerShape(14.dp)
                    val bubbleStyle = HazeStyle(
                        backgroundColor = colors.surface,
                        tint = HazeTint(colors.surface.copy(alpha = 0.35f)),
                        blurRadius = 20.dp,
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset {
                                IntOffset(
                                    -with(density) { 24.dp.roundToPx() },
                                    (thumbY + thumbHeightPx / 2f).roundToInt() -
                                        with(density) { 14.dp.roundToPx() },
                                )
                            }
                            .clip(bubbleShape)
                            .hazeChild(hazeState, shape = bubbleShape, style = bubbleStyle)
                            .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), bubbleShape)
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                    ) {
                        Text(
                            "$currentPage / $pageCount",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}
