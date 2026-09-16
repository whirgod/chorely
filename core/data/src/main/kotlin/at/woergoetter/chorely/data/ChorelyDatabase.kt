package at.woergoetter.chorely.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
