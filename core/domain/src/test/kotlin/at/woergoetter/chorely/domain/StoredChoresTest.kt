package at.woergoetter.chorely.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/**
 * Tests at the [Chores] interface — the surface the app actually uses. Nothing here knows
 * that catch-up exists; that is the whole claim the interface makes.
 */
class StoredChoresTest {

    private val store = FakeChoreStore()
    private var clock: Clock = clockAt(date("2026-09-16"))
    private val chores: Chores = StoredChores(store) { clock }

    private fun travelTo(day: String) {
        clock = clockAt(date(day))
    }

    @Test
    fun `a new chore appears on the agenda under its first due date`() = runTest {
        chores.add(ChoreDraft("Vacuum", weekly(SATURDAY)))

        val agenda = chores.agenda().first()

        assertEquals(listOf(date("2026-09-19")), agenda.upcoming.map { it.dueDate })
        assertTrue(agenda.overdue.isEmpty())
        assertTrue(agenda.today.isEmpty())
    }

    @Test
    fun `completing records history and moves the chore to its next due date`() = runTest {
        val id = chores.add(ChoreDraft("Vacuum", weekly(SATURDAY)))
        travelTo("2026-09-19")

        chores.complete(id)

        val detail = chores.detail(id).first()!!
        assertEquals(date("2026-09-26"), detail.outstanding.dueDate)
        assertEquals(listOf(date("2026-09-19")), detail.history.map { it.dueDate })
        assertTrue(detail.history.single() is Resolution.Completion)
    }

    @Test
    fun `a manual skip is recorded distinctly from a lapse`() = runTest {
        val id = chores.add(ChoreDraft("Vacuum", weekly(SATURDAY)))
        travelTo("2026-09-19")

        chores.skip(id)

        val history = chores.detail(id).first()!!.history
        assertEquals(Resolution.Skip.Kind.Manual, (history.single() as Resolution.Skip).kind)
    }

    @Test
    fun `a missed occurrence is recorded as a lapse only once the user has seen it`() = runTest {
        val id = chores.add(ChoreDraft("Vacuum", weekly(SATURDAY)))

        travelTo("2026-09-19")
        chores.markSeen() // the digest goes out on the day it is due

        travelTo("2026-09-26")
        chores.markSeen()

        val detail = chores.detail(id).first()!!
        assertEquals(listOf(date("2026-09-19")), detail.history.map { it.dueDate })
        assertEquals(Resolution.Skip.Kind.Displaced, (detail.history.single() as Resolution.Skip).kind)
        assertEquals(date("2026-09-26"), detail.outstanding.dueDate)
    }

    @Test
    fun `a month away with nothing seen produces one waiting occurrence and no lapses`() = runTest {
        val id = chores.add(ChoreDraft("Vacuum", weekly(SATURDAY)))

        travelTo("2026-10-31")
        val detail = chores.detail(id).first()!!

        assertTrue(detail.history.isEmpty())
        assertEquals(date("2026-09-19"), detail.outstanding.dueDate)
        assertEquals(1, chores.agenda().first().overdue.size)
    }

    @Test
    fun `marking seen twice on the same day writes nothing the second time`() = runTest {
        val id = chores.add(ChoreDraft("Vacuum", weekly(SATURDAY)))
        travelTo("2026-09-19")
        chores.markSeen()
        travelTo("2026-09-26")

        chores.markSeen()
        val once = chores.detail(id).first()!!.history
        chores.markSeen()
        val twice = chores.detail(id).first()!!.history

        assertEquals(once, twice)
    }

    @Test
    fun `reading the agenda never writes`() = runTest {
        val id = chores.add(ChoreDraft("Vacuum", weekly(SATURDAY)))
        travelTo("2026-09-19")
        chores.markSeen()
        travelTo("2026-09-26")

        repeat(3) { chores.agenda().first() }

        assertTrue(chores.detail(id).first()!!.history.isEmpty())
    }

    @Test
    fun `the digest lists what is overdue and due today, soonest first`() = runTest {
        chores.add(ChoreDraft("Kettle", everyMonths(3)))
        chores.add(ChoreDraft("Vacuum", weekly(SATURDAY)))
        travelTo("2026-09-19")

        val due = chores.due()

        assertEquals(listOf("Kettle", "Vacuum"), due.map { it.chore.name })
    }

    @Test
    fun `the digest is empty when nothing is due, so the reminder stays silent`() = runTest {
        chores.add(ChoreDraft("Vacuum", weekly(SATURDAY)))

        assertTrue(chores.due().isEmpty())
    }

    @Test
    fun `editing a recurrence moves the outstanding occurrence`() = runTest {
        val id = chores.add(ChoreDraft("Vacuum", weekly(SATURDAY)))

        chores.edit(id, ChoreDraft("Vacuum", weekly(MONDAY)))

        assertEquals(date("2026-09-21"), chores.detail(id).first()!!.outstanding.dueDate)
    }

