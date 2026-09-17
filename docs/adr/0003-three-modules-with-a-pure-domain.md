# Three Gradle modules, with the domain as plain JVM

Chorely is built as `:app`, `:core:data` and `:core:domain`. `:core:domain` applies the
Kotlin JVM plugin rather than the Android library plugin, so the Android SDK is not on its
classpath: due-date arithmetic, catch-up and the occurrence model *cannot* reach for a
`Context`, a Room entity or `SystemClock` even by accident, and their tests are ordinary
JUnit that runs in about a second with no device and no Robolectric.

The interface between the modules is deliberately lopsided. `:core:domain` declares the
ports — `ChoreStore`, `ReminderSettings` — and the one interface the app uses, `Chores`.
`:core:data` implements the ports over Room and depends on the domain; `:app` depends on
the domain for language and on `:core:data` only so Hilt can bind the adapters at the
composition root. No ViewModel can see a Room entity, because none is visible to it.

## Considered options

- **A single `:app` module with packages.** Fastest builds and simplest configuration, and
  rejected only because nothing would enforce the seam: the whole value of the pure domain
  is that the compiler refuses the shortcut, and a package boundary refuses nothing.
- **Also splitting `:feature:chores` and `:feature:reminders`.** Rejected for now as Gradle
  configuration without a second consumer to justify it. Worth revisiting if the backlog's
  export/import arrives, which would be the first genuinely separable feature.
- **An Android library for the domain.** Rejected: it compiles the same code but puts the
  SDK back within reach, and it costs an AGP build for a module with no resources, no
  manifest and no Android API use.

## Consequences

`java.time` is used throughout the domain, which needs API 26. Rather than configure core
library desugaring in two Android modules, `minSdk` is 26 — a trade of some very old devices
for one less moving part in the build.

Anything the domain needs from the platform has to be expressed as a port first, which is
friction by design. The one place it bites is time: `Clock` is injected everywhere rather
than reached for, because it carries both the instant and the zone, and both matter.
