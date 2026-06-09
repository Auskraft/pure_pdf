package com.auskraft.purepdf.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auskraft.purepdf.data.db.RecentDocEntity
import com.auskraft.purepdf.data.settings.Density
import com.auskraft.purepdf.data.settings.LibraryView
import com.auskraft.purepdf.ui.theme.LocalPaperColors
import com.auskraft.purepdf.ui.util.formatFileSize
import com.auskraft.purepdf.ui.util.formatOpenedDate
import kotlin.math.absoluteValue

@Composable
fun LibraryScreen(
    recents: List<RecentDocEntity>,
    view: LibraryView,
    density: Density,
    onToggleView: () -> Unit,
    onOpenDoc: (RecentDocEntity) -> Unit,
    onOpenFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize()) {
            // Large top app bar
            Row(
                Modifier.statusBarsPadding().fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Библиотека",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.2).sp,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (recents.isNotEmpty()) {
                    val gridSelected = view == LibraryView.Grid
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onToggleView),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (gridSelected) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = "Вид библиотеки",
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            if (recents.isEmpty()) {
                EmptyLibrary(Modifier.weight(1f))
            } else {
                Text(
                    "Последние",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = if (view == LibraryView.Grid) 16.dp else 16.dp,
                        end = 16.dp, top = 6.dp, bottom = 6.dp,
                    ),
                )
                when (view) {
                    LibraryView.List -> RecentList(recents, density, onOpenDoc)
                    LibraryView.Grid -> RecentGrid(recents, onOpenDoc)
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onOpenFile,
            icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
            text = { Text("Открыть файл", fontWeight = FontWeight.Medium) },
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
    }
}

@Composable
private fun RecentList(
    recents: List<RecentDocEntity>,
    density: Density,
    onOpenDoc: (RecentDocEntity) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 100.dp),
    ) {
        items(recents, key = { it.docKey }) { doc ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenDoc(doc) }
                    .padding(horizontal = 8.dp, vertical = density.rowPaddingDp.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DocThumb(color = thumbColorFor(doc.docKey), size = density.thumbDp.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        doc.name,
                        fontSize = 15.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        subtitleOf(doc),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun RecentGrid(
    recents: List<RecentDocEntity>,
    onOpenDoc: (RecentDocEntity) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(recents, key = { it.docKey }) { doc ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceContainerLow)
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .clickable { onOpenDoc(doc) }
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DocThumb(color = thumbColorFor(doc.docKey), size = 86.dp, corner = 6.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    doc.name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    formatOpenedDate(doc.lastOpened),
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)).background(colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Description,
                contentDescription = null,
                tint = colors.onSecondaryContainer,
                modifier = Modifier.size(42.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text("Здесь пока пусто", fontSize = 19.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "Откройте PDF из файлов или поделитесь документом из другого приложения — он появится в этом списке.",
            fontSize = 14.5.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(280.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun subtitleOf(doc: RecentDocEntity): String {
    val size = formatFileSize(doc.sizeBytes)
    val date = formatOpenedDate(doc.lastOpened)
    return listOf(size, date).filter { it.isNotEmpty() }.joinToString(" · ")
}

// ── Thumbnail ────────────────────────────────────────────────────────────────

private val ThumbPalette = listOf(
    Color(0xFFC2410C), Color(0xFF0F766E), Color(0xFF1A73E8), Color(0xFF7C3AED), Color(0xFFB45309),
)

private fun thumbColorFor(key: String): Color =
    ThumbPalette[(key.hashCode().absoluteValue) % ThumbPalette.size]

/** Stylised "page" placeholder: colored title bar + grey text lines, matching the design. */
@Composable
fun DocThumb(color: Color, size: Dp, corner: Dp = 8.dp, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .size(width = size, height = size * 1.3f)
            .shadow(2.dp, RoundedCornerShape(corner))
            .clip(RoundedCornerShape(corner))
            .background(LocalPaperColors.current.paper)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(corner))
            .padding(size * 0.14f),
        verticalArrangement = Arrangement.spacedBy(size * 0.07f),
    ) {
        Box(
            Modifier.fillMaxWidth(0.62f).height(size * 0.14f)
                .clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.85f)),
        )
        listOf(0.88f, 1f, 0.94f, 0.70f).forEach { frac ->
            Box(
                Modifier.fillMaxWidth(frac).height(size * 0.07f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.onSurfaceVariant.copy(alpha = 0.28f)),
            )
        }
    }
}
