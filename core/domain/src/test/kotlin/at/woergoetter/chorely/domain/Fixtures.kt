package at.woergoetter.chorely.domain

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId

val Vienna: ZoneId = ZoneId.of("Europe/Vienna")

/** A clock fixed to noon on [date], so tests never straddle a local midnight by accident. */
fun clockAt(date: LocalDate, zone: ZoneId = Vienna): Clock =
    Clock.fixed(date.atTime(LocalTime.NOON).atZone(zone).toInstant(), zone)

fun date(text: String): LocalDate = LocalDate.parse(text)

fun weekly(vararg days: DayOfWeek): Recurrence.OnWeekdays = Recurrence.OnWeekdays(days.toSet())

fun everyMonths(n: Int): Recurrence.Every = Recurrence.Every(Period.ofMonths(n))

fun everyDays(n: Int): Recurrence.Every = Recurrence.Every(Period.ofDays(n))

fun chore(
    recurrence: Recurrence,
    anchoredOn: String,
    name: String = "Vacuum",
    id: Long = 1,
): Chore = Chore(ChoreId(id), name, recurrence, date(anchoredOn))

fun completedOn(dueDate: String, at: String = dueDate, zone: ZoneId = Vienna): Resolution.Completion =
    Resolution.Completion(date(dueDate), date(at).atTime(LocalTime.NOON).atZone(zone).toInstant())

fun skippedOn(dueDate: String, at: String = dueDate, zone: ZoneId = Vienna): Resolution.Skip =
    Resolution.Skip(date(dueDate), date(at).atTime(LocalTime.NOON).atZone(zone).toInstant(), Resolution.Skip.Kind.Manual)
