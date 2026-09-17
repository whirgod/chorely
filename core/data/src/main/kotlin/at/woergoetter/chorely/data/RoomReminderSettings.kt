package at.woergoetter.chorely.data

import at.woergoetter.chorely.domain.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

/**
 * Reminder settings live in Room rather than in DataStore so that the database really is
 * the single source of truth for scheduling: anything AlarmManager or WorkManager holds is
 * a cache that can be rebuilt from here alone, including after a reboot.
 */
internal class RoomReminderSettings(
    private val database: ChorelyDatabase,
) : ReminderSettings {

    private val dao = database.choreDao()

    override fun reminderTime(): Flow<LocalTime?> = dao.observeAppState().map { state ->
        state?.reminderMinuteOfDay?.let { LocalTime.ofSecondOfDay(it * 60L) }
    }

    override suspend fun setReminderTime(time: LocalTime?) {
        val minutes = time?.let { it.hour * 60 + it.minute }
        database.patchAppState { it.copy(reminderMinuteOfDay = minutes) }
    }
}
