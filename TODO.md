# TODO

The work that is open right now, in the order it wants doing. Each entry says
where the code is and what is not visible from opening that file — the traps,
the decisions still unmade, and what has to move with it.

Ideas deliberately postponed or rejected are in [BACKLOG.md](BACKLOG.md); this
file is only the near edge of the same list. An entry leaves here when the work
lands, not when it is planned.

Everything below the UI is done: the recurrence and occurrence model, catch-up
and auto-skip, the Room store, reminder scheduling and the digest notification.
The two open items are the last screen and the tests around the reminder path.

## Next

**1. The archive list**
[`ArchiveScreen.kt`](app/src/main/kotlin/at/woergoetter/chorely/ui/archive/ArchiveScreen.kt)
prints names with no actions. Restore and delete are one call each on the view
model, which makes this the smaller of the two.
_Watch out_: delete discards the history and is not undoable, so it wants a
confirmation; archive, which keeps it, must not.

**2. A test for the reminder path**
There is none.
[`DigestScheduleTest`](app/src/test/kotlin/at/woergoetter/chorely/reminder/DigestScheduleTest.kt)
covers `nextDigestDelay` and nothing else, and `app` has no `androidTest`
source set at all — though the dependencies for one (`work-testing`,
`hilt-android-testing`, `kspAndroidTest`) are already declared.
_Owed_: the boot path, which AGENTS.md requires a test for — `BootReceiver` to
`ReminderSyncWorker` to `WorkManagerReminders` — and `DailyDigestWorker`'s
post-then-`markSeen` ordering, which is the guard that stops an unseen
occurrence lapsing.
_Watch out_: `DailyDigestWorker` re-syncs only where `doWork` returns, so a
throw from `chores.due()` or `notifier.post()` drops the chain for good — a
reboot or the user changing the reminder time in settings is then the only
thing that rebuilds it. Long-standing rather than new: the
chore save that once called `Reminders.sync()` was never reachable from a
placeholder editor. Left unfixed because the fix is precisely what the
`TestDriver` test above has to assert.
_Moves with it_: `ci.yml`'s `instrumented-tests` job runs only
`:core:data:connectedDebugAndroidTest`, so an app instrumented test would
compile in CI and never run until that job learns about it.

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
_Why it is still here_: the latch would have to notice a pop it did not
originate, which is a change to the navigation layer rather than a correction
inside this screen. `back()` already refuses to empty the stack, so the cheapest
variant of this cannot crash — it only navigates wrongly.
_Moves with it_: a test. This one is only reachable through a real transition,
so unlike the guards in
[`NavigationTest`](app/src/test/kotlin/at/woergoetter/chorely/NavigationTest.kt)
it needs a Compose UI test, and therefore the `androidTest` source set and the
CI job that item 2 above is already waiting on.
