package at.woergoetter.chorely.data

import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

/**
 * The clock the whole app is handed — through [DataModule], so that going back to
 * `Clock.systemDefaultZone()` there fails here — follows a timezone change made after it was
 * created.
 */
class DeviceClockTest {

    private val original: TimeZone = TimeZone.getDefault()

    @After
    fun restore() = TimeZone.setDefault(original)

    @Test
    fun `today follows the change`() {
        // UTC+14 and UTC-11 without daylight saving: 25 hours apart, so at every instant the
        // two are on different calendar days, and a clock stuck in the first gets today wrong.
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
        val clock = DataModule.clock()
        val inKiritimati = LocalDate.now(clock)

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Pago_Pago"))

        assertNotEquals(inKiritimati, LocalDate.now(clock))
    }
}
