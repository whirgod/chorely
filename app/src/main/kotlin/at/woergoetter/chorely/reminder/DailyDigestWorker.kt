package at.woergoetter.chorely.reminder

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import at.woergoetter.chorely.domain.Chores
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Posts the daily digest and, having posted it, records that the user has been shown what
 * is due.
 *
 * The order matters and is the whole reason this worker has any logic at all:
 *
 * - Nothing due means no notification. A silent day is the point, not an oversight.
 * - [Chores.markSeen] runs only after a notification actually reached the user. If the
 *   system refused it — `POST_NOTIFICATIONS` not granted — recording the occurrences as
 *   seen would let catch-up log lapses for chores the user was never told about.
 * - The schedule is re-synced at the end, because WorkManager's one-shot work is consumed
 *   by running and the next day's digest does not otherwise exist.
 * - Any failure retries this run instead, rather than failing it: a failed run would end the
 *   chain, and nothing short of a reboot or the user changing the reminder time rebuilds it.
 *   Retrying keeps today's digest too, where syncing past the failure would aim straight at
 *   tomorrow — and it survives a database that is down for `sync()` as well, since the
 *   retry is WorkManager's own and reads nothing. A retry after a post re-posts under the
 *   same notification id, which replaces it rather than adding a second.
 */
@HiltWorker
class DailyDigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val chores: Chores,
    private val notifier: DigestNotifier,
    private val reminders: Reminders,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = try {
        val due = chores.due()
        val posted = due.isNotEmpty() && notifier.post(due)
        if (posted) chores.markSeen()
        reminders.sync()
        Result.success()
    } catch (stopped: CancellationException) {
        // The system stopping the run, not a failure: WorkManager reschedules it itself.
        throw stopped
    } catch (failure: Exception) {
        Log.w(TAG, "digest run failed, retrying", failure)
        Result.retry()
    }

    companion object {
        const val NAME = "daily-digest"
        private const val TAG = "DailyDigestWorker"
    }
}
