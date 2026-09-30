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
     * Idempotent — two calls at one instant leave the same schedule — but not free: it
     * replaces whatever is pending. A digest already owed — past its time but not yet run, or
     * retrying — is replaced by one that runs at once, so a clock correction or a reboot
     * cannot throw today's away; one not yet due is re-aimed. Call it where the reminder time,
     * the device's time or zone, or the chain itself changed; a write elsewhere in the app
     * changes nothing this reads.
     */
    suspend fun sync()
}

/** Posts the daily digest. Returns false if the system refused it — typically an ungranted
 *  `POST_NOTIFICATIONS` — so the caller can avoid recording occurrences as seen. */
interface DigestNotifier {

    suspend fun post(due: List<at.woergoetter.chorely.domain.DueChore>): Boolean
}
