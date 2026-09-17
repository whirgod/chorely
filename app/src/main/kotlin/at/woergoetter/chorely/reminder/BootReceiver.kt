package at.woergoetter.chorely.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Rebuilds the reminder schedule after a reboot.
 *
 * Scheduled work and alarms do not survive one, and the database does — so this asks for the
 * schedule to be derived again rather than restoring anything. It delegates to a worker
 * because a receiver has no business doing database I/O in its ten-second window.
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
