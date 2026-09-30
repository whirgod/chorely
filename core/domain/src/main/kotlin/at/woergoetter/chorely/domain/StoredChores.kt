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
    private val deviceClock: Clock,
) : Chores {

    /**
     * The device's clock, read once: see [Clock.pinned]. Every operation takes one and hands it
     * down, and nothing reads [deviceClock] directly. The agenda and detail flows take a fresh
     * one per emission, so they still move on with time.
     */
    private fun pinned(): Clock = deviceClock.pinned()

    override fun agenda(): Flow<Agenda> = store.book().map { book ->
        val clock = pinned()
        val today = LocalDate.now(clock)
        val due = book.active()
            .map { it.dueChore(book.seenThrough, clock) }
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
            val clock = pinned()
            // Derived from the history's head rather than the book's newest resolution, which
            // is the same row: the two are separate flows, and a write re-emits them one at a
            // time, so the book's copy can lag the history it is shown next to.
            val current = record.copy(lastResolution = history.firstOrNull())
            ChoreDetail(
                chore = current.chore,
                outstanding = current.outstanding(book.seenThrough, clock),
                history = history,
            )
        }

    override fun archived(): Flow<List<Chore>> = store.book().map { book ->
        book.chores.map { it.chore }.filter { it.isArchived }.sortedByDescending { it.archivedAt }
    }

    override suspend fun due(): DueToday {
        val clock = pinned()
        val today = LocalDate.now(clock)
        val book = store.transact { it.book() }
        val chores = book.active()
            .map { it.dueChore(book.seenThrough, clock) }
            .filter { it.dueDate <= today }
            .sortedWith(byDueDateThenName)
        return DueToday(today, chores)
    }

    override suspend fun markSeen(through: LocalDate?): Unit = store.transact { edit ->
        val clock = pinned()
        val book = edit.book()
        book.active().forEach { record ->
            val displaced = record.catchUp(book.seenThrough, clock).displaced
            if (displaced.isNotEmpty()) edit.append(record.chore.id, displaced)
        }
        // Never backwards: a move to a zone further west makes "today" earlier, and the
        // record of what the user has been shown must not un-show anything.
        val shown = through ?: LocalDate.now(clock)
        edit.markSeen(book.seenThrough?.let { maxOf(it, shown) } ?: shown)
    }

    override suspend fun add(draft: ChoreDraft): ChoreId = store.transact { edit ->
        edit.insert(draft, anchoredOn = anchorFor(draft.recurrence, LocalDate.now(pinned())))
    }

    override suspend fun edit(id: ChoreId, draft: ChoreDraft): Unit = store.transact { edit ->
        val clock = pinned()
        val (record, seenThrough) = edit.activeCaughtUp(id, clock) ?: return@transact
        val previous = record.outstanding(seenThrough, clock)
        // The new rule taken up where the old one started, so that `recomputed` is what the
        // rule gives on its own rather than what the old anchor happened to allow.
        val edited = record.chore.copy(
            name = draft.name,
            recurrence = draft.recurrence,
            anchoredOn = anchorFor(draft.recurrence, record.chore.anchoredOn),
        )
        val recomputed = catchUp(edited, record.lastResolution, seenThrough, clock).outstanding
        val target = retarget(previous, recomputed, LocalDate.now(clock))

        // Re-anchoring is what makes the target stick, and it sticks exactly: an anchor is a
        // due date, so the derivation returns `target` itself even when the new rule would
        // never have placed an occurrence there. Every resolution in play is due strictly
        // before the occurrence it produced, and `target` never falls after `previous`, so
        // none of them survives the new anchor either.
        //
        // The one state this cannot express: a chore whose newest resolution is due *after*
        // its own outstanding occurrence — reachable only by resolving the same chore twice
        // in a day under a completion-anchored rule, which records a resolution for an
        // occurrence a period ahead. No anchor can name `target` and supersede that
        // resolution at once, so the new rule steps from the resolution instead.
        edit.update(edited.copy(anchoredOn = target.dueDate))
    }

    override suspend fun complete(id: ChoreId): Unit = resolve(id) { due, at ->
        Resolution.Completion(dueDate = due, at = at)
    }

    override suspend fun skip(id: ChoreId): Unit = resolve(id) { due, at ->
        Resolution.Skip(dueDate = due, at = at, kind = Resolution.Skip.Kind.Manual)
    }

    override suspend fun archive(id: ChoreId): Unit = store.transact { edit ->
        // Archiving twice keeps the first moment, which is what the archive sorts and shows:
        // activeCaughtUp refuses an archived chore.
        val clock = pinned()
        val (record, _) = edit.activeCaughtUp(id, clock) ?: return@transact
        edit.update(record.chore.copy(archivedAt = clock.instant()))
    }

    override suspend fun restore(id: ChoreId): Unit = store.transact { edit ->
        val record = edit.book().record(id) ?: return@transact
        // Only an archived chore: re-anchoring an active one would forgive whatever it has
        // outstanding, and a second tap on Restore lands on a chore the first one revived.
        if (!record.chore.isArchived) return@transact
        // Re-anchored to today: a chore archived for a year should not come back a year
        // overdue, and its history stays intact behind the new anchor.
        val anchoredOn = anchorFor(record.chore.recurrence, LocalDate.now(pinned()))
        edit.update(record.chore.copy(archivedAt = null, anchoredOn = anchoredOn))
    }

    override suspend fun delete(id: ChoreId): Unit = store.transact { edit ->
        // Only from the archive: deleting is the way out of it, and an active chore's history
        // is not something a stray caller may discard in one call.
        val record = edit.book().record(id) ?: return@transact
        if (record.chore.isArchived) edit.delete(id)
    }

    private suspend fun resolve(id: ChoreId, resolution: (LocalDate, java.time.Instant) -> Resolution): Unit =
        store.transact { edit ->
            val clock = pinned()
            val (record, seenThrough) = edit.activeCaughtUp(id, clock) ?: return@transact
            val due = record.outstanding(seenThrough, clock).dueDate
            edit.append(id, listOf(resolution(due, clock.instant())))
        }

    /**
     * Writes any lapses the chore has accrued and returns it as it then stands, so callers
     * act on a chore that is already up to date. Null if there is no such chore, or if it is
     * archived — which is what makes every write through here refuse an archived chore: its
     * occurrences stopped falling due when it was archived, and catching it up would write
     * months of lapses into a history that cannot lose them.
     */
    private suspend fun ChoreEdit.activeCaughtUp(id: ChoreId, clock: Clock): Pair<ChoreRecord, LocalDate?>? {
        val book = book()
        val record = book.record(id)?.takeUnless { it.chore.isArchived } ?: return null
        val displaced = record.catchUp(book.seenThrough, clock).displaced
        if (displaced.isEmpty()) return record to book.seenThrough
        append(id, displaced)
        val refreshed = book()
        return (refreshed.record(id) ?: return null) to refreshed.seenThrough
    }

    private fun ChoreBook.record(id: ChoreId) = chores.firstOrNull { it.chore.id == id }

    private fun ChoreBook.active() = chores.filterNot { it.chore.isArchived }

    private fun ChoreRecord.catchUp(seenThrough: LocalDate?, clock: Clock) =
        catchUp(chore, lastResolution, seenThrough, clock)

    private fun ChoreRecord.outstanding(seenThrough: LocalDate?, clock: Clock) =
        catchUp(seenThrough, clock).outstanding

    private fun ChoreRecord.dueChore(seenThrough: LocalDate?, clock: Clock) =
        DueChore(chore, outstanding(seenThrough, clock))

    private companion object {
        val byDueDateThenName = compareBy<DueChore>({ it.dueDate }, { it.chore.name.lowercase() })
    }
}
