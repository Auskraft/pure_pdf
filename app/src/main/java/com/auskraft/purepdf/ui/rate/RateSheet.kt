package com.auskraft.purepdf.ui.rate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Star rating sheet. Picking a rating calls [onPick] (the caller records it and opens the store
 * for 4–5★ or a feedback email for 1–3★). A brief delay lets the stars fill before it closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateSheet(onPick: (stars: Int) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableIntStateOf(0) }
    var acting by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surfaceContainer) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Оцените приложение", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "Оценка помогает другим найти приложение",
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { star ->
                    val filled = star <= selected
                    IconButton(
                        onClick = {
                            if (acting) return@IconButton
                            acting = true
                            selected = star
                            scope.launch {
                                delay(320)
                                onPick(star)
                            }
                        },
                    ) {
                        Icon(
                            if (filled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "$star",
                            tint = if (filled) colors.primary else colors.outline,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss) { Text("Позже") }
        }
    }
}
