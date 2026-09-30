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

**The editor's leave latch does not cover the system back gesture**
`leave()` in
[`ChoreEditorScreen.kt`](app/src/main/kotlin/at/woergoetter/chorely/ui/chore/ChoreEditorScreen.kt)
closes the latch for Cancel and Save, but `NavDisplay`'s `onBack` calls the back
stack's `back()` directly, so a gesture never goes through it. On
`[Agenda, Chore, Editor]`: tap Cancel, then swipe back while the editor is still
animating out, and the chore detail screen is popped as well — one tap plus one
gesture costs two screens. The rarer order is worse in kind: swipe back to
abandon a half-filled form, let a finger land on the still-visible Save, and the
chore is created after the user abandoned it.
`ChoreScreen` has a latch of the same shape with the same gap: Back or Archive
then a swipe, on `[Agenda, Chore]`, lands the swipe on the agenda and leaves
the app.
`ArchiveScreen` has no latch at all: a Restore tapped while it slides out
still lands, and a Delete opens a dialog that vanishes with the entry.
_Why it is still here_: the latch would have to notice a pop it did not
originate, which is a change to the navigation layer rather than a correction
inside this screen. `back()` already refuses to empty the stack, so the cheapest
variant of this cannot crash — it only navigates wrongly.
_Moves with it_: a test. This one is only reachable through a real transition,
so unlike the guards in
[`NavigationTest`](app/src/test/kotlin/at/woergoetter/chorely/NavigationTest.kt)
it needs a Compose UI test, in the app's `androidTest` source set, which CI
already runs on API 26 — where `reminder/Fakes.kt` replaces the data module for
every test, and its `FakeChores` throws on the reads a UI needs.

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
- A reboot while a failed digest waits out its backoff loses today's digest:
  the boot sync REPLACEs the pending retry and aims at tomorrow. The same loss
  `BootReceiver`'s KDoc already names for a reboot after the reminder time, made
  likelier by the retry window; keeping a digest already aimed at today would
  close both.

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
