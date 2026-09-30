package at.woergoetter.chorely.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Rebuilds the reminder schedule after a reboot.
 *
 * Derives the schedule again from Room rather than restoring anything, and delegates to a
 * worker because a receiver has no business doing database I/O in its ten-second window.
 *
 * It is belt-and-braces, not load-bearing: the digest is WorkManager work, and WorkManager
 * persists its own requests and reschedules them itself after a reboot — nothing here uses
 * AlarmManager, which is the thing that really does lose its schedule. So this costs a
 * `sync()`, and `sync()` re-enqueues with REPLACE: a reboot after the reminder time, with
 * the digest restored but not yet run, discards it. Whether the receiver earns that is an
 * open question; see the sync bullet in AGENTS.md.
 *
 * Not a Hilt entry point: it injects nothing, and WorkManager is reached through its own
 * singleton rather than through the graph.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<ReminderSyncWorker>().build())
    }
}
