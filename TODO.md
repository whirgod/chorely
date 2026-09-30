# TODO

The work that is open right now, in the order it wants doing. Each entry says
where the code is and what is not visible from opening that file — the traps,
the decisions still unmade, and what has to move with it.

Ideas deliberately postponed or rejected are in [BACKLOG.md](BACKLOG.md); this
file is only the near edge of the same list. An entry leaves here when the work
lands, not when it is planned.

Every screen is built, and everything below them is done and tested: the
recurrence and occurrence model, catch-up and auto-skip, the Room store,
reminder scheduling and the digest notification. Nothing is queued under Next;
what is left is housekeeping.

## Housekeeping

**Three edges of the digest's retry**
[`DailyDigestWorker`](app/src/main/kotlin/at/woergoetter/chorely/reminder/DailyDigestWorker.kt)
retries a failed run as a whole, up to five times, five minutes apart.
- A `sync()` that throws after a successful post re-posts on the retry, and
  rings, if the user has dismissed the first one meanwhile. Needs the retry to
  know the post happened, which nothing persists across attempts today; making
  every retry silent instead would lose the alert when the *first* attempt
  failed before posting.
- A retry for a reminder time after 23:10 can cross midnight, post the new
  day's list, and then sync to that same day's reminder time: two digests on
  one day, none the day before. Give up once the local date has moved on.
- [`WorkManagerReminders.sync()`](app/src/main/kotlin/at/woergoetter/chorely/reminder/WorkManagerReminders.kt)
  never awaits the `Operation` that `enqueueUniqueWork` returns, so WorkManager's
  own write failing is never seen, let alone retried. Awaiting it inside the
  digest worker suspends on the REPLACE that cancels that very worker, so the
  `DailyDigestWorkerTest` event assertions need rethinking with it.

**A stale editor can still save over an archived chore**
The editor refuses to open an archived chore, but checks only when it opens:
a chore archived from a second stacked task while the editor is up — or an
editor restored after process death, which skips the load — saves into
`Chores.edit`, which ignores it, and pops as though it had saved. Needs `edit`
to say whether it wrote, which `Chores` returns as `Unit` today; `detail()`
could also emit null for an archived chore, so screens stop filtering it
themselves, once the chore screen no longer relies on seeing it archive.

**One operation can read two zones**
[`StoredChores`](core/domain/src/main/kotlin/at/woergoetter/chorely/domain/StoredChores.kt)
reads its clock's zone several times per call, and the clock now follows a
zone change, so one landing mid-call can bucket the agenda by one day and catch
up by another. Milliseconds wide; `nextDigestDelay` already pins the zone once.
_Moves with it_: `clock.withZone(clock.zone)` at the top of each operation,
passed down into the `ChoreRecord` helpers that read the member today.
