package at.woergoetter.chorely.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Re-derives the reminder schedule from the database. Used after a reboot. */
@HiltWorker
class ReminderSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val reminders: Reminders,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        reminders.sync()
        return Result.success()
    }
}
