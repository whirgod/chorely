package at.woergoetter.chorely.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import at.woergoetter.chorely.domain.Chores
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

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
 */
@HiltWorker
class DailyDigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val chores: Chores,
    private val notifier: DigestNotifier,
    private val reminders: Reminders,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val due = chores.due()
        val posted = due.isNotEmpty() && notifier.post(due)
        if (posted) chores.markSeen()
        reminders.sync()
        return Result.success()
    }

    companion object {
        const val NAME = "daily-digest"
    }
}
