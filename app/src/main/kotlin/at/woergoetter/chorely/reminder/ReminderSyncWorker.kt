package at.woergoetter.chorely.reminder

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/** Re-derives the reminder schedule from the database. Used after a reboot. */
@HiltWorker
class ReminderSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val reminders: Reminders,
) : CoroutineWorker(context, parameters) {

    /** Retried on a failure, as the digest is: a database briefly unavailable at boot is not fatal. */
    override suspend fun doWork(): Result = try {
        reminders.sync()
        Result.success()
    } catch (stopped: CancellationException) {
        throw stopped
    } catch (failure: Exception) {
        Log.w("ReminderSyncWorker", "sync after boot failed, retrying", failure)
        Result.retry()
    }
}
