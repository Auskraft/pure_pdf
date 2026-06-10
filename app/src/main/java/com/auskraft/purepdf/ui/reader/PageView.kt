package com.auskraft.purepdf.ui.reader

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.auskraft.purepdf.pdf.SearchMatch
import com.auskraft.purepdf.ui.theme.LocalPaperColors
import kotlin.math.roundToInt

/**
 * Night-mode "smart invert": white paper -> dark grey (#191919-ish), black ink -> light grey.
 * out = -0.78*in + 224 per channel, so neither paper nor ink is pure black/white.
 */
private val NightColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.78f, 0f, 0f, 0f, 224f,
            0f, -0.78f, 0f, 0f, 224f,
            0f, 0f, -0.78f, 0f, 224f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

@Composable
fun PageView(
    index: Int,
    pageNumber: Int,
    total: Int,
    viewportWidthPx: Int,
    zoom: Float,
    renderScale: Float,
    panX: Float,
    darkMode: Boolean,
    bookmarked: Boolean,
    activeMatch: SearchMatch?,
    renderPage: suspend (index: Int, widthPx: Int) -> Bitmap?,
    pageAspectRatio: suspend (index: Int) -> Float,
    highlightRects: suspend (index: Int, start: Int, len: Int, widthPx: Int) -> List<RectF>,
) {
    val density = LocalDensity.current
    val paper = LocalPaperColors.current
    val highlightColor = MaterialTheme.colorScheme.primary

    var aspect by remember(index) { mutableStateOf(1.3f) }
    LaunchedEffect(index) { aspect = pageAspectRatio(index) }

    val renderWidthPx = (viewportWidthPx * renderScale).roundToInt().coerceAtLeast(1)
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(index, renderWidthPx) { bitmap = renderPage(index, renderWidthPx) }

    var rects by remember(index) { mutableStateOf<List<RectF>>(emptyList()) }
    LaunchedEffect(index, activeMatch, renderWidthPx) {
        rects = if (activeMatch != null && activeMatch.page == index) {
            highlightRects(index, activeMatch.charStart, activeMatch.charLen, renderWidthPx)
        } else {
            emptyList()
        }
    }

    val displayWidthPx = viewportWidthPx * zoom
    val displayHeightPx = displayWidthPx * aspect

    Box(
        Modifier
            .width(with(density) { viewportWidthPx.toDp() })
            .height(with(density) { displayHeightPx.toDp() })
            .clipToBounds(),
    ) {
        Box(
            Modifier
                // requiredWidth so the zoomed page can exceed the viewport (clip window) width —
                // a plain .width() would be clamped to the parent, leaving the page un-magnified.
                // An oversized child is auto-centred by the layout, which already contributes the
                // (viewport - display) / 2 base offset — add only the user pan on top of it.
                .requiredWidth(with(density) { displayWidthPx.toDp() })
                .height(with(density) { displayHeightPx.toDp() })
                .offset { IntOffset(panX.roundToInt(), 0) }
                .shadow(2.dp, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(paper.paper),
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    colorFilter = if (darkMode) NightColorFilter else null,
                )
                if (rects.isNotEmpty()) {
                    Canvas(Modifier.fillMaxSize()) {
                        val scale = size.width / renderWidthPx.toFloat()
                        rects.forEach { r ->
                            drawRect(
                                color = highlightColor.copy(alpha = 0.35f),
                                topLeft = Offset(r.left * scale, r.top * scale),
                                size = Size((r.right - r.left) * scale, (r.bottom - r.top) * scale),
                            )
                        }
                    }
                }
            }

            Text(
                text = "$pageNumber / $total",
                fontSize = (9.5f * zoom.coerceAtMost(1.4f)).sp,
                color = paper.muted,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            )

            if (bookmarked) {
                BookmarkRibbon(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 22.dp),
                )
            }
        }
    }
}

@Composable
private fun BookmarkRibbon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.width(18.dp).height(26.dp)) {
        val w = size.width
        val h = size.height
        val notch = h * 0.25f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h)
            lineTo(w / 2f, h - notch)
            lineTo(0f, h)
            close()
        }
        drawPath(path, color)
    }
}
