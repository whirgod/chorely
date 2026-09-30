package at.woergoetter.chorely.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * The clock the whole app is handed follows a timezone change made after it was created,
 * which `Clock.systemDefaultZone()` does not.
 */
class DeviceClockTest {

    private val original: TimeZone = TimeZone.getDefault()

    @After
    fun restore() = TimeZone.setDefault(original)

    @Test
    fun `the zone is the device's current one, not the one it had when the clock was made`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Vienna"))
        val clock = DeviceClock

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))

        assertEquals(ZoneId.of("Pacific/Auckland"), clock.zone)
    }

    @Test
    fun `today follows the change`() {
        // UTC+14 and UTC-11 without daylight saving: 25 hours apart, so at every instant the
        // two are on different calendar days, and a clock stuck in the first gets today wrong.
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
        val clock = DeviceClock
        val inKiritimati = LocalDate.now(clock)

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Pago_Pago"))

        assertNotEquals(inKiritimati, LocalDate.now(clock))
    }
}
