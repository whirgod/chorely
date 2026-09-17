package at.woergoetter.chorely.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.woergoetter.chorely.domain.ChoreDraft
import at.woergoetter.chorely.domain.ChoreStore
import at.woergoetter.chorely.domain.Chores
import at.woergoetter.chorely.domain.Recurrence
import at.woergoetter.chorely.domain.Resolution
import at.woergoetter.chorely.domain.StoredChores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId

/**
 * The same seam as `FakeChoreStore`, against the real adapter.
 *
 * These tests are not a second copy of the domain's behaviour tests — those already pass
 * through the fake. What is checked here is only what Room can get wrong that a list cannot:
 * the latest-resolution query, transaction rollback, the cascade on delete, and that a
 * recurrence survives a round trip through columns.
 */
@RunWith(AndroidJUnit4::class)
class RoomChoreStoreTest {

    private val vienna: ZoneId = ZoneId.of("Europe/Vienna")
    private lateinit var database: ChorelyDatabase
    private lateinit var store: ChoreStore

    private fun clockAt(date: String): Clock =
        Clock.fixed(LocalDate.parse(date).atTime(LocalTime.NOON).atZone(vienna).toInstant(), vienna)

    private fun chores(date: String): Chores = StoredChores(store, clockAt(date))

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChorelyDatabase::class.java,
        ).build()
        store = RoomChoreStore(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun weekdayRecurrenceSurvivesARoundTrip() = runTest {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        chores("2026-09-16").add(ChoreDraft("Bins", Recurrence.OnWeekdays(days)))

        val stored = store.book().first().chores.single().chore

        assertEquals(Recurrence.OnWeekdays(days), stored.recurrence)
    }

    @Test
    fun periodRecurrenceSurvivesARoundTrip() = runTest {
        chores("2026-09-16").add(ChoreDraft("Kettle", Recurrence.Every(Period.ofMonths(3))))

        val stored = store.book().first().chores.single().chore

        assertEquals(Recurrence.Every(Period.ofMonths(3)), stored.recurrence)
    }

    @Test
    fun theLatestResolutionIsTheOneWithTheNewestDueDate() = runTest {
        val id = chores("2026-09-16").add(ChoreDraft("Vacuum", Recurrence.OnWeekdays(setOf(DayOfWeek.SATURDAY))))
        store.transact { edit ->
            edit.append(
                id,
                listOf(
                    Resolution.Completion(LocalDate.parse("2026-09-19"), clockAt("2026-09-19").instant()),
                    // Entered later, but for an older occurrence: it must not win.
                    Resolution.Completion(LocalDate.parse("2026-09-12"), clockAt("2026-09-26").instant()),
                ),
            )
        }

        val latest = store.book().first().chores.single().lastResolution

        assertEquals(LocalDate.parse("2026-09-19"), latest?.dueDate)
    }

    @Test
    fun aFailedTransactionWritesNothing() = runTest {
        val id = chores("2026-09-16").add(ChoreDraft("Vacuum", Recurrence.OnWeekdays(setOf(DayOfWeek.SATURDAY))))

        runCatching {
            store.transact { edit ->
                edit.append(id, listOf(Resolution.Completion(LocalDate.parse("2026-09-19"), clockAt("2026-09-19").instant())))
                error("rolled back")
            }
        }

        assertTrue(store.history(id).first().isEmpty())
    }

    @Test
    fun deletingAChoreTakesItsHistoryWithIt() = runTest {
        val chores = chores("2026-09-19")
        val id = chores.add(ChoreDraft("Vacuum", Recurrence.OnWeekdays(setOf(DayOfWeek.SATURDAY))))
        chores.complete(id)

        chores.delete(id)

        assertTrue(store.history(id).first().isEmpty())
        assertNull(store.book().first().chores.firstOrNull { it.chore.id == id })
    }

    @Test
    fun archivingAChoreRetainsItsHistory() = runTest {
        val chores = chores("2026-09-19")
        val id = chores.add(ChoreDraft("Vacuum", Recurrence.OnWeekdays(setOf(DayOfWeek.SATURDAY))))
        chores.complete(id)

        chores.archive(id)

        assertEquals(1, store.history(id).first().size)
    }

    @Test
    fun seenThroughSurvivesBeingSetTwice() = runTest {
        chores("2026-09-16").markSeen()
        chores("2026-09-19").markSeen()

        assertEquals(LocalDate.parse("2026-09-19"), store.book().first().seenThrough)
    }
}
