package at.woergoetter.chorely.domain

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** A chore together with the single newest resolution in its history. */
data class ChoreRecord(
    val chore: Chore,
    val lastResolution: Resolution?,
)

/**
 * Everything catch-up needs in order to run, read as one consistent snapshot.
 *
 * [seenThrough] is global rather than per-chore because "the user has been shown what is
 * due" is a fact about the user, not about any one chore.
 */
data class ChoreBook(
    val chores: List<ChoreRecord> = emptyList(),
    val seenThrough: LocalDate? = null,
)

/**
 * Persistence for chores and their resolution history.
 *
 * This is a port: the domain declares it, `:core:data` implements it over Room, and tests
 * implement it in memory. It deliberately speaks only of storing and reading — no due
 * dates, no catch-up, no notion of what is outstanding. Anything an adapter would have to
 * *decide* belongs in [catchUp] instead.
 */
interface ChoreStore {

    /** Every chore, archived ones included, each with its newest resolution. */
    fun book(): Flow<ChoreBook>

    /** One chore's full resolution history, newest first. Empty if the chore is gone. */
    fun history(id: ChoreId): Flow<List<Resolution>>

    /**
     * Runs [block] against a consistent snapshot, atomically. Reads inside [block] see
     * writes made earlier within the same [block]; a failure rolls the whole thing back.
     *
     * Catch-up reads and then writes, so it is only idempotent if those happen together —
     * which is why every mutation in [Chores] goes through here.
     */
    suspend fun <T> transact(block: suspend (ChoreEdit) -> T): T
}

/** The write vocabulary available inside [ChoreStore.transact]. */
interface ChoreEdit {

    suspend fun book(): ChoreBook

    suspend fun insert(draft: ChoreDraft, anchoredOn: LocalDate): ChoreId

    /** Replaces the chore row. Never touches its history. */
    suspend fun update(chore: Chore)

    /** Appends to the append-only history. Existing rows are never modified. */
    suspend fun append(id: ChoreId, resolutions: List<Resolution>)

    /** The deliberate, destructive act: discards the chore *and* its history. */
    suspend fun delete(id: ChoreId)

    /** Records that the user has been shown what was due as of [through]. */
    suspend fun markSeen(through: LocalDate)
}
