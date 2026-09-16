package at.woergoetter.chorely.domain

import java.time.Instant
import java.time.LocalDate

/**
 * A single expected doing of a chore, on a due date.
 *
 * The outstanding occurrence is always *derived* from the recurrence plus the newest
 * stored resolution, never stored: a device that has been off for a month shows correct
 * state the moment it opens, with no background job involved.
 */
data class Occurrence(
    val choreId: ChoreId,
    val dueDate: LocalDate,
) {
    /**
     * Whether the due date has passed. Not a state of its own, and not a judgement —
     * an overdue occurrence is still simply waiting.
     */
    fun isOverdue(today: LocalDate): Boolean = dueDate < today
}

/**
 * The record of an occurrence having been resolved. Append-only: a resolution is never
 * overwritten or deleted, and archiving a chore retains all of them.
 */
sealed interface Resolution {

    /** The due date of the occurrence this resolved. */
    val dueDate: LocalDate

    /** When the resolution happened. May precede [dueDate]: chores can be done early. */
    val at: Instant

    /** The user did the chore. */
    data class Completion(
        override val dueDate: LocalDate,
        override val at: Instant,
    ) : Resolution

    /** The occurrence passed without the chore being done. */
    data class Skip(
        override val dueDate: LocalDate,
        override val at: Instant,
        val kind: Kind,
    ) : Resolution {
        /**
         * A manual skip is a decision; a displaced skip is a lapse. They are recorded
         * distinctly because they mean different things in the history.
         */
        enum class Kind {
            /** The user decided this one was not needed. */
            Manual,

            /** A calendar-anchored successor fell due and pushed this one out. */
            Displaced,
        }
    }
}
