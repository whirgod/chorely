package at.woergoetter.chorely.domain

import java.time.Clock
import java.time.LocalDate

/**
 * What a chore's stream looks like once it has been brought up to the present: the
 * occurrences the rule says have since been displaced, and the one outstanding now.
 */
data class CatchUp(
    /** Auto-skips to append to the history, oldest first. Usually empty. */
    val displaced: List<Resolution.Skip>,
    /** The single outstanding occurrence. Derived, never stored. */
    val outstanding: Occurrence,
)

/**
 * Brings one chore's occurrence stream up to the present.
 *
 * This is the whole of Chorely's due-date behaviour, and the only place any of it lives.
 * It is pure and total: same inputs, same answer, no I/O, no ambient time. Callers hand it
 * a [Clock] — which carries both the instant and the zone, so tests can advance time and
 * change timezone — and everything else follows.
 *
 * What it decides, so that nothing else has to:
 *
 * - **The first occurrence.** With no resolution in play, the chore falls due on the first
 *   day on or after [Chore.anchoredOn] that its recurrence allows.
 * - **Superseded history.** A resolution older than [Chore.anchoredOn] was made under a
 *   rule that no longer applies; it stays in the history but does not move the series.
 * - **Calendar anchoring.** After a resolution, the next due date is the next day on the
 *   calendar grid — a late completion does not move it.
 * - **Completion anchoring.** After a completion, the next due date is the period measured
 *   from the day the chore was *done*, so the whole future series shifts. After a manual
 *   skip it is measured from the due date instead, so tapping "skip" at 2am rather than 2pm
 *   cannot change when the chore comes back.
 * - **Auto-skip.** A calendar-anchored occurrence is displaced only once its successor has
 *   actually fallen due, and only if the user has already been shown it ([seenThrough]).
 *   Without that second guard, a daily chore's occurrence could appear and vanish unseen,
 *   recorded as a lapse the user never had a chance to act on.
 * - **Completion-anchored chores never auto-skip**, because nothing arrives to displace an
 *   outstanding occurrence; it simply waits, however long that takes.
 *
 * Idempotent in the sense that matters: feed the returned [CatchUp.displaced] back in as
 * history and a second call returns no further displacements.
 *
 * @param lastResolution the newest stored resolution for this chore, or null if it has
 *   never been resolved.
 * @param seenThrough the latest day on which the user was shown what was due — by the daily
 *   digest or by opening the app. Null means they never have been, and nothing is displaced.
 */
fun catchUp(
    chore: Chore,
    lastResolution: Resolution?,
    seenThrough: LocalDate?,
    clock: Clock,
): CatchUp {
    val today = LocalDate.now(clock)
    val inPlay = lastResolution?.takeIf { it.dueDate >= chore.anchoredOn }
    var due = chore.nextDueDate(inPlay, clock)

    val recurrence = chore.recurrence
    if (recurrence !is Recurrence.OnWeekdays || seenThrough == null) {
        return CatchUp(displaced = emptyList(), outstanding = Occurrence(chore.id, due))
    }

    val now = clock.instant()
    val displaced = mutableListOf<Resolution.Skip>()
    while (due <= seenThrough) {
        val successor = recurrence.nextDueAfter(due)
        if (successor > today) break
        displaced += Resolution.Skip(dueDate = due, at = now, kind = Resolution.Skip.Kind.Displaced)
        due = successor
    }
    return CatchUp(displaced = displaced, outstanding = Occurrence(chore.id, due))
}

/**
 * Where an edited recurrence puts the outstanding occurrence.
 *
 * [recomputed] is what the new rule gives on its own. A rule change must never make a
 * neglected chore look clean, so if the chore was already overdue the result stays at least
 * as overdue as it was. Moving it *earlier* is allowed — that is the user tightening the
 * rule, not the app forgiving a lapse.
 */
fun retarget(previous: Occurrence, recomputed: Occurrence, today: LocalDate): Occurrence =
    if (previous.isOverdue(today)) previous.copy(dueDate = minOf(recomputed.dueDate, previous.dueDate))
    else recomputed

private fun Chore.nextDueDate(lastResolution: Resolution?, clock: Clock): LocalDate =
    when (recurrence) {
        is Recurrence.OnWeekdays -> when (lastResolution) {
            null -> recurrence.firstDueOnOrAfter(anchoredOn)
            else -> recurrence.nextDueAfter(lastResolution.dueDate)
        }

        is Recurrence.Every -> when (lastResolution) {
            null -> anchoredOn
            // Measured from the day it was actually done: that is what "elapsed since last
            // done" means, and it is why doing it late shifts the whole series.
            is Resolution.Completion ->
                lastResolution.at.atZone(clock.zone).toLocalDate().plus(recurrence.period)
            // Measured from the due date: a skip is a decision about an occurrence, and the
            // moment the user happened to tap it carries no information.
            is Resolution.Skip -> lastResolution.dueDate.plus(recurrence.period)
        }
    }
