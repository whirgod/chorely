package at.woergoetter.chorely.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class DigestScheduleTest {

    private val vienna = ZoneId.of("Europe/Vienna")

    private fun clockAt(dateTime: String, zone: ZoneId = vienna): Clock =
        Clock.fixed(LocalDateTime.parse(dateTime).atZone(zone).toInstant(), zone)

    @Test
    fun `a time still ahead today fires today`() {
        val delay = nextDigestDelay(LocalTime.of(19, 0), clockAt("2026-09-16T08:00"))

        assertEquals(Duration.ofHours(11), delay)
    }

    @Test
    fun `a time already past today fires tomorrow`() {
        val delay = nextDigestDelay(LocalTime.of(19, 0), clockAt("2026-09-16T20:00"))

        assertEquals(Duration.ofHours(23), delay)
    }

    @Test
    fun `the reminder time exactly now waits a full day rather than firing twice`() {
        val delay = nextDigestDelay(LocalTime.of(19, 0), clockAt("2026-09-16T19:00"))

        assertEquals(Duration.ofDays(1), delay)
    }

    @Test
    fun `the spring-forward day is an hour short, not an hour wrong`() {
        // Europe/Vienna jumps 02:00 to 03:00 on 2026-03-29.
        val delay = nextDigestDelay(LocalTime.of(9, 0), clockAt("2026-03-28T09:00"))

        assertEquals(Duration.ofHours(23), delay)
    }

    @Test
    fun `the autumn-back day is an hour long`() {
        // Europe/Vienna repeats 02:00-03:00 on 2026-10-25.
        val delay = nextDigestDelay(LocalTime.of(9, 0), clockAt("2026-10-24T09:00"))

        assertEquals(Duration.ofHours(25), delay)
    }

    @Test
    fun `a reminder time that does not exist on a spring-forward day is pushed past the gap`() {
        // 02:30 never happens on 2026-03-29 in Vienna; java.time moves it to 03:30. Only
        // 2h30m of real time passes, because the clock skips 02:00 to 03:00 on the way.
        val delay = nextDigestDelay(LocalTime.of(2, 30), clockAt("2026-03-29T00:00"))

        assertEquals(Duration.ofMinutes(150), delay)
        val firesAt = clockAt("2026-03-29T00:00").instant().plus(delay).atZone(vienna)
        assertEquals(LocalDate.parse("2026-03-29"), firesAt.toLocalDate())
        assertEquals(LocalTime.of(3, 30), firesAt.toLocalTime())
    }

    @Test
    fun `today's digest time has passed once it is reached`() {
        assertEquals(false, isPastTodaysDigest(LocalTime.of(19, 0), clockAt("2026-09-16T18:59")))
        assertEquals(true, isPastTodaysDigest(LocalTime.of(19, 0), clockAt("2026-09-16T19:00")))
        assertEquals(true, isPastTodaysDigest(LocalTime.of(19, 0), clockAt("2026-09-16T23:59")))
    }

    @Test
    fun `today's digest time is read in the zone the clock is in`() {
        // 16:00 in Vienna is 10:00 in New York: an 08:00 reminder has passed there, 18:00 not.
        val newYork = clockAt("2026-09-16T10:00", ZoneId.of("America/New_York"))

        assertEquals(true, isPastTodaysDigest(LocalTime.of(8, 0), newYork))
        assertEquals(false, isPastTodaysDigest(LocalTime.of(18, 0), newYork))
    }
}
