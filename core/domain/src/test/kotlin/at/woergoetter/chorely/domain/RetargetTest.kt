package at.woergoetter.chorely.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RetargetTest {

    private val id = ChoreId(1)
    private val today = date("2026-09-16")

    @Test
    fun `a chore that was not overdue simply moves to wherever the new rule puts it`() {
        val moved = retarget(
            previous = Occurrence(id, date("2026-09-19")),
            recomputed = Occurrence(id, date("2026-12-01")),
            today = today,
        )
        assertEquals(date("2026-12-01"), moved.dueDate)
    }

    @Test
    fun `a rule change cannot make a neglected chore look clean`() {
        val moved = retarget(
            previous = Occurrence(id, date("2026-08-01")),
            recomputed = Occurrence(id, date("2026-12-01")),
            today = today,
        )
        assertEquals(date("2026-08-01"), moved.dueDate)
    }

    @Test
    fun `tightening a rule may pull an overdue occurrence further back`() {
        val moved = retarget(
            previous = Occurrence(id, date("2026-08-01")),
            recomputed = Occurrence(id, date("2026-07-01")),
            today = today,
        )
        assertEquals(date("2026-07-01"), moved.dueDate)
    }
}
