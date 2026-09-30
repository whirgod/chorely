package at.woergoetter.chorely.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The system clock, in whatever zone the device is in at the moment it is asked.
 *
 * `Clock.systemDefaultZone()` reads the default zone once, when it is created, and the app
 * holds its clock as a singleton — so after the user changes timezone with the process alive,
 * the domain went on deriving due dates in the old zone while the screens, which read the
 * zone afresh, used the new one. Android resets the process's default `TimeZone` when the
 * system zone changes, so reading it on every call is all it takes to follow.
 */
object DeviceClock : Clock() {

    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun instant(): Instant = Instant.now()

    override fun millis(): Long = System.currentTimeMillis()

    /** A clock fixed to [zone], which by asking for one the caller no longer wants to follow. */
    override fun withZone(zone: ZoneId): Clock = system(zone)
}
