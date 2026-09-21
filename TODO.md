# TODO

The work that is open right now, in the order it wants doing. Each entry says
where the code is and what is not visible from opening that file — the traps,
the decisions still unmade, and what has to move with it.

Ideas deliberately postponed or rejected are in [BACKLOG.md](BACKLOG.md); this
file is only the near edge of the same list. An entry leaves here when the work
lands, not when it is planned.

Everything below the UI is done: the recurrence and occurrence model, catch-up
and auto-skip, the Room store, reminder scheduling and the digest notification.
All four open items are the UI and the tests around the reminder path.

## Next

**1. The reminder time picker, and the notification permission**
[`SettingsScreen.kt`](app/src/main/kotlin/at/woergoetter/chorely/ui/settings/SettingsScreen.kt)
shows the stored time and offers no way to set it; `POST_NOTIFICATIONS` is
declared in the manifest but never requested.
_Decide first_: `reminder_minute_of_day` has no default, so on a fresh install
`reminderTime()` is null, `Reminders.sync()` cancels rather than schedules, and
**the digest never fires at all** until the user opens this screen. Either seed
a default time on first run or keep reminders opt-in — a product decision, not
an implementation detail, and the only thing standing between a new install and
a silent app.

**2. The chore detail screen**
[`ChoreScreen.kt`](app/src/main/kotlin/at/woergoetter/chorely/ui/chore/ChoreScreen.kt)
lists history rows as `it::class.simpleName` and wires none of its own
callbacks: `onEdit`, `onComplete`, `onSkip` and `onArchive` are all unused.
_Carries_: the assisted-injection step written out on `ChoreViewModel.detail` —
`@HiltViewModel(assistedFactory = ...)` plus `hiltViewModel(creationCallback =
...)` at the nav entry — after which the chore id reaches the constructor and
`detail` becomes a `StateFlow` like its peers. It was left until the screen
exists because the screen's shape decides what the ViewModel exposes.
_Watch out_: a history is not a score. See the Rejected section of
[BACKLOG.md](BACKLOG.md) before adding a streak or a completion rate.

**3. The archive list**
[`ArchiveScreen.kt`](app/src/main/kotlin/at/woergoetter/chorely/ui/archive/ArchiveScreen.kt)
prints names with no actions. Restore and delete are one call each on the view
model, which makes this the smallest of the four.
_Watch out_: delete discards the history and is not undoable, so it wants a
confirmation; archive, which keeps it, must not.

**4. A test for the reminder path**
There is none.
[`DigestScheduleTest`](app/src/test/kotlin/at/woergoetter/chorely/reminder/DigestScheduleTest.kt)
covers `nextDigestDelay` and nothing else, and `app` has no `androidTest`
source set at all — though the dependencies for one (`work-testing`,
`hilt-android-testing`, `kspAndroidTest`) are already declared.
_Owed_: the boot path, which AGENTS.md requires a test for — `BootReceiver` to
`ReminderSyncWorker` to `WorkManagerReminders` — and `DailyDigestWorker`'s
post-then-`markSeen` ordering, which is the guard that stops an unseen
occurrence lapsing.
_Moves with it_: `ci.yml`'s `instrumented-tests` job runs only
`:core:data:connectedDebugAndroidTest`, so an app instrumented test would
compile in CI and never run until that job learns about it.

## Housekeeping

**User-facing strings are hardcoded in the placeholder screens**
"Daily reminder at …", "Reminders off" and the detail screen's "Due …" are
literals in Kotlin; everything the agenda and the editor show is already in
`strings.xml`. Fold each one in as its screen is built, rather than as a sweep
afterwards.
