package at.woergoetter.chorely.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** How every screen writes a date: the locale's medium style, "Sep 30, 2026" in English. */
@Composable
fun rememberDateFormatter(locale: Locale = Locale.getDefault()): DateTimeFormatter =
    remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }

/**
 * The day this instant fell on in the zone the device is in *now*, like every other date in
 * the app: instants are stored, local dates derived on read, with no correction for travel.
 * Not `LocalDate.ofInstant`, which Android has only from API 34.
 */
fun Instant.toLocalDateHere(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()
