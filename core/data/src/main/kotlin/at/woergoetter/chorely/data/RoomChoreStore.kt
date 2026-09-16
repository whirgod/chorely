package at.woergoetter.chorely.data

import androidx.room.withTransaction
import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreBook
import at.woergoetter.chorely.domain.ChoreDraft
import at.woergoetter.chorely.domain.ChoreEdit
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.ChoreRecord
import at.woergoetter.chorely.domain.ChoreStore
import at.woergoetter.chorely.domain.Resolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The production adapter at the [ChoreStore] seam. `FakeChoreStore` in `:core:domain`'s
 * tests is the other one.
 *
 * Holds no decisions: it stores what it is told and reads back what is there. Everything
 * that could be got *wrong* about due dates is on the far side of this interface.
 */
internal class RoomChoreStore(
    private val database: ChorelyDatabase,
) : ChoreStore {

    private val dao = database.choreDao()

    override fun book(): Flow<ChoreBook> =
        combine(dao.observeChores(), dao.observeLatestResolutions(), dao.observeAppState()) { chores, latest, state ->
            book(chores, latest, state)
        }

    override fun history(id: ChoreId): Flow<List<Resolution>> =
        dao.observeHistory(id.value).map { rows -> rows.map { it.toDomain() } }

    override suspend fun <T> transact(block: suspend (ChoreEdit) -> T): T =
        database.withTransaction { block(RoomEdit()) }

    private inner class RoomEdit : ChoreEdit {

        override suspend fun book(): ChoreBook =
            book(dao.chores(), dao.latestResolutions(), dao.appState())

        override suspend fun insert(draft: ChoreDraft, anchoredOn: LocalDate): ChoreId {
            val rowId = dao.insert(
                Chore(ChoreId(0), draft.name, draft.recurrence, anchoredOn).toEntity().copy(id = 0),
            )
            return ChoreId(rowId)
        }

        override suspend fun update(chore: Chore) = dao.update(chore.toEntity())

        override suspend fun append(id: ChoreId, resolutions: List<Resolution>) =
            dao.insertResolutions(resolutions.map { it.toEntity(id) })

        override suspend fun delete(id: ChoreId) = dao.deleteChore(id.value)

        override suspend fun markSeen(through: LocalDate) {
            val current = dao.appState()
            dao.upsertAppState(
                current?.copy(seenThrough = through.toEpochDay())
                    ?: AppStateEntity(seenThrough = through.toEpochDay(), reminderMinuteOfDay = null),
            )
        }
    }

    private fun book(
        chores: List<ChoreEntity>,
        latest: List<ResolutionEntity>,
        state: AppStateEntity?,
    ): ChoreBook {
        val byChore = latest.associateBy { it.choreId }
        return ChoreBook(
            chores = chores.map { entity ->
                ChoreRecord(entity.toDomain(), byChore[entity.id]?.toDomain())
            },
            seenThrough = state?.seenThrough?.let(LocalDate::ofEpochDay),
        )
    }
}
