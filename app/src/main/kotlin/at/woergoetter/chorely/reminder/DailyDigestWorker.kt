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
import java.time.Duration

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
 * - A failure retries this run rather than failing it: a failed run would end the chain, and
 *   nothing short of a reboot or the user changing the reminder time rebuilds it. Retrying
 *   keeps today's digest too, where syncing past the failure would aim straight at tomorrow,
 *   and it survives a database that is down for `sync()` as well, since the retry is
 *   WorkManager's own and reads nothing. A retry after a post re-posts under the same
 *   notification id, silently: the notifier alerts only once.
 * - Retries are bounded. On the last attempt a failure gives today up: it tries once more to
 *   aim the chain at tomorrow and fails the run, so something that throws every time shows
 *   up as failed work instead of re-posting all night.
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
        if (runAttemptCount + 1 < MAX_ATTEMPTS) {
            Log.w(TAG, "digest run failed, retrying", failure)
            Result.retry()
        } else {
            Log.e(TAG, "digest run failed $MAX_ATTEMPTS times, giving today up", failure)
            // Best effort: if this throws too, the chain is gone until a reboot or a settings
            // change, and there is nothing left to try.
            runCatching { reminders.sync() }.onFailure { failure.addSuppressed(it) }
            Result.failure()
        }
    }

    companion object {
        const val NAME = "daily-digest"
        private const val TAG = "DailyDigestWorker"

        /** With [RETRY_BACKOFF] linear, the last attempt runs 50 minutes after the first. */
        const val MAX_ATTEMPTS = 5

        /** Linear rather than WorkManager's default doubling, so a retry stays near the chosen time. */
        val RETRY_BACKOFF: Duration = Duration.ofMinutes(5)
    }
}
