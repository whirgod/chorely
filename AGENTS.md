# AGENTS.md

## Project overview

Chorely is an offline-only Android app for recurring home cleaning chores: the user defines a chore with a repeat interval, the app computes when it is next due and fires a local notification. See [README.md](README.md) for the user-facing feature list.

Planned stack: Kotlin, Jetpack Compose, Room, WorkManager/AlarmManager. Nothing is pinned until the Gradle project exists.

**The repository contains no code yet** — only README, `.gitignore`, and agent config. The first implementation task must scaffold the Gradle project (see Setup).

## Setup

This machine has no JDK, no Android SDK, and no `android` CLI on `PATH` — verify with `java -version` before assuming otherwise.

- Install the CLI: `curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash`, then use `android sdk install …` for platform/build-tools packages.
- Scaffold the project with `android create empty-activity --name="Chorely" --output=.` rather than hand-writing Gradle files; the template ships a working wrapper, version catalog, and Compose setup.
- The `android-cli`, `testing-setup`, `navigation-3`, `edge-to-edge`, and `styles` skills in [.agents/skills](.agents/skills) are the authority for Android tooling and API-level questions — read the relevant SKILL.md instead of recalling API details.
- Use `android docs <keywords>` for current Android API guidance; training knowledge of Jetpack APIs is routinely stale.

## Commands

Once scaffolded, everything runs through the Gradle wrapper from the repo root:

- Build debug APK: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- Instrumented tests (needs a running device/emulator): `./gradlew connectedDebugAndroidTest`
- Lint: `./gradlew lint`
- Full pre-commit gate: `./gradlew build lint test`

Never invoke `gradle` directly — only `./gradlew`, so the pinned wrapper version is used.

## Domain invariants

These span multiple files and are easy to get wrong from any single one:

- The next due date is computed from the **completion** timestamp, not from the previous due date — completing a chore late shifts the whole future series forward; there is no catch-up backlog.
- A chore is "overdue", not "missed": due dates never expire, and no chore occurrence is ever silently dropped.
- Room is the single source of truth for schedules; anything scheduled with AlarmManager/WorkManager is a derived cache that must be rebuildable from the database alone.

## Never do

- No network, account, analytics, or cloud-sync dependency — the app must work fully offline, and adding an internet permission is a product change, not an implementation detail.
- Do not store due dates as pre-expanded rows for every future occurrence; store the rule plus the last completion.

## Reminder scheduling gotchas

The reminder path is where sessions get lost. Facts that are not visible from any one source file:

- Scheduled alarms do not survive a reboot; a `RECEIVE_BOOT_COMPLETED` receiver must re-derive them from Room, and that path needs its own test.
- `POST_NOTIFICATIONS` is a runtime permission on API 33+; a silently missing notification usually means it was never granted.
- Exact alarms require `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` on API 31+ and are Play-Store-restricted — prefer inexact scheduling unless a chore genuinely needs a precise minute.
- WorkManager periodic work has a 15-minute minimum interval and is deliberately inexact under Doze; use it for a daily due-sweep, not for firing a reminder at a specific time.
- Keep alarm/notification scheduling behind an interface so due-date logic stays unit-testable without a device.

## Code style

- Kotlin official style as enforced by `./gradlew lint`; do not hand-format against a different convention.
- Dependency versions live only in `gradle/libs.versions.toml` — never inline a version in a `build.gradle.kts`, and never duplicate one into this file.
- Date/time: `java.time` with an explicit `Clock` injected into scheduling logic, so tests can advance time.

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
