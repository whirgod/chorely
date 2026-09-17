package at.woergoetter.chorely.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ChoreDao {

    @Query("SELECT * FROM chores")
    fun observeChores(): Flow<List<ChoreEntity>>

    @Query("SELECT * FROM chores")
    suspend fun chores(): List<ChoreEntity>

    /**
     * The newest resolution per chore — "newest" by due date, then by the moment it was
     * recorded, so a completion entered for an older occurrence cannot displace a later one.
     */
    @Query(
        """
        SELECT r.* FROM resolutions r
        JOIN (
            SELECT choreId, MAX(dueDate) AS dueDate FROM resolutions GROUP BY choreId
        ) newest ON newest.choreId = r.choreId AND newest.dueDate = r.dueDate
        GROUP BY r.choreId
        HAVING r.resolvedAt = MAX(r.resolvedAt)
        """,
    )
    fun observeLatestResolutions(): Flow<List<ResolutionEntity>>

    @Query(
        """
        SELECT r.* FROM resolutions r
        JOIN (
            SELECT choreId, MAX(dueDate) AS dueDate FROM resolutions GROUP BY choreId
        ) newest ON newest.choreId = r.choreId AND newest.dueDate = r.dueDate
        GROUP BY r.choreId
        HAVING r.resolvedAt = MAX(r.resolvedAt)
        """,
    )
    suspend fun latestResolutions(): List<ResolutionEntity>

    @Query("SELECT * FROM resolutions WHERE choreId = :choreId ORDER BY dueDate DESC, resolvedAt DESC")
    fun observeHistory(choreId: Long): Flow<List<ResolutionEntity>>

    @Insert
    suspend fun insert(chore: ChoreEntity): Long

    @Update
    suspend fun update(chore: ChoreEntity)

    @Insert
    suspend fun insertResolutions(resolutions: List<ResolutionEntity>)

    @Query("DELETE FROM chores WHERE id = :id")
    suspend fun deleteChore(id: Long)

    @Query("SELECT * FROM app_state WHERE id = 0")
    fun observeAppState(): Flow<AppStateEntity?>

    @Query("SELECT * FROM app_state WHERE id = 0")
    suspend fun appState(): AppStateEntity?

    @Upsert
    suspend fun upsertAppState(state: AppStateEntity)
}
