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
     * moves it, or cancels it if reminders are switched off.
     *
     * Idempotent — two calls at one instant leave the same schedule — but not harmless:
     * it replaces whatever is pending, so a digest that fell due this morning and has not
     * run yet is thrown away and the next one aimed at tomorrow. Call it where the
     * reminder time or the chain itself changed, which is where losing that run is either
     * correct or already lost; a write elsewhere in the app changes nothing this reads.
     */
    suspend fun sync()
}

/** Posts the daily digest. Returns false if the system refused it — typically an ungranted
 *  `POST_NOTIFICATIONS` — so the caller can avoid recording occurrences as seen. */
interface DigestNotifier {

    suspend fun post(due: List<at.woergoetter.chorely.domain.DueChore>): Boolean
}
