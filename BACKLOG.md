# Backlog

Ideas not being built now, and ideas deliberately not being built at all. Each
entry says why, so that picking one up later is a decision rather than a guess.

Decisions about how Chorely *works* live elsewhere: vocabulary in
[CONTEXT.md](CONTEXT.md), architecture in [docs/adr](docs/adr), invariants in
[AGENTS.md](AGENTS.md). Work that is open right now, rather than postponed, is
in [TODO.md](TODO.md).

## Deferred

**Household sharing**
Several people using Chorely for the same home, seeing the same chores and
assigning them between each other.
_Why not now_: the single-user core has to exist and be worth using first, and
sharing state without an account or a network means device pairing, conflict
resolution, and a sync model — a project in its own right rather than a
feature.
_Revisit when_: the app is in daily use and the thing actually missing is "my
flatmate did this and I cannot see that".
_Note_: this is the only entry here that is not additive. It puts an actor on
every completion, adds assignment to a chore, and either takes on an account or
a local pairing mechanism — so it touches the occurrence model and the offline
constraint at the same time. Treat it as a new version of the app, not a
feature on this one.

**Export and import**
Write chores and history to a file the user controls, and read it back.
_Why not now_: Android auto-backup covers the common case of moving to a new
phone, for the cost of leaving a manifest flag alone.
_Revisit when_: someone wants their data off Google's cloud, or a restore
fails and there is no second path to the history.

**Tags**
Free-form grouping of chores, replacing the flat list.
_Why not now_: a flat list is fine at the scale one household actually has, and
a taxonomy invented before the app has been used is a guess.
_Revisit when_: the chore list needs scrolling to find anything, or "show me
everything in the bathroom while I'm already in the bathroom" becomes a real
annoyance rather than a hypothetical one.

**Monthly and day-of-month schedules**
Calendar-anchored recurrence beyond weekly: "the 1st", "first Saturday",
"last Friday".
_Why not now_: weekly-on-weekdays covers bins, plants and bedsheets, which is
most of why calendar anchoring exists. Completion-anchored recurrence covers
the quarterly chores. The gap between them is small and the date arithmetic in
it is nasty.
_Revisit when_: a chore comes up that is genuinely neither — fixed to a date
rather than a weekday, and not merely "about every month".
_Note_: day-of-month needs a decided answer for "the 31st" in February.
Clamping to the last day of the month is the only sane one.

**Pause a chore**
Stop a chore falling due for a stretch — a holiday — then resume it.
_Why not now_: archive and restore covers the shape of it, and under the
occurrence model a chore ignored for two weeks comes back with one outstanding
occurrence rather than a backlog, so the degraded case is mild.
_Revisit when_: pausing several chores at once for a trip is tedious enough to
notice, which probably means a holiday mode rather than a per-chore pause.

## Rejected

Not "later" — decided against. Reopening one of these means overturning a
reason, not just finding time.

**Per-chore reminder times**
A notification time set on each chore rather than one for the whole app.
_Why_: it makes chores due at a moment rather than on a day, which contradicts
the domain model, and it drags in `SCHEDULE_EXACT_ALARM` — a Play-Store
restricted permission — for no gain. One daily digest also respects the user's
attention more than N separate buzzes.

**Streaks, completion rates, and charts**
Gamified statistics over the completion history.
_Why_: a broken streak is a reason to stop using an app, and guilt is precisely
what Chorely's "overdue is not a judgement" stance exists to avoid. The plain
per-chore history answers the question people actually have — when did I last
do this — without keeping score.

**Priority on a chore**
An importance ranking, separate from when the chore is due.
_Why_: it fights the due-date model, which already answers what matters today.
In practice it is a field everyone fills in once and never maintains.

**Estimated duration on a chore**
How long the chore takes, for planning "what can I fit into 20 minutes".
_Why_: the estimate is never accurate and never updated. If this comes back it
should be derived from real completions, not typed in by hand.

**Rooms as a fixed taxonomy**
A closed set of rooms — Kitchen, Bathroom — as the grouping axis.
_Why_: superseded by tags above, which do the same job without the app deciding
what rooms a home has.
