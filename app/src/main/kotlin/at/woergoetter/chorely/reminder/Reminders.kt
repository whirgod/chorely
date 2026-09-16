package at.woergoetter.chorely.reminder

/**
 * Keeps the daily digest scheduled to match what is in the database.
 *
 * The interface is deliberately this small. Callers — the settings screen, the boot
 * receiver, the worker rescheduling itself — never say *when*: the reminder time lives in
 * Room, and anything AlarmManager or WorkManager holds is a cache derived from it. That is
 * what makes "rebuild the schedule from the database alone" a single call rather than a
 * procedure every call site has to get right.
 */
interface Reminders {

    /**
     * Brings the scheduled digest in line with the stored reminder time: schedules it,
     * moves it, or cancels it if reminders are switched off. Idempotent, and safe to call
     * from any of the several places that discover the schedule might be stale.
     */
    suspend fun sync()
}

/** Posts the daily digest. Returns false if the system refused it — typically an ungranted
 *  `POST_NOTIFICATIONS` — so the caller can avoid recording occurrences as seen. */
interface DigestNotifier {

    suspend fun post(due: List<at.woergoetter.chorely.domain.DueChore>): Boolean
}
