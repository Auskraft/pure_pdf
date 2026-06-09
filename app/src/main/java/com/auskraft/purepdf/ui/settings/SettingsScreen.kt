package com.auskraft.purepdf.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auskraft.purepdf.data.settings.AccentPreset
import com.auskraft.purepdf.data.settings.AppSettings
import com.auskraft.purepdf.data.settings.Density
import com.auskraft.purepdf.data.settings.LibraryView

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onToggleDark: (Boolean) -> Unit,
    onToggleKeep: (Boolean) -> Unit,
    onAccent: (AccentPreset) -> Unit,
    onView: (LibraryView) -> Unit,
    onDensity: (Density) -> Unit,
    onRate: () -> Unit,
    onOpenDocs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Настройки",
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).sp,
            color = colors.onSurface,
            modifier = Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            // ── Чтение ──
            SectionLabel("Чтение")
            SettingsCard {
                SettingRow(
                    icon = if (settings.darkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                    title = "Ночной режим",
                    subtitle = "Тёмная схема для чтения в темноте",
                ) { Switch(checked = settings.darkMode, onCheckedChange = onToggleDark) }
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Restore,
                    title = "Запоминать позицию",
                    subtitle = "Страница и масштаб для каждого файла",
                ) { Switch(checked = settings.keepPosition, onCheckedChange = onToggleKeep) }
            }

            // ── Внешний вид ──
            SectionLabel("Внешний вид")
            SettingsCard {
                SettingBlock(icon = Icons.Rounded.Palette, title = "Акцентный цвет") {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AccentPreset.entries.forEach { preset ->
                            AccentSwatch(
                                color = Color(preset.seedArgb),
                                selected = settings.accent == preset,
                                onClick = { onAccent(preset) },
                            )
                        }
                    }
                }
                InsetDivider()
                SettingBlock(icon = Icons.Rounded.ViewAgenda, title = "Вид библиотеки") {
                    SingleChoiceSegmentedButtonRow {
                        val views = listOf(LibraryView.List to "Список", LibraryView.Grid to "Сетка")
                        views.forEachIndexed { i, (value, label) ->
                            SegmentedButton(
                                selected = settings.libraryView == value,
                                onClick = { onView(value) },
                                shape = SegmentedButtonDefaults.itemShape(i, views.size),
                                icon = {},
                            ) { Text(label) }
                        }
                    }
                }
                InsetDivider()
                SettingBlock(icon = Icons.Rounded.ViewAgenda, title = "Плотность") {
                    SingleChoiceSegmentedButtonRow {
                        Density.entries.forEachIndexed { i, value ->
                            SegmentedButton(
                                selected = settings.density == value,
                                onClick = { onDensity(value) },
                                shape = SegmentedButtonDefaults.itemShape(i, Density.entries.size),
                                icon = {},
                            ) { Text(value.label) }
                        }
                    }
                }
            }

            // ── Приложение (rating + documentation) ──
            SectionLabel("Приложение")
            SettingsCard {
                SettingRow(
                    icon = Icons.Rounded.StarRate,
                    title = "Оценить приложение",
                    subtitle = "Поддержать разработку — это бесплатно",
                    onClick = onRate,
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Description,
                    title = "Документация",
                    subtitle = "Конфиденциальность, условия, лицензии",
                    onClick = onOpenDocs,
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.outline) },
                )
            }

            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
    ) { content() }
}

@Composable
private fun InsetDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, color = colors.onSurface)
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(subtitle, fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun SettingBlock(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(title, fontSize = 15.5.sp, color = colors.onSurface)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun AccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .then(
                if (selected) {
                    Modifier.border(2.5.dp, colors.onSurface, CircleShape).padding(4.dp)
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
    )
}
