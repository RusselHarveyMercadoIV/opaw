package app.opaw.services

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The phone's 12- or 24-hour setting, read at launch. */
object ClockSettings {
    @Volatile var is24Hour = true
}

/** "13:05", or "1:05 PM" on a phone set to 12-hour time. The counterpart of `.formatted(date: .omitted, time: .shortened)`. */
fun clock(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.atZone(zone).format(DateTimeFormatter.ofPattern(if (ClockSettings.is24Hour) "HH:mm" else "h:mm a", Locale.getDefault()))

/** The time column in log lists widens for "10:30 PM" so every time stays on one line. */
fun clockColumnWidth(base: Int): Int = if (ClockSettings.is24Hour) base else base + 22

fun formatDate(date: LocalDate, pattern: String): String = date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

fun formatInstant(instant: Instant, pattern: String, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.atZone(zone).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
