package at.woergoetter.chorely.reminder

import java.time.Clock
import java.time.Duration
import java.time.Instant
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
 * Whether a digest scheduled for [scheduledAt], and not yet run, is owed — to be run at once
 * on the next sync — rather than re-aimed at the next reminder time.
 *
 * Owed once its time has passed, with one exception: a digest scheduled for today, when today's
 * [reminderTime] is still ahead in the zone the device is in now. That is the reminder moved
 * later in the day, or a zone where it is not that time yet, and re-aiming is then the one
 * digest today instead of an early one and then the real one. A digest from an earlier day —
 * held by Doze past midnight, or pending through a night the phone was off — is owed: that
 * day's digest is still to be posted, late, and today's follows at its time.
 */
internal fun isOwedDigest(scheduledAt: Instant, reminderTime: LocalTime, deviceClock: Clock): Boolean {
    val clock = deviceClock.withZone(deviceClock.zone)
    val now = clock.instant()
    if (scheduledAt.isAfter(now)) return false
    val today = LocalDate.now(clock)
    val todaysTime = today.atTime(reminderTime).atZone(clock.zone).toInstant()
    val forToday = scheduledAt.atZone(clock.zone).toLocalDate() == today
    return !(forToday && todaysTime.isAfter(now))
}
