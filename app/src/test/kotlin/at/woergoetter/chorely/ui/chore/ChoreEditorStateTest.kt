package at.woergoetter.chorely.ui.chore

import androidx.compose.runtime.saveable.SaverScope
import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreDraft
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period

/**
 * The form's rules. [ChoreDraft] and [Recurrence] throw on invalid values rather than
 * returning them, so "is this saveable" has to be answered before Save is offered — that
 * is what these pin.
 */
class ChoreEditorStateTest {

    private fun chore(recurrence: Recurrence, name: String = "Vacuum") = Chore(
        id = ChoreId(1),
        name = name,
        recurrence = recurrence,
        anchoredOn = LocalDate.of(2026, 9, 21),
    )

    @Test
    fun `a blank form is not saveable`() {
        assertNull(ChoreEditorState().toDraft())
    }

    @Test
    fun `a name alone is not saveable, since no day is picked yet`() {
        assertNull(ChoreEditorState(name = "Vacuum").toDraft())
    }

    @Test
    fun `a name of only whitespace is not saveable`() {
        val state = ChoreEditorState(name = "   ").withDay(DayOfWeek.SATURDAY, true)

        assertNull(state.toDraft())
    }

    @Test
    fun `the saved name is trimmed`() {
        val state = ChoreEditorState(name = "  Vacuum  ").withDay(DayOfWeek.SATURDAY, true)

        assertEquals("Vacuum", state.toDraft()?.name)
    }

