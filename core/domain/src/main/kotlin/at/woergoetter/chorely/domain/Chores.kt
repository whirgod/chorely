package at.woergoetter.chorely.domain

import kotlinx.coroutines.flow.Flow

/**
 * The whole of Chorely's behaviour, as the app sees it.
 *
 * Every screen, the daily digest worker and the boot receiver talk to this and nothing
 * else. Catch-up, auto-skip, the two anchorings, transactions and the clock are all behind
 * it: a caller can use every method here correctly while knowing only what the words in
 * [CONTEXT.md](../../../../../../../CONTEXT.md) mean.
 *
 * Reads are pure derivations and never write, so collecting [agenda] has no side effects.
 * The lapses that catch-up discovers are persisted by [markSeen] and by every mutation
 * below — which is also the only way the history can honestly record them, since a lapse
 * is only a lapse once the user has had a chance to act on it.
 *
 * An archived chore accrues none: its occurrences stopped falling due when it was archived.
 * So [complete], [skip], [edit] and [archive] ignore one, silently — only [restore] and
 * [delete] act on it — and [detail] still derives it, which is why a screen showing a chore
 * must stop offering those actions once it reads [Chore.isArchived].
 */
interface Chores {

    /** What is overdue, due today, and coming up. Archived chores are excluded. */
    fun agenda(): Flow<Agenda>

    /** One chore with its outstanding occurrence and full history; null once deleted. */
    fun detail(id: ChoreId): Flow<ChoreDetail?>

    /** Archived chores, newest first, for the restore-or-delete screen. */
    fun archived(): Flow<List<Chore>>

    /**
     * What the daily digest should list: overdue and due today, soonest first.
     * Empty means the digest stays silent.
     */
    suspend fun due(): List<DueChore>

    /**
     * Records that the user has now been shown what is due, and writes the auto-skips
     * that fact makes real. Called when the overview is displayed and after the daily
     * digest has actually been posted — never merely because a background job ran.
     */
    suspend fun markSeen()

    suspend fun add(draft: ChoreDraft): ChoreId

    /**
     * Applies [draft] and recomputes the outstanding occurrence under the new recurrence.
     * An occurrence that was already overdue stays overdue. Ignores an archived chore.
     */
    suspend fun edit(id: ChoreId, draft: ChoreDraft)

    /** Resolves the outstanding occurrence as done. Permitted before the due date. Ignores an archived chore. */
    suspend fun complete(id: ChoreId)

    /** Resolves the outstanding occurrence as deliberately passed over. Ignores an archived chore. */
    suspend fun skip(id: ChoreId)

    /** Stops the chore falling due, keeping its history. Archiving it again changes nothing. */
    suspend fun archive(id: ChoreId)

    /** Un-archives, with the outstanding occurrence recomputed from today. A chore that is not archived is left as it is. */
    suspend fun restore(id: ChoreId)

    /** Discards an archived chore and its history. Not undoable. An active chore is left as it is. */
    suspend fun delete(id: ChoreId)
}
