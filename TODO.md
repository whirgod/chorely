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

**The archive row's buttons crowd the name at large font scales**
[`ArchiveScreen.kt`](app/src/main/kotlin/at/woergoetter/chorely/ui/archive/ArchiveScreen.kt)
puts Delete and Restore in `ListItem`'s `trailingContent`, which is not
constrained, so on a narrow phone with a large font the name — the only thing
saying which chore a Delete applies to — is squeezed to a sliver. Moving the
actions under the headline, or into an overflow menu, fixes it.

**Date formatting is copied between screens**
`ChoreScreen` and `ArchiveScreen` each build the locale-keyed MEDIUM formatter
and each convert an `Instant` to a local date with
`atZone(ZoneId.systemDefault())` (not `LocalDate.ofInstant`, which is API 34+).
A third copy is the moment to lift both into a shared helper in `ui`.

**Writes to an archived chore still catch it up**
`complete`, `skip` and `edit` in
[`StoredChores.kt`](core/domain/src/main/kotlin/at/woergoetter/chorely/domain/StoredChores.kt)
run `caughtUp` without asking whether the chore is archived, so they write the
lapses of months it spent in the archive into a history that cannot lose them —
the thing `archive`, `restore` and `delete` now refuse. Nothing in the app
reaches them for an archived chore, since no screen opens one; a second stacked
`MainActivity` holding a stale chore screen can.
_Moves with it_: a `StoredChoresTest` case per write, and one gate in
`caughtUp` is likely simpler than three.

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
