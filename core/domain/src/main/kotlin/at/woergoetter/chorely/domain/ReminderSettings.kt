package at.woergoetter.chorely.domain

import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

/**
 * When the daily digest goes out.
 *
 * One time for the whole app, not one per chore: a chore is due on a *day*, never at a
 * moment, and per-chore times are ruled out in BACKLOG.md.
 *
 * Declared here and implemented in `:core:data` so the app talks to one module's language.
 */
interface ReminderSettings {

    /** The local time of the daily digest, or null when reminders are switched off. */
    fun reminderTime(): Flow<LocalTime?>

    suspend fun setReminderTime(time: LocalTime?)
}
