package at.woergoetter.chorely.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import at.woergoetter.chorely.MainActivity
import at.woergoetter.chorely.R
import at.woergoetter.chorely.domain.DueChore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One notification listing everything due, never one per chore.
 *
 * Reports whether the post actually happened: on API 33+ `POST_NOTIFICATIONS` is a runtime
 * permission, and a silently missing notification is almost always an ungranted one. The
 * caller needs to know, because "the user has been shown this" is what licenses catch-up to
 * record lapses.
 */
@Singleton
class SystemDigestNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : DigestNotifier {

    override suspend fun post(due: List<DueChore>): Boolean {
        if (due.isEmpty()) return false
        // Inline rather than behind a helper: lint only recognises the guard when the check
        // and the notify() it protects sit in the same function.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        ensureChannel()
        val today = LocalDate.now(clock)
        val overdue = due.count { it.dueDate < today }

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.resources.getQuantityString(R.plurals.digest_title, due.size, due.size))
            .setContentText(due.joinToString(", ") { it.chore.name })
            .setStyle(inboxStyle(due, overdue))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        return runCatching { NotificationManagerCompat.from(context).notify(ID, notification) }.isSuccess
    }

    private fun inboxStyle(due: List<DueChore>, overdue: Int): NotificationCompat.InboxStyle {
        var style = NotificationCompat.InboxStyle()
        due.take(MAX_LINES).forEach { style = style.addLine(it.chore.name) }
        if (overdue > 0) style = style.setSummaryText(context.getString(R.string.digest_overdue, overdue))
        return style
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.digest_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.digest_channel_description) },
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL = "daily-digest"
        const val ID = 1
        const val MAX_LINES = 6
    }
}
