package com.scanforge.app.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

private val DATE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.getDefault())

/** Human-readable byte size, e.g. `0 B`, `932 KB`, `4.1 MB`. */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) {
        "${bytes} B"
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
    }
}

fun Instant.formatDate(): String = DATE_FORMAT.format(atZone(ZoneId.systemDefault()))

fun Instant.formatDateTime(): String = DATE_TIME_FORMAT.format(atZone(ZoneId.systemDefault()))
