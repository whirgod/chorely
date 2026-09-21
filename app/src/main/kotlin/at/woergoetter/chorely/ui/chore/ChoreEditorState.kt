package at.woergoetter.chorely.ui.chore

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreDraft
import at.woergoetter.chorely.domain.Recurrence
import java.time.DayOfWeek
import java.time.Period

/** Which of the two recurrences the form is currently offering. */
enum class RecurrenceKind { OnWeekdays, Every }

/** The unit a [Recurrence.Every] period is entered in. */
enum class PeriodUnit {
    Days,
    Weeks,
    Months,
    ;

    fun periodOf(count: Int): Period = when (this) {
        Days -> Period.ofDays(count)
        Weeks -> Period.ofWeeks(count)
        Months -> Period.ofMonths(count)
    }
}

/**
 * Everything the editor form holds, as the user has typed it rather than as the domain
 * would have it. Both recurrences are kept side by side, so flipping between the two kinds
 * and back does not lose what was already entered.
 *
 * Pure, and the only place the form's rules live: [toDraft] is the single answer to both
 * "is this saveable" and "what would it save". A screen that asked those separately could
 * enable a button for input the domain then rejects — [ChoreDraft] and [Recurrence] both
 * `require` their way out of invalid values, and a crash on Save is not a validation error.
 */
@Immutable
data class ChoreEditorState(
    val name: String = "",
    val kind: RecurrenceKind = RecurrenceKind.OnWeekdays,
    val days: Set<DayOfWeek> = emptySet(),
    /** As typed: the field has to be allowed to be empty, or mid-edit, and still render. */
    val count: String = "1",
    val unit: PeriodUnit = PeriodUnit.Weeks,
) {

    /** The draft this would save, or null if it is not yet saveable. */
    fun toDraft(): ChoreDraft? {
        val trimmed = name.trim().ifBlank { return null }
        return ChoreDraft(name = trimmed, recurrence = recurrence() ?: return null)
    }

    val isSaveable: Boolean get() = toDraft() != null

    fun withDay(day: DayOfWeek, selected: Boolean): ChoreEditorState =
        copy(days = if (selected) days + day else days - day)

    private fun recurrence(): Recurrence? = when (kind) {
        RecurrenceKind.OnWeekdays -> days.ifEmpty { return null }.let(Recurrence::OnWeekdays)
        RecurrenceKind.Every -> count.toIntOrNull()?.takeIf { it in 1..MAX_COUNT }
            ?.let { Recurrence.Every(unit.periodOf(it)) }
    }

    companion object {

        /**
         * The largest count the form accepts, generous in every unit it is spelled in: 999
         * days is nearly three years, 999 weeks nineteen, 999 months eighty-three.
         *
         * There has to be a bound at all because `Period.ofWeeks` multiplies by seven with
         * `Math.multiplyExact`, which throws from 306,783,379 weeks upwards. [toDraft] is
         * what composition asks whether Save should be offered, so that exception would land
         * on the keystroke that typed the digit and take the half-filled form down with it —
         * the worst possible place for one. An over-large count is refused the way a zero one
         * is, by simply not offering Save.
         */
        const val MAX_COUNT = 999

        /** The form filled in from an existing chore, for edit mode. */
        fun of(chore: Chore): ChoreEditorState {
            val blank = ChoreEditorState(name = chore.name)
            return when (val recurrence = chore.recurrence) {
                is Recurrence.OnWeekdays -> blank.copy(
                    kind = RecurrenceKind.OnWeekdays,
                    days = recurrence.days,
                )

                is Recurrence.Every -> {
                    // A period the form cannot express — one that fits no single unit, or
                    // more of one than [MAX_COUNT] — is unreachable from this app, which has
                    // only ever written what `periodOf` produces within the bound. Should one
                    // turn up, the count is cleared rather than rounded to something the user
                    // did not choose or prefilled at a plausible-looking number Save would
                    // then refuse: Save stays off until they have said what the recurrence is.
                    val (count, unit) = recurrence.period.inOneUnit() ?: return blank.copy(
                        kind = RecurrenceKind.Every,
                        count = "",
                    )
                    blank.copy(kind = RecurrenceKind.Every, count = count.toString(), unit = unit)
                }
            }
        }

        /**
         * Survives process death, which the ViewModel holding this would not: half a filled-in
         * form is exactly the thing a user resents retyping.
         */
        val Saver: Saver<ChoreEditorState?, Any> = listSaver(
            save = { state ->
                if (state == null) emptyList() else listOf(
                    state.name,
                    state.kind.name,
                    state.count,
                    state.unit.name,
                    // Safe to join on a comma: these are enum names, not user input.
                    state.days.joinToString(",") { it.name },
                )
            },
            restore = { saved ->
                ChoreEditorState(
                    name = saved[0],
                    kind = RecurrenceKind.valueOf(saved[1]),
                    count = saved[2],
                    unit = PeriodUnit.valueOf(saved[3]),
                    days = saved[4].split(",").filter(String::isNotEmpty)
                        .map(DayOfWeek::valueOf).toSet(),
                )
            },
        )
    }
}

/**
 * The period as a count of one of the units the form offers, or null if it takes more than
 * one of them, or more than [ChoreEditorState.MAX_COUNT] of a single one. Weeks win over
 * days where both fit, since `Period.ofWeeks` stores its weeks as days and "every 14 days"
 * is not how anyone says a fortnight.
 */
private fun Period.inOneUnit(): Pair<Int, PeriodUnit>? {
    val max = ChoreEditorState.MAX_COUNT
    val months = toTotalMonths()
    return when {
        months in 1..max.toLong() && days == 0 -> months.toInt() to PeriodUnit.Months
        months == 0L && days % 7 == 0 && days / 7 in 1..max -> days / 7 to PeriodUnit.Weeks
        months == 0L && days in 1..max -> days to PeriodUnit.Days
        else -> null
    }
}
