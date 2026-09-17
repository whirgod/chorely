package at.woergoetter.chorely.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period

/**
 * Where a chore's rhythm comes from. Derived from the kind of [Recurrence], never
 * stored alongside it: a recurrence cannot be calendar-anchored *and* weekly-by-period,
 * and making anchoring an independent flag would allow that combination to be spelled.
 */
enum class Anchoring {
    /** The world supplies the day. A late completion must not move the next due date. */
    Calendar,

    /** Elapsed time since the chore was last done is the only thing that matters. */
    Completion,
}

/**
 * The rule on a chore that determines when it next falls due. Every chore has
 * exactly one.
 *
 * Adding a kind (the backlog's monthly and day-of-month schedules) means adding a
 * subtype here and a branch in [nextDueDate]; nothing outside this file, and no
 * interface in the app, has to learn about it.
 */
sealed interface Recurrence {

    val anchoring: Anchoring

    /**
     * Calendar-anchored: "every Tuesday", "Mon and Thu". Doing it late does not move
     * the following due date, and a missed occurrence is displaced by its successor.
     */
    data class OnWeekdays(val days: Set<DayOfWeek>) : Recurrence {
        init {
            require(days.isNotEmpty()) { "a weekday recurrence needs at least one day" }
        }

        override val anchoring: Anchoring get() = Anchoring.Calendar
    }

    /**
     * Completion-anchored: "every 3 months". Doing it late moves the whole future
     * series later, which is correct rather than drift, and nothing ever arrives to
     * displace an outstanding occurrence.
     */
    data class Every(val period: Period) : Recurrence {
        init {
            require(!period.isZero && !period.isNegative) { "a period recurrence must move forward" }
        }

        override val anchoring: Anchoring get() = Anchoring.Completion
    }
}

/**
 * The first day on or after [date] on which this recurrence falls due.
 *
 * For a completion-anchored recurrence there is no calendar grid to land on, so
 * [date] itself is the answer.
 */
internal fun Recurrence.firstDueOnOrAfter(date: LocalDate): LocalDate = when (this) {
    is Recurrence.OnWeekdays -> generateSequence(date) { it.plusDays(1) }.first { it.dayOfWeek in days }
    is Recurrence.Every -> date
}

/**
 * The due date that follows [previousDue] on the calendar grid.
 *
 * Only meaningful for calendar-anchored recurrences: a completion-anchored one has no
 * successor until the outstanding occurrence is resolved, which is exactly why it never
 * auto-skips.
 */
internal fun Recurrence.OnWeekdays.nextDueAfter(previousDue: LocalDate): LocalDate =
    firstDueOnOrAfter(previousDue.plusDays(1))
