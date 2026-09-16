package at.woergoetter.chorely.domain

import java.time.LocalDate

/** A chore paired with its outstanding occurrence. */
data class DueChore(
    val chore: Chore,
    val occurrence: Occurrence,
) {
    val dueDate: LocalDate get() = occurrence.dueDate
}

/**
 * What is due, split the way the overview screen shows it. Every list is sorted by due
 * date, then by name, so the UI never has to sort and two screens cannot disagree.
 */
data class Agenda(
    val overdue: List<DueChore> = emptyList(),
    val today: List<DueChore> = emptyList(),
    val upcoming: List<DueChore> = emptyList(),
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && today.isEmpty() && upcoming.isEmpty()
}

/** Everything the per-chore screen shows: the chore, where it stands, and its history. */
data class ChoreDetail(
    val chore: Chore,
    val outstanding: Occurrence,
    /** Newest first. Append-only, so this only ever grows. */
    val history: List<Resolution>,
)
