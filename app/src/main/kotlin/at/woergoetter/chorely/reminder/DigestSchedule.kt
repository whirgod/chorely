package at.woergoetter.chorely.reminder

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * How long until the next daily digest should fire, given the chosen local time.
 *
 * Extracted from the scheduler because this is the only part of reminder scheduling that
 * can be *wrong* rather than merely broken: today-versus-tomorrow at the boundary, and the
 * days on which a local time happens twice or not at all. Everything around it is a call
 * into WorkManager, which a unit test would only be able to watch itself make.
 */
internal fun nextDigestDelay(reminderTime: LocalTime, clock: Clock): Duration {
    val now = clock.instant()
    val today = LocalDate.now(clock).atTime(reminderTime).atZone(clock.zone).toInstant()
    // Strictly after: firing "now" for a time that has just passed would post today's digest
    // twice on the day the user moves the reminder earlier.
    val next = if (today > now) today else LocalDate.now(clock).plusDays(1)
        .atTime(reminderTime).atZone(clock.zone).toInstant()
    return Duration.between(now, next)
}
