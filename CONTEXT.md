# Chorely

An offline, single-device Android app for keeping on top of recurring home
cleaning chores. It holds the rules for what needs doing how often, works out
what is due, and reminds the user.

## Language

**Chore**:
A recurring piece of housework the user wants to stay on top of, together with
the rule for how often it recurs. A chore is a long-lived thing, not a single
act of cleaning.
_Avoid_: Task, todo, job, item

**Recurrence**:
The rule on a chore that determines when it next falls due. Every chore has
exactly one, and it is either calendar-anchored or completion-anchored,
according to whether the chore's rhythm comes from the world or from the chore
itself.
_Avoid_: Schedule, interval, frequency, repeat

**Calendar-anchored**:
A recurrence for a chore with an external rhythm — the world supplies the day.
"Every Tuesday", because that is when the bins go out. Doing it late does not
move the following due date; drifting off the rhythm would be a defect.
_Avoid_: Fixed, absolute, static, on-schedule

**Completion-anchored**:
A recurrence for a chore with an internal clock — elapsed time since it was
last done is the only thing that matters. "Every 3 months", because that is how
long limescale takes. Doing it late moves the whole future series later, and
that is correct rather than drift.
_Avoid_: Floating, elapsed, relative, since-last

**Occurrence**:
A single expected doing of a chore, on a due date. Every chore has exactly one
outstanding occurrence at a time; resolving it produces the next.
_Avoid_: Instance, event, entry, task

**Outstanding**:
The state of an occurrence that has not yet been resolved, whether its due date
is in the future, today, or past.
_Avoid_: Open, pending, active, unresolved

**Completion**:
The resolution of an occurrence by the user doing the chore. Recorded with the
moment it happened, never overwritten or deleted, and permitted before the due
date as well as after.
_Avoid_: Done, tick, check-off, log entry

**Skip**:
The resolution of an occurrence without the chore being done. Either manual —
the user decided this one was not needed — or automatic, when a
calendar-anchored chore's next occurrence falls due and displaces it. The two
are recorded distinctly: one is a decision, the other a lapse.
_Avoid_: Dismiss, ignore, cancel

**Archive**:
Retiring a chore so it stops falling due, without destroying it. An archived
chore keeps its history and can be restored; deleting is the separate,
deliberate act that discards both.
_Avoid_: Disable, deactivate, hide, retire

**Due date**:
The calendar day on which an occurrence is expected to be done. Whole days
only — nothing in Chorely is ever due at a time of day.
_Avoid_: Deadline, due time, next date

**Overdue**:
A property of an outstanding occurrence whose due date has passed. Not a state
of its own, and not a judgement: an overdue occurrence is still simply waiting.
_Avoid_: Missed, late, failed, behind
