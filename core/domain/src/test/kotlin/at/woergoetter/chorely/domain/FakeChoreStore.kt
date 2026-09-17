package at.woergoetter.chorely.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * The second adapter at the [ChoreStore] seam — the one that makes it a real seam rather
 * than a hypothetical one. Room satisfies the same interface in `:core:data`.
 *
 * Deliberately naive: a list and a lock. If a test passes here and fails against Room, the
 * difference is Room's, and that is what the instrumented store tests are for.
 */
class FakeChoreStore : ChoreStore {

    private data class State(
        val chores: List<Chore> = emptyList(),
        val history: Map<ChoreId, List<Resolution>> = emptyMap(),
        val seenThrough: LocalDate? = null,
        val nextId: Long = 1,
    )

    private val state = MutableStateFlow(State())
    private val lock = Mutex()

    override fun book(): Flow<ChoreBook> = state.map { it.book() }

    override fun history(id: ChoreId): Flow<List<Resolution>> =
        state.map { s -> s.history[id].orEmpty().sortedWith(newestFirst) }

    override suspend fun <T> transact(block: suspend (ChoreEdit) -> T): T = lock.withLock {
        val entry = state.value
        val edit = Edit(entry)
        try {
            val result = block(edit)
            state.value = edit.current
            result
        } catch (t: Throwable) {
            state.value = entry // roll back
            throw t
        }
    }

    private inner class Edit(var current: State) : ChoreEdit {

        override suspend fun book(): ChoreBook = current.book()

        override suspend fun insert(draft: ChoreDraft, anchoredOn: LocalDate): ChoreId {
            val id = ChoreId(current.nextId)
            current = current.copy(
                chores = current.chores + Chore(id, draft.name, draft.recurrence, anchoredOn),
                nextId = current.nextId + 1,
            )
            return id
        }

        override suspend fun update(chore: Chore) {
            current = current.copy(chores = current.chores.map { if (it.id == chore.id) chore else it })
        }

        override suspend fun append(id: ChoreId, resolutions: List<Resolution>) {
            current = current.copy(
                history = current.history + (id to (current.history[id].orEmpty() + resolutions)),
            )
        }

        override suspend fun delete(id: ChoreId) {
            current = current.copy(
                chores = current.chores.filterNot { it.id == id },
                history = current.history - id,
            )
        }

        override suspend fun markSeen(through: LocalDate) {
            current = current.copy(seenThrough = through)
        }
    }

    private fun State.book() = ChoreBook(
        chores = chores.map { chore ->
            ChoreRecord(chore, history[chore.id].orEmpty().maxWithOrNull(byDueDateThenRecorded))
        },
        seenThrough = seenThrough,
    )

    private companion object {
        /**
         * Room orders history by `dueDate DESC, resolvedAt DESC`, and a catch-up stamps every
         * lapse it writes with one instant — so ordering on the instant alone would hand the
         * domain a run of lapses oldest first, which is not what the adapter returns.
         */
        val byDueDateThenRecorded = compareBy<Resolution>({ it.dueDate }, { it.at })
        val newestFirst = byDueDateThenRecorded.reversed()
    }
}
