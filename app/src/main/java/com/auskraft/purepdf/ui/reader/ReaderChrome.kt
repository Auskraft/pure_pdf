package com.auskraft.purepdf.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun ReaderTopBar(
    title: String,
    isBookmarked: Boolean,
    darkMode: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onBookmarks: () -> Unit,
    onToggleDark: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer) {
        Column {
            Row(
                Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = colors.onSurface) }
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                IconButton(onSearch) { Icon(Icons.Rounded.Search, "Поиск", tint = colors.onSurface) }
                IconButton(onBookmarks) {
                    Icon(
                        if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        "Закладки",
                        tint = if (isBookmarked) colors.primary else colors.onSurface,
                    )
                }
                IconButton(onToggleDark) {
                    Icon(
                        if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        "Тема",
                        tint = colors.onSurface,
                    )
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
        }
    }
}

@Composable
fun ReaderSearchBar(
    query: String,
    resultCount: Int,
    activeIndex: Int,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(color = colors.surfaceContainer) {
        Column {
            Row(
                Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Закрыть поиск", tint = colors.onSurface) }
                Row(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(colors.surfaceContainerHighest)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = colors.onSurface, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onNext() }),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("Поиск в документе", color = colors.onSurfaceVariant, fontSize = 15.sp)
                            }
                            inner()
                        },
                    )
                    if (query.isNotEmpty()) {
                        Text(
                            if (resultCount > 0) "${activeIndex + 1}/$resultCount" else "нет",
                            fontSize = 12.5.sp,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, "Очистить", tint = colors.onSurfaceVariant, modifier = Modifier.size(17.dp))
                        }
                    }
                }
                IconButton(onPrev) { Icon(Icons.Rounded.KeyboardArrowUp, "Предыдущее", tint = colors.onSurface) }
                IconButton(onNext) { Icon(Icons.Rounded.KeyboardArrowDown, "Следующее", tint = colors.onSurface) }
            }
            HorizontalDivider(color = colors.outlineVariant)
        }
    }
}

@Composable
fun ReaderBottomBar(
    currentPage: Int,
    pageCount: Int,
    onSeek: (Int) -> Unit,
    onOpenJump: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer) {
        Column {
            HorizontalDivider(color = colors.outlineVariant)
            Row(
                Modifier
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Slider(
                    value = currentPage.toFloat().coerceIn(1f, pageCount.toFloat()),
                    onValueChange = { onSeek(it.roundToInt().coerceIn(1, pageCount)) },
                    valueRange = 1f..pageCount.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainerHighest)
                        .clickable(onClick = onOpenJump)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        "$currentPage / $pageCount",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                }
            }
        }
    }
}
