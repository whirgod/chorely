package at.woergoetter.chorely.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Rebuilds the reminder schedule after a reboot, and after the device's timezone or clock is
 * changed.
 *
 * The second is load-bearing where the first is not: the pending digest's delay was worked out
 * in the zone of the moment it was enqueued, so an 08:00 reminder set in Vienna would fire at
 * 02:00 in New York. The domain follows the zone on its own (see `DeviceClock`); the schedule
 * has to be re-aimed.
 *
 * Derives the schedule again from Room rather than restoring anything, and delegates to a
 * worker because a receiver has no business doing database I/O in its ten-second window.
 *
 * After a reboot it is belt-and-braces: WorkManager persists its own requests and reschedules
 * them itself, and nothing here uses AlarmManager, the thing that really loses its schedule.
 * It costs nothing there either, since `sync()` runs a digest that is already owed rather than
 * re-aiming it.
 *
 * One sync at a time, with KEEP: a zone change arriving while an earlier sync is still pending
 * — seconds after a boot, or during a retry's backoff — is dropped, and that sync may already
 * have read the old zone. Rare enough to accept; the next day's digest re-aims itself.
 *
 * Not a Hilt entry point: it injects nothing, and WorkManager is reached through its own
 * singleton rather than through the graph.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESYNC_ON) return
        // Unique with KEEP, so a second trigger while one sync is still retrying adds nothing.
        WorkManager.getInstance(context).enqueueUniqueWork(
            ReminderSyncWorker.NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReminderSyncWorker>()
                .setBackoffCriteria(BackoffPolicy.LINEAR, DailyDigestWorker.RETRY_BACKOFF)
                .build(),
        )
    }

    private companion object {
        /** All three are exempt from the background limits on implicit broadcasts. */
        val RESYNC_ON = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )
    }
}
