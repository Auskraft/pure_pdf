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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.auskraft.purepdf.data.db.BookmarkEntity

@Composable
fun PageJumpDialog(
    total: Int,
    current: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 1..total
    val go = { if (valid) onJump(parsed!!) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh) {
            Column(Modifier.width(280.dp).padding(24.dp)) {
                Text("Перейти к странице", fontSize = 19.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text("Сейчас $current из $total", fontSize = 13.5.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter(Char::isDigit).take(7) },
                    singleLine = true,
                    isError = text.isNotEmpty() && !valid,
                    placeholder = { Text("1–$total") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = go, enabled = valid) { Text("Перейти") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    bookmarks: List<BookmarkEntity>,
    currentPage: Int,
    isCurrentBookmarked: Boolean,
    onToggleCurrent: () -> Unit,
    onJump: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text("Закладки", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.primaryContainer)
                    .clickable(onClick = onToggleCurrent)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isCurrentBookmarked) Icons.Rounded.Delete else Icons.Rounded.Add,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (isCurrentBookmarked) "Убрать закладку со стр. $currentPage" else "Добавить закладку — стр. $currentPage",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onPrimaryContainer,
                )
            }

            Spacer(Modifier.height(10.dp))

            if (bookmarks.isEmpty()) {
                Text(
                    "Пока нет закладок",
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                )
            } else {
                bookmarks.forEach { bookmark ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onJump(bookmark.page) }
                                .padding(horizontal = 10.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = colors.primary)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    bookmark.label,
                                    fontSize = 15.sp,
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("Страница ${bookmark.page}", fontSize = 12.5.sp, color = colors.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onDelete(bookmark.page) }) {
                            Icon(Icons.Rounded.Close, "Удалить", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
