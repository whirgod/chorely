# AGENTS.md

## Project overview

Chorely is an offline-only Android app for recurring home cleaning chores: the user defines a chore with a repeat interval, the app computes when it is next due and fires a local notification. See [README.md](README.md) for the user-facing feature list.

Stack: Kotlin, Jetpack Compose, Navigation 3, Room, Hilt, WorkManager. Modules are `:app`, `:core:data` and `:core:domain`; see [docs/adr/0003](docs/adr/0003-three-modules-with-a-pure-domain.md) for why, and read it before adding a module or moving code between them.

The UI is scaffolding: `AgendaScreen` works, the chore/editor/archive/settings screens are marked `TODO` and render placeholder text. Everything beneath them is real; the open work and the traps in each piece of it are in [TODO.md](TODO.md).

## Setup

Gradle needs `JAVA_HOME` and `ANDROID_HOME` exported; neither is on this machine's profile:

```
export JAVA_HOME=$HOME/.jdks/jdk-17.0.20.1+1
export ANDROID_HOME=$HOME/Android/Sdk
```

- There is no system JDK and `apt` needs sudo — the JDK above was unpacked from Adoptium into `~/.jdks`. Check it is still there before concluding the build is broken.
- The Android SDK and the `android` CLI (`~/.local/bin/android`) were installed by `curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash`.
- No emulator is possible here: this WSL2 kernel has no `/dev/kvm`, so `connectedDebugAndroidTest` cannot run locally. Instrumented tests are still written and must at least compile via `assembleDebugAndroidTest`.
- The `android-cli`, `testing-setup`, `navigation-3`, `edge-to-edge`, and `styles` skills in [.agents/skills](.agents/skills) are the authority for Android tooling and API-level questions — read the relevant SKILL.md instead of recalling API details.
- Use `android docs <keywords>` for current Android API guidance; training knowledge of Jetpack APIs is routinely stale.

## Commands

Everything runs through the Gradle wrapper from the repo root:

- Build debug APK: `./gradlew assembleDebug`
- Unit tests: `./gradlew test` (`:core:domain:test` alone is the fast loop for due-date work)
- Instrumented tests (needs a device; impossible here, see Setup): `./gradlew connectedDebugAndroidTest`
- Compile instrumented tests without running them: `./gradlew assembleDebugAndroidTest`
- Lint: `./gradlew lint`
- Full pre-commit gate: `./gradlew build lint test`

Never invoke `gradle` directly — only `./gradlew`, so the pinned wrapper version is used.

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every PR to `main` and on pushes to it.

- `build` runs the same gate as the pre-push one, then compiles the instrumented tests.
- `instrumented-tests` boots an API 26 emulator — the app's `minSdk` — and runs `:core:data:connectedDebugAndroidTest`. **This is the only place instrumented tests ever run**, since the dev machine has no `/dev/kvm`.
- A test assertion failing there is real; retrying it is how a Room bug gets shipped. Only two signatures are worth a `gh run rerun --failed`, both runner-level and both seen on the very first run: `Unable to connect to adb daemon`, and Gradle failing to resolve a plugin that demonstrably exists on Maven Central. If a re-run reproduces either, it is no longer the runner.
- `main` is protected: it takes pull requests only, both checks must pass, and force-pushes and deletions are blocked. Renaming a job in the workflow breaks the required check until the protection rule is renamed to match.

## Releases

[`.github/workflows/release.yml`](.github/workflows/release.yml) builds a signed APK and publishes it as a GitHub Release on any `v*` tag push; there is no other distribution channel.

- The tag is the version: it must match `vMAJOR.MINOR.PATCH[-prerelease]`, the workflow rejects anything else, and a `-suffix` publishes as a prerelease.
- `versionName` and `versionCode` in [`app/build.gradle.kts`](app/build.gradle.kts) are derived from that tag via `-Pchorely.versionName`, packed three digits to a field (`1.2.3` -> `1002003`) — never hand-edit either, and never reuse or move a tag, since a shipped `versionCode` can only ever rise.
- That packing caps minor and patch at 999 and major at 2146 (`versionCode` is an `Int`); overflowing a field would silently collide with the field above it, so both the workflow and the build refuse such a version rather than shipping it.
- Release signing comes from four `CHORELY_*` environment variables, backed by the `RELEASE_*` repository secrets listed at the top of the workflow; with none set there is no signing config at all, so a local `./gradlew build` assembles an unsigned release on purpose.
- The workflow re-runs `lint test` because a tag can point at a commit that never went through `ci.yml`, but it deliberately skips the emulator suite, which gates merges instead.

## Domain invariants

All of these are implemented in one pure function, `catchUp` in `:core:domain`, and pinned by `CatchUpTest`. Change due-date behaviour there and nowhere else; if a rule is being expressed in a ViewModel, a DAO or a worker, it is in the wrong place.