    @Test
    fun `a name and at least one day is saveable`() {
        val state = ChoreEditorState(name = "Bins")
            .withDay(DayOfWeek.MONDAY, true)
            .withDay(DayOfWeek.THURSDAY, true)

        assertEquals(
            ChoreDraft("Bins", Recurrence.OnWeekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))),
            state.toDraft(),
        )
    }

    @Test
    fun `deselecting the last day makes the form unsaveable again`() {
        val state = ChoreEditorState(name = "Bins")
            .withDay(DayOfWeek.MONDAY, true)
            .withDay(DayOfWeek.MONDAY, false)

        assertFalse(state.isSaveable)
    }

    @Test
    fun `an empty count is not saveable`() {
        val state = ChoreEditorState(name = "Descale", kind = RecurrenceKind.Every, count = "")

        assertNull(state.toDraft())
    }

    @Test
    fun `a count of zero is not saveable, since a period must move forward`() {
        val state = ChoreEditorState(name = "Descale", kind = RecurrenceKind.Every, count = "0")

        assertNull(state.toDraft())
    }

    @Test
    fun `the largest accepted count saves the period it names, in every unit`() {
        val max = ChoreEditorState.MAX_COUNT
        val periods = mapOf(
            PeriodUnit.Days to Period.ofDays(max),
            PeriodUnit.Weeks to Period.ofWeeks(max),
            PeriodUnit.Months to Period.ofMonths(max),
        )

        periods.forEach { (unit, period) ->
            val state =
                ChoreEditorState("Descale", RecurrenceKind.Every, count = "$max", unit = unit)

            assertEquals(ChoreDraft("Descale", Recurrence.Every(period)), state.toDraft())
        }
    }

    @Test
    fun `a count one past the largest accepted one is not saveable, in every unit`() {
        PeriodUnit.entries.forEach { unit ->
            val state = ChoreEditorState(
                "Descale",
                RecurrenceKind.Every,
                count = "${ChoreEditorState.MAX_COUNT + 1}",
                unit = unit,
            )

            assertNull(state.toDraft())
        }
    }

    @Test
    fun `a count that would overflow the period arithmetic is refused, never thrown`() {
        // 306,783,379 is where `Period.ofWeeks` multiplying by seven overflows an Int; days
        // and months take that count without complaint, and are turned down for being past
        // the bound rather than for arithmetic. Asking the form is what composition does to
        // decide whether Save is offered, so throwing here would cost the user the form.
        PeriodUnit.entries.forEach { unit ->
            val state =
                ChoreEditorState("Descale", RecurrenceKind.Every, count = "306783379", unit = unit)

            assertNull(state.toDraft())
            assertFalse(state.isSaveable)
        }
    }

    @Test
    fun `a count too large to be a number at all is not saveable`() {
        val state =
            ChoreEditorState(name = "Descale", kind = RecurrenceKind.Every, count = "99999999999")

        assertNull(state.toDraft())
    }

    @Test
    fun `a count that is not a number is not saveable`() {
        val state = ChoreEditorState(name = "Descale", kind = RecurrenceKind.Every, count = "3 ")

        assertNull(state.toDraft())
    }

    @Test
    fun `each unit saves the period it names`() {
        val states = mapOf(
            PeriodUnit.Days to Period.ofDays(3),
            PeriodUnit.Weeks to Period.ofWeeks(3),
            PeriodUnit.Months to Period.ofMonths(3),
        )

        states.forEach { (unit, period) ->
            val state = ChoreEditorState("Descale", RecurrenceKind.Every, count = "3", unit = unit)

            assertEquals(ChoreDraft("Descale", Recurrence.Every(period)), state.toDraft())
        }
    }

    @Test
    fun `the unselected kind's input does not leak into the draft`() {
        val state = ChoreEditorState(name = "Descale", kind = RecurrenceKind.Every, count = "3")
            .withDay(DayOfWeek.MONDAY, true)

        assertEquals(Recurrence.Every(Period.ofWeeks(3)), state.toDraft()?.recurrence)
    }

    @Test
    fun `switching kinds keeps what was already entered for the other one`() {
        val weekdays = ChoreEditorState(name = "Bins").withDay(DayOfWeek.MONDAY, true)
        val period = weekdays.copy(kind = RecurrenceKind.Every, count = "3")

        assertEquals(weekdays.toDraft(), period.copy(kind = RecurrenceKind.OnWeekdays).toDraft())
    }

    @Test
    fun `a weekday chore prefills its days`() {
        val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)
        val state = ChoreEditorState.of(chore(Recurrence.OnWeekdays(days), name = "Bins"))

        assertEquals("Bins", state.name)
        assertEquals(RecurrenceKind.OnWeekdays, state.kind)
        assertEquals(days, state.days)
    }

    @Test
    fun `every period the form can produce round-trips through prefill unchanged`() {
        val periods = listOf(
            Period.ofDays(1), Period.ofDays(3), Period.ofDays(10),
            Period.ofWeeks(1), Period.ofWeeks(2), Period.ofWeeks(6),
            Period.ofMonths(1), Period.ofMonths(3), Period.ofMonths(18),
        )

        periods.forEach { period ->
            val original = chore(Recurrence.Every(period))
            val prefilled = ChoreEditorState.of(original)

            assertEquals(period, (prefilled.toDraft()?.recurrence as Recurrence.Every).period)
        }
    }

    @Test
    fun `a whole number of weeks prefills as weeks rather than days`() {
        val state = ChoreEditorState.of(chore(Recurrence.Every(Period.ofDays(14))))

        assertEquals(PeriodUnit.Weeks, state.unit)
        assertEquals("2", state.count)
    }

    @Test
    fun `a year prefills as the months it is worth`() {
        val state = ChoreEditorState.of(chore(Recurrence.Every(Period.ofYears(1))))

        assertEquals(PeriodUnit.Months, state.unit)
        assertEquals("12", state.count)
        assertEquals(
            LocalDate.of(2026, 9, 21).plusYears(1),
            LocalDate.of(2026, 9, 21).plus((state.toDraft()?.recurrence as Recurrence.Every).period),
        )
    }

    @Test
    fun `a period no single unit can express prefills empty rather than rounded`() {
        val state = ChoreEditorState.of(chore(Recurrence.Every(Period.of(0, 1, 3))))

        assertEquals(RecurrenceKind.Every, state.kind)
        assertEquals("", state.count)
        assertFalse(state.isSaveable)
    }

    @Test
    fun `a stored period larger than the form accepts prefills empty rather than unsaveable`() {
        val tooLarge = ChoreEditorState.MAX_COUNT + 1
        val periods = listOf(
            Period.ofDays(tooLarge),
            Period.ofWeeks(tooLarge),
            Period.ofMonths(tooLarge),
        )

        periods.forEach { period ->
            val state = ChoreEditorState.of(chore(Recurrence.Every(period)))

            assertEquals(RecurrenceKind.Every, state.kind)
            assertEquals("", state.count)
            assertFalse(state.isSaveable)
        }
    }

    @Test
    fun `the saver round-trips a filled-in form`() {
        val state = ChoreEditorState(
            name = "Bins, and the recycling",
            kind = RecurrenceKind.Every,
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            count = "3",
            unit = PeriodUnit.Months,
        )

        assertEquals(state, state.saveAndRestore())
    }

    @Test
    fun `the saver round-trips a form with no days picked`() {
        val state = ChoreEditorState(name = "Bins")

        assertEquals(state, state.saveAndRestore())
    }

    @Test
    fun `a form that has not been filled in yet is not saved at all`() {
        assertNull(save(null))
    }

    @Test
    fun `a chore whose recurrence the form can express is saveable straight after prefill`() {
        val state = ChoreEditorState.of(chore(Recurrence.OnWeekdays(setOf(DayOfWeek.SATURDAY))))

        assertTrue(state.isSaveable)
    }
}

/** Nothing the form holds is un-saveable, so the bundle's own rejection never applies. */
private val alwaysSaveable = SaverScope { true }

private fun save(state: ChoreEditorState?): Any? =
    with(ChoreEditorState.Saver) { alwaysSaveable.save(state) }

private fun ChoreEditorState.saveAndRestore(): ChoreEditorState? =
    ChoreEditorState.Saver.restore(save(this)!!)