    @Test
    fun `editing a recurrence leaves an already overdue chore overdue`() = runTest {
        val id = chores.add(ChoreDraft("Kettle", everyMonths(3)))
        travelTo("2026-12-01") // due since 2026-09-16, untouched

        chores.edit(id, ChoreDraft("Kettle", everyMonths(12)))

        val outstanding = chores.detail(id).first()!!.outstanding
        assertEquals(date("2026-09-16"), outstanding.dueDate)
        assertTrue(outstanding.isOverdue(date("2026-12-01")))
    }

    @Test
    fun `editing to a rule that cannot fall on the overdue day leaves it there anyway`() = runTest {
        // Wednesdays, overdue since Wednesday the 16th, changed to Sundays on the Friday.
        // The next Sunday is the 20th: landing there would hand the user back a clean chore.
        val id = chores.add(ChoreDraft("Vacuum", weekly(WEDNESDAY)))
        travelTo("2026-09-18")

        chores.edit(id, ChoreDraft("Vacuum", weekly(SUNDAY)))

        val outstanding = chores.detail(id).first()!!.outstanding
        assertEquals(date("2026-09-16"), outstanding.dueDate)
        assertTrue(outstanding.isOverdue(date("2026-09-18")))
    }

    @Test
    fun `an edit holds the overdue day without reviving the completion behind it`() = runTest {
        // Mon and Thu, done on the Monday, overdue since the Tuesday, changed to Sundays on
        // the Wednesday: the target sits between the completion and the new rule's days.
        val id = chores.add(ChoreDraft("Bins", weekly(MONDAY, TUESDAY)))
        travelTo("2026-09-21")
        chores.complete(id)
        travelTo("2026-09-23")

        chores.edit(id, ChoreDraft("Bins", weekly(SUNDAY)))

        val detail = chores.detail(id).first()!!
        assertEquals(date("2026-09-22"), detail.outstanding.dueDate)
        assertEquals(listOf(date("2026-09-21")), detail.history.map { it.dueDate })
    }

    @Test
    fun `an edit cannot hold a chore whose newest resolution is due after it`() = runTest {
        // The documented residual gap: resolving twice in one day records a resolution for an
        // occurrence a period ahead, and no anchor can name the overdue day *and* supersede
        // that resolution, so the new rule steps from the resolution instead.
        val id = chores.add(ChoreDraft("Kettle", everyMonths(3)))
        chores.complete(id)
        chores.complete(id) // resolves the occurrence due 2026-12-16
        chores.edit(id, ChoreDraft("Kettle", everyDays(1)))
        travelTo("2026-09-20") // overdue since the 17th

        chores.edit(id, ChoreDraft("Kettle", weekly(SUNDAY)))

        assertEquals(date("2026-12-20"), chores.detail(id).first()!!.outstanding.dueDate)
    }

    @Test
    fun `lapses written in one catch-up still come back newest first`() = runTest {
        val id = chores.add(ChoreDraft("Vacuum", weekly(SATURDAY)))
        travelTo("2026-10-03")
        chores.markSeen() // nothing has lapsed yet: none of it had been shown
        travelTo("2026-10-10")

        chores.markSeen()

        val history = chores.detail(id).first()!!.history
        assertEquals(
            listOf(date("2026-10-03"), date("2026-09-26"), date("2026-09-19")),
            history.map { it.dueDate },
        )
    }

    @Test
    fun `archiving stops a chore falling due but keeps its history`() = runTest {
        val id = chores.add(ChoreDraft("Vacuum", weekly(SATURDAY)))
        travelTo("2026-09-19")
        chores.complete(id)

        chores.archive(id)

        assertTrue(chores.agenda().first().isEmpty)
        assertEquals(listOf("Vacuum"), chores.archived().first().map { it.name })
        assertEquals(1, chores.detail(id).first()!!.history.size)
    }

    @Test
    fun `restoring re-anchors to today rather than returning a year overdue`() = runTest {
        val id = chores.add(ChoreDraft("Vacuum", weekly(SATURDAY)))
        chores.archive(id)
        travelTo("2027-09-16")

        chores.restore(id)

        assertEquals(date("2027-09-18"), chores.detail(id).first()!!.outstanding.dueDate)
    }

    @Test
    fun `deleting discards the chore and its history`() = runTest {
        val id = chores.add(ChoreDraft("Vacuum", weekly(SATURDAY)))
        travelTo("2026-09-19")
        chores.complete(id)

        chores.delete(id)

        assertNull(chores.detail(id).first())
        assertTrue(chores.agenda().first().isEmpty)
    }

    private fun StoredChores(store: ChoreStore, clock: () -> Clock): Chores =
        StoredChores(store, MutableClock(clock))

    /** Lets a test move time without rebuilding the subject. */
    private class MutableClock(private val source: () -> Clock) : Clock() {
        override fun getZone() = source().zone
        override fun withZone(zone: java.time.ZoneId) = source().withZone(zone)
        override fun instant() = source().instant()
    }
}