- A recurrence is **calendar-anchored** (external rhythm: "every Tuesday" — a late completion must not move the next due date) or **completion-anchored** (internal clock: "every 3 months" — the next due date is computed from the completion timestamp, so late completion shifts the whole series); see [CONTEXT.md](CONTEXT.md) before touching due-date logic.
- Every chore has exactly one **outstanding** occurrence at a time, including completion-anchored ones, so "what is outstanding now" is a single query rather than a union over the two kinds.
- Only calendar-anchored occurrences auto-skip, and only once the next one falls due; a completion-anchored occurrence stays outstanding indefinitely, because nothing arrives to displace it.
- Never auto-skip an occurrence the user has not yet been shown: catch-up is guarded by a global `seenThrough` date that only `Chores.markSeen()` advances, and the digest worker calls it **only after a notification was actually posted** — see [docs/adr/0002](docs/adr/0002-the-user-must-have-seen-an-occurrence-before-it-can-lapse.md).
- Occurrences may be completed **before** their due date; do not assert completion timestamps fall after due dates.
- The outstanding occurrence is **derived** from the recurrence plus the newest stored resolution, never stored — so a device that has been off for a month shows correct state the moment it opens, with no background job involved.
- Reads derive and never write: `Chores.agenda()` and `detail()` run catch-up in memory, so collecting a Flow has no side effects. Auto-skips are persisted only by `markSeen()` and by the mutating methods, each of which catches up inside its own transaction first. Catch-up is idempotent, so the two paths cannot disagree.
- Editing a recurrence recomputes the outstanding due date, but an occurrence that was already overdue must stay at least as overdue — see `retarget`. The recomputed date sticks because the edit moves `Chore.anchoredOn`, which is a due date and not a lower bound on one ([docs/adr/0004](docs/adr/0004-the-anchor-is-a-due-date-not-a-lower-bound.md)), and which makes every older resolution superseded and invisible to catch-up; the history keeps them regardless.
- Room is the single source of truth for schedules; anything scheduled with AlarmManager/WorkManager is a derived cache that must be rebuildable from the database alone.

## Never do

- No network, account, analytics, or cloud-sync dependency — the app must work fully offline, and adding an internet permission is a product change, not an implementation detail.
- Do not build anything listed under Rejected in [BACKLOG.md](BACKLOG.md) — streaks and completion rates especially, which look like an obvious improvement and are ruled out on purpose.
- Android auto-backup is deliberately left **enabled** and is the one sanctioned exception to the no-cloud rule: it is the OS's own mechanism, the app never knows about it, and with no export feature it is all that survives a lost phone — do not "fix" it.
- Do not pre-expand rows for future occurrences; store the recurrence rule plus the history of resolved ones.
- Never delete or overwrite a resolved occurrence — the completion history is append-only, and archiving a chore must retain it.

## Reminder scheduling gotchas

The reminder path is where sessions get lost. Facts that are not visible from any one source file:

- Scheduled alarms do not survive a reboot; a `RECEIVE_BOOT_COMPLETED` receiver must re-derive them from Room, and that path needs its own test.
- `POST_NOTIFICATIONS` is a runtime permission on API 33+; a silently missing notification usually means it was never granted.
- Exact alarms require `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` on API 31+ and are Play-Store-restricted — prefer inexact scheduling unless a chore genuinely needs a precise minute.
- WorkManager periodic work has a 15-minute minimum interval and is deliberately inexact under Doze; use it for a daily due-sweep, not for firing a reminder at a specific time.
- The digest is chained one-shot `WorkManager` work, not periodic work, because periodic work cannot be aimed at a time of day; every run schedules the next, so any path that drops a run must call `Reminders.sync()`.
- `Reminders.sync()` is idempotent and derives everything from Room — call it freely rather than tracking whether the schedule is stale.
- `nextDigestDelay` is the only part of scheduling that can be silently *wrong* rather than broken; it is pure and unit-tested against DST, and new scheduling arithmetic belongs there too.

## Code style

- Kotlin official style as enforced by `./gradlew lint`; do not hand-format against a different convention.
- Dependency versions live only in `gradle/libs.versions.toml` — never inline a version in a `build.gradle.kts`, and never duplicate one into this file.
- Date/time: `java.time` with both `Clock` and `ZoneId` injected into scheduling logic, so tests can advance time and change timezone.
- Due dates are whole local calendar days in the device's *current* timezone, with no correction for travel: store instants, derive local dates on read.
- `minSdk` is 26 so `java.time` needs no core library desugaring — lowering it means adding desugaring to both Android modules, not just changing the number.
- `:core:domain` must stay a plain Kotlin JVM module: if something there needs Android, it needs a port instead.

## Commits and pull requests

- Commit subjects follow Conventional Commits: `type(scope): subject` (e.g. `feat(reminders): reschedule alarms after boot`), using `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, `build`, `ci`, `perf`, or `style`.
- PR titles use the same Conventional Commits format as the commit subject.
- Required before pushing: `./gradlew build lint test` green.
- Add or update tests for scheduling and due-date logic with every change that touches them, even if nobody asked.

## Maintaining this file

This file is memory for agent sessions, and you, the agent, maintain it.

- Update this file in the same change set whenever a change invalidates a line here or teaches a costly lesson.
- A bullet earns its place only if an agent would still get it wrong after reading the files its section points to: version pins, invariants that span files, never-do rules, and surprises that cost a session.
- Prefer deleting over adding, and pointers over prose; drop anything a reader could learn by opening the file a bullet points to.
- One sentence per bullet, current state only, no history or changelog.
- When a line here exists because a file it points to is wrong, say so and link that file, so the duplication can be deleted once the source is fixed.
