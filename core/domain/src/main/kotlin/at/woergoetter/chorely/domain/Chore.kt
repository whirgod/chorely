package at.woergoetter.chorely.domain

import java.time.Instant
import java.time.LocalDate

/** Identity of a [Chore]. Assigned by the store on insert. */
@JvmInline
value class ChoreId(val value: Long)

/**
 * A recurring piece of housework together with the rule for how often it recurs.
 * Long-lived: a chore is not a single act of cleaning.
 */
data class Chore(
    val id: ChoreId,
    val name: String,
    val recurrence: Recurrence,
    /**
     * The due date of the *current* rule's first occurrence.
     *
     * Set from today when the chore is created or restored — rounded onto the rule's grid by
     * [anchorFor] — and moved by an edit to wherever the new rule puts the outstanding
     * occurrence. A due date rather than a lower bound on one, so an edit can hold an overdue
     * occurrence on a day the new rule would never place one. Resolutions older than this
     * belong to a superseded rule: they stay in the history as record, and [catchUp] ignores
     * them. Together that is what lets an edit re-target the outstanding occurrence without
     * storing it.
     */
    val anchoredOn: LocalDate,
    /** Non-null once archived. Archiving never destroys history. */
    val archivedAt: Instant? = null,
) {
    val isArchived: Boolean get() = archivedAt != null
}

/** The user-supplied part of a chore, for creating and editing. */
data class ChoreDraft(
    val name: String,
    val recurrence: Recurrence,
) {
    init {
        require(name.isNotBlank()) { "a chore needs a name" }
    }
}
