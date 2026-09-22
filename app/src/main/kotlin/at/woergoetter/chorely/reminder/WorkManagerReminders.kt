package at.woergoetter.chorely.reminder

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import at.woergoetter.chorely.domain.ReminderSettings
import kotlinx.coroutines.flow.first
import java.time.Clock
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
        workManager.enqueueUniqueWork(
            DailyDigestWorker.NAME,
            // REPLACE, so changing the reminder time moves the pending digest rather than
            // leaving yesterday's request to fire at the old time.
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DailyDigestWorker>()
                .setInitialDelay(nextDigestDelay(time, clock))
                .addTag(DailyDigestWorker.NAME)
                .build(),
        )
    }

}
