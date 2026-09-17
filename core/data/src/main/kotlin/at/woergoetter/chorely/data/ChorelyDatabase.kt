package at.woergoetter.chorely.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction

@Database(
    entities = [ChoreEntity::class, ResolutionEntity::class, AppStateEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class ChorelyDatabase : RoomDatabase() {

    abstract fun choreDao(): ChoreDao

    companion object {
        const val NAME = "chorely.db"

        fun open(context: Context): ChorelyDatabase =
            Room.databaseBuilder(context, ChorelyDatabase::class.java, NAME)
                // Foreign keys are what make deleting a chore take its history with it, and
                // nothing else: resolutions are otherwise never deleted.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}

/**
 * Read the `app_state` singleton, patch it, and write it back in one transaction.
 *
 * Every writer of that row rewrites all of it, so an unprotected read-then-write reverts
 * whatever another writer committed in between — a reminder-time change landing on a stale
 * row would silently undo `seen_through`, and with it the guard that lets catch-up record
 * lapses. Nesting inside an outer [withTransaction] is harmless, so callers already inside
 * one may use this too.
 */
internal suspend fun ChorelyDatabase.patchAppState(patch: (AppStateEntity) -> AppStateEntity) {
    withTransaction {
        val dao = choreDao()
        val current = dao.appState() ?: AppStateEntity(seenThrough = null, reminderMinuteOfDay = null)
        dao.upsertAppState(patch(current))
    }
}
