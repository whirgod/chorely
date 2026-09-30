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
internal fun nextDigestDelay(reminderTime: LocalTime, deviceClock: Clock): Duration {
    // One zone for the whole computation: the device's clock follows a zone change, and one
    // landing between the reads below would put the time on one zone's date and convert it in
    // another's.
    val clock = deviceClock.withZone(deviceClock.zone)
    val now = clock.instant()
    val today = LocalDate.now(clock).atTime(reminderTime).atZone(clock.zone).toInstant()
    // Strictly after: firing "now" for a time that has just passed would post today's digest
    // twice on the day the user moves the reminder earlier.
    val next = if (today > now) today else LocalDate.now(clock).plusDays(1)
        .atTime(reminderTime).atZone(clock.zone).toInstant()
    return Duration.between(now, next)
}

/**
 * Whether today's digest time has already come, in the zone the device is in now — so that a
 * digest still pending is owed today rather than waiting for a time still ahead.
 *
 * The line between "run the pending digest now" and "re-aim it": moving the reminder later
 * in the day, or arriving somewhere it is not that time yet, re-aims to the time still ahead
 * — one digest today, not an early one and then the real one.
 */
internal fun isPastTodaysDigest(reminderTime: LocalTime, deviceClock: Clock): Boolean {
    val clock = deviceClock.withZone(deviceClock.zone)
    val today = LocalDate.now(clock).atTime(reminderTime).atZone(clock.zone).toInstant()
    return !today.isAfter(clock.instant())
}
