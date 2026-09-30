package at.woergoetter.chorely.reminder

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import at.woergoetter.chorely.domain.ReminderSettings
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the digest as chained one-shot work rather than periodic work.
 *
 * Periodic work has a 15-minute floor and is deliberately inexact under Doze, so it cannot
 * be aimed at a chosen time of day; a one-shot with an initial delay can, and each run
 * schedules the next. Exact alarms would be more precise, but they need a Play-restricted
 * permission and a chore is due on a *day*, never at a minute — a digest that slips by half
 * an hour costs nothing.
 */
@Singleton
class WorkManagerReminders @Inject constructor(
    private val workManager: WorkManager,
    private val settings: ReminderSettings,
    private val clock: Clock,
) : Reminders {

    override suspend fun sync() {
        val time = settings.reminderTime().first()
        if (time == null) {
            workManager.cancelUniqueWork(DailyDigestWorker.NAME)
            return
        }
        // A digest already owed runs now rather than being re-aimed at tomorrow: one waiting
        // out a retry's backoff, or one past its time but held back by Doze (see isOwedDigest
        // for the one case that is re-aimed instead). A clock correction, a zone change or a
        // reboot arriving then would otherwise throw that day's away. Not the running digest's
        // own closing sync, which sees itself RUNNING.
        // A replaced retry starts its attempts afresh; the broadcasts that cause it are rare
        // enough that the bound still holds in practice.
        val pending = workManager.getWorkInfosForUniqueWorkFlow(DailyDigestWorker.NAME).first()
            .singleOrNull { it.state == WorkInfo.State.ENQUEUED }
        val owed = pending != null && (
            pending.runAttemptCount > 0 ||
                isOwedDigest(Instant.ofEpochMilli(pending.nextScheduleTimeMillis), time, clock)
            )
        workManager.enqueueUniqueWork(
            DailyDigestWorker.NAME,
            // REPLACE, so changing the reminder time moves the pending digest rather than
            // leaving yesterday's request to fire at the old time.
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DailyDigestWorker>()
                .setInitialDelay(if (owed) Duration.ZERO else nextDigestDelay(time, clock))
                .setBackoffCriteria(BackoffPolicy.LINEAR, DailyDigestWorker.RETRY_BACKOFF)
                .addTag(DailyDigestWorker.NAME)
                .build(),
        )
    }

}
