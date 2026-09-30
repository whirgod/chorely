package at.woergoetter.chorely.reminder

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/** Re-derives the reminder schedule from the database, for [BootReceiver]. */
@HiltWorker
class ReminderSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val reminders: Reminders,
) : CoroutineWorker(context, parameters) {

    /**
     * Retried on a failure, with the digest's backoff and bound (see [BootReceiver]): a database
     * briefly unavailable is not fatal, and an attempt that succeeds hours late would
     * replace a digest pending for today.
     */
    override suspend fun doWork(): Result = try {
        reminders.sync()
        Result.success()
    } catch (stopped: CancellationException) {
        throw stopped
    } catch (failure: Exception) {
        if (runAttemptCount + 1 < DailyDigestWorker.MAX_ATTEMPTS) {
            Log.w(TAG, "reminder sync failed, retrying", failure)
            Result.retry()
        } else {
            Log.e(TAG, "reminder sync failed ${DailyDigestWorker.MAX_ATTEMPTS} times", failure)
            Result.failure()
        }
    }

    companion object {
        const val NAME = "reminder-sync"
        private const val TAG = "ReminderSyncWorker"
    }
}
