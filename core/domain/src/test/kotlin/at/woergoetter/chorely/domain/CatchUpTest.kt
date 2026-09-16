package at.woergoetter.chorely.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.THURSDAY

/**
 * Tests at the interface of the one function that holds every due-date invariant. If a
 * rule in AGENTS.md's "Domain invariants" is real, it is pinned here.
 */
class CatchUpTest {

    // 2026-09-16 is a Wednesday; 09-19 Saturday; 09-21 Monday.

    @Test
    fun `a new calendar-anchored chore falls due on the first matching day from its anchor`() {
        val result = catchUp(
            chore = chore(weekly(SATURDAY), anchoredOn = "2026-09-16"),
            lastResolution = null,
            seenThrough = null,
            clock = clockAt(date("2026-09-16")),
        )
        assertEquals(date("2026-09-19"), result.outstanding.dueDate)
    }

    @Test
    fun `an anchor that already lands on a matching day is itself the first due date`() {
        val result = catchUp(
            chore = chore(weekly(SATURDAY), anchoredOn = "2026-09-19"),
            lastResolution = null,
            seenThrough = null,
            clock = clockAt(date("2026-09-16")),
        )
        assertEquals(date("2026-09-19"), result.outstanding.dueDate)
    }

    @Test
    fun `a new completion-anchored chore falls due on its anchor day`() {
        val result = catchUp(
            chore = chore(everyMonths(3), anchoredOn = "2026-09-16"),
            lastResolution = null,
            seenThrough = null,
            clock = clockAt(date("2026-09-16")),
        )
        assertEquals(date("2026-09-16"), result.outstanding.dueDate)
    }

    @Test
    fun `a late completion does not move a calendar-anchored chore off its day`() {
        // Due Saturday the 19th, done on Monday the 21st: still due the following Saturday.
        val result = catchUp(
            chore = chore(weekly(SATURDAY), anchoredOn = "2026-09-16"),
            lastResolution = completedOn(dueDate = "2026-09-19", at = "2026-09-21"),
            seenThrough = null,
            clock = clockAt(date("2026-09-21")),
        )
        assertEquals(date("2026-09-26"), result.outstanding.dueDate)
    }

    @Test
    fun `a late completion shifts the whole completion-anchored series`() {
        // Due 2026-09-16, actually done 2026-09-30: next is three months from the doing.
        val result = catchUp(
            chore = chore(everyMonths(3), anchoredOn = "2026-06-16"),
            lastResolution = completedOn(dueDate = "2026-09-16", at = "2026-09-30"),
            seenThrough = null,
            clock = clockAt(date("2026-09-30")),
        )
        assertEquals(date("2026-12-30"), result.outstanding.dueDate)
    }

    @Test
    fun `an early completion is accepted and still anchors the next occurrence`() {
        val result = catchUp(
            chore = chore(everyMonths(3), anchoredOn = "2026-06-16"),
            lastResolution = completedOn(dueDate = "2026-09-16", at = "2026-09-10"),
            seenThrough = null,
            clock = clockAt(date("2026-09-10")),
        )
        assertEquals(date("2026-12-10"), result.outstanding.dueDate)
    }

    @Test
    fun `a manual skip on a completion-anchored chore measures from the due date, not the tap`() {
        val result = catchUp(
            chore = chore(everyMonths(3), anchoredOn = "2026-06-16"),
            lastResolution = skippedOn(dueDate = "2026-09-16", at = "2026-09-30"),
            seenThrough = null,
            clock = clockAt(date("2026-09-30")),
        )
        assertEquals(date("2026-12-16"), result.outstanding.dueDate)
    }

    @Test
    fun `a missed calendar-anchored occurrence is displaced once its successor falls due`() {
        // Saturday the 19th was seen and missed; by Saturday the 26th it is displaced.
        val result = catchUp(
            chore = chore(weekly(SATURDAY), anchoredOn = "2026-09-19"),
            lastResolution = null,
            seenThrough = date("2026-09-25"),
            clock = clockAt(date("2026-09-26")),
        )
        assertEquals(listOf(date("2026-09-19")), result.displaced.map { it.dueDate })
        assertEquals(Resolution.Skip.Kind.Displaced, result.displaced.single().kind)
        assertEquals(date("2026-09-26"), result.outstanding.dueDate)
    }

