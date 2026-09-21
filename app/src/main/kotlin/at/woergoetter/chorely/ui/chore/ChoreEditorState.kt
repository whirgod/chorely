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
        RecurrenceKind.Every -> unit.periodOf(count.toIntOrNull()?.takeIf { it > 0 } ?: return null)
            .let(Recurrence::Every)
    }

    companion object {

        /** The form filled in from an existing chore, for edit mode. */
        fun of(chore: Chore): ChoreEditorState {
            val blank = ChoreEditorState(name = chore.name)
            return when (val recurrence = chore.recurrence) {
                is Recurrence.OnWeekdays -> blank.copy(
                    kind = RecurrenceKind.OnWeekdays,
                    days = recurrence.days,
                )

                is Recurrence.Every -> {
                    // A period that fits no single unit is unreachable from this app, which
                    // has only ever written what `periodOf` produces. Should one turn up, the
                    // count is cleared rather than rounded to something the user did not
                    // choose: Save stays off until they have said what the recurrence is.
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
 * one. Weeks win over days where both fit, since `Period.ofWeeks` stores its weeks as days
 * and "every 14 days" is not how anyone says a fortnight.
 */
private fun Period.inOneUnit(): Pair<Int, PeriodUnit>? {
    val months = toTotalMonths()
    return when {
        months in 1..Int.MAX_VALUE.toLong() && days == 0 -> months.toInt() to PeriodUnit.Months
        months == 0L && days > 0 && days % 7 == 0 -> days / 7 to PeriodUnit.Weeks
        months == 0L && days > 0 -> days to PeriodUnit.Days
        else -> null
    }
}
