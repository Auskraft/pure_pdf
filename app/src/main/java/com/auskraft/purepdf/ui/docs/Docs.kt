package com.auskraft.purepdf.ui.docs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class DocItem(val title: String, val body: String) {
    Privacy("Политика конфиденциальности", PRIVACY_BODY),
    Terms("Условия использования", TERMS_BODY),
    Licenses("Открытый код и лицензии", LICENSES_BODY),
}

/** Full-screen documentation: a list of documents that opens each in a reader. */
@Composable
fun DocsScreen(onClose: () -> Unit) {
    var selected by remember { mutableStateOf<DocItem?>(null) }
    BackHandler { if (selected != null) selected = null else onClose() }
    when (val item = selected) {
        null -> DocsList(onOpen = { selected = it }, onBack = onClose)
        else -> DocViewer(item = item, onBack = { selected = null })
    }
}

@Composable
private fun DocsList(onOpen: (DocItem) -> Unit, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.surface)) {
        DocTopBar(title = "Документация", onBack = onBack)
        Column(
            Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceContainerLow)
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
        ) {
            DocItem.entries.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.title, fontSize = 15.5.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.outline)
                }
            }
        }
        Text(
            "Pure PDF · версия 1.0",
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun DocViewer(item: DocItem, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.surface)) {
        DocTopBar(title = item.title, onBack = onBack)
        Text(
            item.body,
            fontSize = 14.5.sp,
            lineHeight = 22.sp,
            color = colors.onSurface,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun DocTopBar(title: String, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = colors.onSurface) }
        Text(
            title,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
