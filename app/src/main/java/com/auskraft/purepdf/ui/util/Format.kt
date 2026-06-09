package com.auskraft.purepdf.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val RU = Locale("ru")

/** "380 КБ", "4,8 МБ" — matching the design. */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.roundToInt()} КБ"
    return String.format(RU, "%.1f МБ", kb / 1024.0)
}

/** "Сегодня, 14:32" / "Вчера, 21:40" / "3 июня" / "3 июн. 2025". */
fun formatOpenedDate(millis: Long): String {
    if (millis <= 0) return ""
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(millis).atZone(zone)
    val date = dateTime.toLocalDate()
    val today = LocalDate.now(zone)
    val time = String.format(Locale.ROOT, "%02d:%02d", dateTime.hour, dateTime.minute)
    return when {
        date == today -> "Сегодня, $time"
        date == today.minusDays(1) -> "Вчера, $time"
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMMM", RU))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", RU))
    }
}
