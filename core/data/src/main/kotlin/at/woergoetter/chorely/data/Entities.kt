package at.woergoetter.chorely.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chores")
internal data class ChoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Discriminator for the [RecurrenceColumns] below; see `Mapping.kt`. */
    val recurrenceKind: String,
    /** Comma-separated ISO day numbers for a weekday recurrence, else null. */
    val weekdays: String?,
    /** ISO-8601 period for a period recurrence, else null. */
    val period: String?,
    val anchoredOn: Long,
    val archivedAt: Long?,
)

/**
 * Resolved occurrences. Append-only: there is no update path, and the only delete is the
 * cascade from deleting the chore itself.
 */
@Entity(
    tableName = "resolutions",
    foreignKeys = [
        ForeignKey(
            entity = ChoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["choreId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("choreId"), Index(value = ["choreId", "dueDate"])],
)
internal data class ResolutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val choreId: Long,
    val dueDate: Long,
    val resolvedAt: Long,
    /** "completion", "skip_manual" or "skip_displaced". */
    val kind: String,
)

/** Single-row table for state that belongs to the user rather than to any one chore. */
@Entity(tableName = "app_state")
internal data class AppStateEntity(
    @PrimaryKey val id: Int = SINGLETON,
    @ColumnInfo(name = "seen_through") val seenThrough: Long?,
    /** Minutes past local midnight for the daily digest; null means reminders are off. */
    @ColumnInfo(name = "reminder_minute_of_day") val reminderMinuteOfDay: Int?,
) {
    companion object {
        const val SINGLETON = 0
    }
}
