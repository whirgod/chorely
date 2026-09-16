package at.woergoetter.chorely.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/**
 * The only implementation of [Chores]: a [ChoreStore], a [Clock], and [catchUp].
 *
 * There is nothing else to it, and that is the point — every hard decision is in the pure
 * function this delegates to, where it is tested without a store, a coroutine or a device.
 * What lives here is the read/write split: reads derive and never write, writes catch up
 * first and then mutate, all inside one transaction.
 */
class StoredChores(
    private val store: ChoreStore,
    private val clock: Clock,
) : Chores {

    override fun agenda(): Flow<Agenda> = store.book().map { book ->
        val today = LocalDate.now(clock)
        val due = book.active()
            .map { it.dueChore(book.seenThrough) }
            .sortedWith(byDueDateThenName)
        Agenda(
            overdue = due.filter { it.dueDate < today },
            today = due.filter { it.dueDate == today },
            upcoming = due.filter { it.dueDate > today },
        )
    }

    override fun detail(id: ChoreId): Flow<ChoreDetail?> =
        combine(store.book(), store.history(id)) { book, history ->
            val record = book.record(id) ?: return@combine null
            ChoreDetail(
                chore = record.chore,
                outstanding = record.outstanding(book.seenThrough),
                history = history,
            )
        }

    override fun archived(): Flow<List<Chore>> = store.book().map { book ->
        book.chores.map { it.chore }.filter { it.isArchived }.sortedByDescending { it.archivedAt }
    }

    override suspend fun due(): List<DueChore> {
        val today = LocalDate.now(clock)
        val book = store.transact { it.book() }
        return book.active()
            .map { it.dueChore(book.seenThrough) }
            .filter { it.dueDate <= today }
            .sortedWith(byDueDateThenName)
    }

    override suspend fun markSeen(): Unit = store.transact { edit ->
        val book = edit.book()
        book.active().forEach { record ->
            val displaced = record.catchUp(book.seenThrough).displaced
            if (displaced.isNotEmpty()) edit.append(record.chore.id, displaced)
        }
        edit.markSeen(LocalDate.now(clock))
    }

    override suspend fun add(draft: ChoreDraft): ChoreId = store.transact { edit ->
        edit.insert(draft, anchoredOn = LocalDate.now(clock))
    }

    override suspend fun edit(id: ChoreId, draft: ChoreDraft): Unit = store.transact { edit ->
        val (record, seenThrough) = edit.caughtUp(id) ?: return@transact
        val previous = record.outstanding(seenThrough)
        val edited = record.chore.copy(name = draft.name, recurrence = draft.recurrence)
        val recomputed = catchUp(edited, record.lastResolution, seenThrough, clock).outstanding
        val target = retarget(previous, recomputed, LocalDate.now(clock))

        // Re-anchoring is what makes the target stick: every resolution in play has a due
        // date strictly before `previous`, and `target` never falls after `previous`, so
        // none of them survives the new anchor and the derivation lands exactly on `target`.
        edit.update(edited.copy(anchoredOn = target.dueDate))
    }

    override suspend fun complete(id: ChoreId): Unit = resolve(id) { due, at ->
        Resolution.Completion(dueDate = due, at = at)
    }

    override suspend fun skip(id: ChoreId): Unit = resolve(id) { due, at ->
        Resolution.Skip(dueDate = due, at = at, kind = Resolution.Skip.Kind.Manual)
    }

    override suspend fun archive(id: ChoreId): Unit = store.transact { edit ->
        val (record, _) = edit.caughtUp(id) ?: return@transact
        edit.update(record.chore.copy(archivedAt = clock.instant()))
    }

    override suspend fun restore(id: ChoreId): Unit = store.transact { edit ->
        val record = edit.book().record(id) ?: return@transact
        // Re-anchored to today: a chore archived for a year should not come back a year
        // overdue, and its history stays intact behind the new anchor.
        edit.update(record.chore.copy(archivedAt = null, anchoredOn = LocalDate.now(clock)))
    }

    override suspend fun delete(id: ChoreId): Unit = store.transact { edit -> edit.delete(id) }

    private suspend fun resolve(id: ChoreId, resolution: (LocalDate, java.time.Instant) -> Resolution): Unit =
        store.transact { edit ->
            val (record, seenThrough) = edit.caughtUp(id) ?: return@transact
            val due = record.outstanding(seenThrough).dueDate
            edit.append(id, listOf(resolution(due, clock.instant())))
        }

    /**
     * Writes any lapses the chore has accrued and returns it as it then stands, so callers
     * act on a chore that is already up to date. Null if there is no such chore.
     */
    private suspend fun ChoreEdit.caughtUp(id: ChoreId): Pair<ChoreRecord, LocalDate?>? {
        val book = book()
        val record = book.record(id) ?: return null
        val displaced = record.catchUp(book.seenThrough).displaced
        if (displaced.isEmpty()) return record to book.seenThrough
        append(id, displaced)
        val refreshed = book()
        return (refreshed.record(id) ?: return null) to refreshed.seenThrough
    }

    private fun ChoreBook.record(id: ChoreId) = chores.firstOrNull { it.chore.id == id }

    private fun ChoreBook.active() = chores.filterNot { it.chore.isArchived }

    private fun ChoreRecord.catchUp(seenThrough: LocalDate?) =
        catchUp(chore, lastResolution, seenThrough, clock)

    private fun ChoreRecord.outstanding(seenThrough: LocalDate?) = catchUp(seenThrough).outstanding

    private fun ChoreRecord.dueChore(seenThrough: LocalDate?) = DueChore(chore, outstanding(seenThrough))

    private companion object {
        val byDueDateThenName = compareBy<DueChore>({ it.dueDate }, { it.chore.name.lowercase() })
    }
}
