package at.woergoetter.chorely.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.woergoetter.chorely.domain.ChoreStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * The reminder time and `seenThrough` share the one `app_state` row, and each writer
 * rewrites all of it. What is pinned here is that neither writer carries the other's
 * column back to an older value — the settings screen and the digest worker write that
 * row independently.
 */
@RunWith(AndroidJUnit4::class)
class RoomReminderSettingsTest {

    private lateinit var database: ChorelyDatabase
    private lateinit var settings: RoomReminderSettings
    private lateinit var store: ChoreStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChorelyDatabase::class.java,
        ).build()
        settings = RoomReminderSettings(database)
        store = RoomChoreStore(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun theReminderTimeSurvivesARoundTrip() = runTest {
        settings.setReminderTime(LocalTime.of(19, 30))

        assertEquals(LocalTime.of(19, 30), settings.reminderTime().first())
    }

    @Test
    fun switchingRemindersOffClearsTheTime() = runTest {
        settings.setReminderTime(LocalTime.of(19, 0))

        settings.setReminderTime(null)

        assertNull(settings.reminderTime().first())
    }

    @Test
    fun settingTheReminderTimeLeavesSeenThroughAlone() = runTest {
        store.transact { it.markSeen(LocalDate.parse("2026-09-16")) }

        settings.setReminderTime(LocalTime.of(19, 0))

        assertEquals(LocalDate.parse("2026-09-16"), store.book().first().seenThrough)
    }

    @Test
    fun markingSeenLeavesTheReminderTimeAlone() = runTest {
        settings.setReminderTime(LocalTime.of(19, 0))

        // Also the only place the app_state patch runs nested inside an outer transaction.
        store.transact { it.markSeen(LocalDate.parse("2026-09-16")) }

        assertEquals(LocalTime.of(19, 0), settings.reminderTime().first())
    }
}