    @Test
    fun `an occurrence is not displaced before its successor falls due`() {
        // On the Friday, the missed Saturday is overdue but nothing has arrived to push it.
        val result = catchUp(
            chore = chore(weekly(SATURDAY), anchoredOn = "2026-09-19"),
            lastResolution = null,
            seenThrough = date("2026-09-25"),
            clock = clockAt(date("2026-09-25")),
        )
        assertTrue(result.displaced.isEmpty())
        assertEquals(date("2026-09-19"), result.outstanding.dueDate)
        assertTrue(result.outstanding.isOverdue(date("2026-09-25")))
    }

    @Test
    fun `an occurrence the user has never been shown is never displaced`() {
        // A month away with notifications never seen: one occurrence waits, no lapses invented.
        val result = catchUp(
            chore = chore(weekly(SATURDAY), anchoredOn = "2026-09-19"),
            lastResolution = null,
            seenThrough = null,
            clock = clockAt(date("2026-10-31")),
        )
        assertTrue(result.displaced.isEmpty())
        assertEquals(date("2026-09-19"), result.outstanding.dueDate)
    }

    @Test
    fun `displacement stops at the last occurrence the user was shown`() {
        // Seen through the 26th, now the 31st of October: only occurrences up to the 26th
        // can have been missed knowingly, and the outstanding one is the successor of that.
        val result = catchUp(
            chore = chore(weekly(SATURDAY), anchoredOn = "2026-09-19"),
            lastResolution = null,
            seenThrough = date("2026-09-26"),
            clock = clockAt(date("2026-10-31")),
        )
        assertEquals(listOf(date("2026-09-19"), date("2026-09-26")), result.displaced.map { it.dueDate })
        assertEquals(date("2026-10-03"), result.outstanding.dueDate)
    }

    @Test
    fun `a completion-anchored occurrence waits indefinitely and is never displaced`() {
        val result = catchUp(
            chore = chore(everyMonths(3), anchoredOn = "2026-01-01"),
            lastResolution = null,
            seenThrough = date("2026-09-16"),
            clock = clockAt(date("2026-09-16")),
        )
        assertTrue(result.displaced.isEmpty())
        assertEquals(date("2026-01-01"), result.outstanding.dueDate)
    }

    @Test
    fun `catch-up is idempotent once its displacements are fed back as history`() {
        val vacuum = chore(weekly(SATURDAY), anchoredOn = "2026-09-19")
        val clock = clockAt(date("2026-10-31"))
        val first = catchUp(vacuum, null, date("2026-09-26"), clock)

        val second = catchUp(vacuum, first.displaced.last(), date("2026-09-26"), clock)

        assertTrue(second.displaced.isEmpty())
        assertEquals(first.outstanding, second.outstanding)
    }

    @Test
    fun `a multi-weekday rule steps to the next matching day, not a fixed period`() {
        val bins = chore(weekly(MONDAY, THURSDAY), anchoredOn = "2026-09-16")
        val monday = catchUp(bins, completedOn("2026-09-17"), null, clockAt(date("2026-09-17")))
        assertEquals(date("2026-09-21"), monday.outstanding.dueDate)

        val thursday = catchUp(bins, completedOn("2026-09-21"), null, clockAt(date("2026-09-21")))
        assertEquals(date("2026-09-24"), thursday.outstanding.dueDate)
    }

    @Test
    fun `resolutions older than the anchor belong to a superseded rule and do not move the series`() {
        val result = catchUp(
            chore = chore(weekly(SATURDAY), anchoredOn = "2026-10-03"),
            lastResolution = completedOn("2026-09-19"),
            seenThrough = null,
            clock = clockAt(date("2026-09-20")),
        )
        assertEquals(date("2026-10-03"), result.outstanding.dueDate)
    }

    @Test
    fun `the completion instant is read in the clock's zone`() {
        // 23:30 in Vienna on the 16th is still the 16th there, but the 17th in Tokyo.
        val atVienna = Resolution.Completion(
            dueDate = date("2026-09-16"),
            at = date("2026-09-16").atTime(23, 30).atZone(Vienna).toInstant(),
        )
        val kettle = chore(everyDays(30), anchoredOn = "2026-08-01")

        val vienna = catchUp(kettle, atVienna, null, clockAt(date("2026-09-16"), Vienna))
        val tokyo = catchUp(kettle, atVienna, null, clockAt(date("2026-09-17"), java.time.ZoneId.of("Asia/Tokyo")))

        assertEquals(date("2026-10-16"), vienna.outstanding.dueDate)
        assertEquals(date("2026-10-17"), tokyo.outstanding.dueDate)
    }
}
