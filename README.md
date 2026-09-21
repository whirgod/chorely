# Chorely

An Android app for staying on top of regular home cleaning chores.

Give each chore a repeating schedule — vacuum every Saturday, descale the
kettle every 3 months — and Chorely works out what is due and reminds you.

## Status

Early development. The domain is implemented and tested; the UI is scaffolding.

- **Done**: the recurrence and occurrence model, catch-up and auto-skip, the
  Room store, reminder scheduling and the daily digest notification. The agenda
  works, and so does the chore editor — though only for creating a chore, since
  nothing in the app opens it for an existing one yet.
- **Not done**: the chore detail, archive and settings screens render
  placeholder text, so the reminder time cannot be set yet.

What is left to build, in the order it wants doing, is in [TODO.md](TODO.md).
The vocabulary is in [CONTEXT.md](CONTEXT.md), the decisions behind the model
and the module layout are in [docs/adr](docs/adr).

## Planned features

- Chores that repeat either on set weekdays ("every Saturday", the default) or
  after an elapsed period since you last did them ("every 3 months")
- One daily notification listing what is due, at a time you choose — silent on
  days with nothing due
- Mark a chore done, early or late, or skip an occurrence you decided to pass on
- Overview of what is due today, what is overdue, and what is coming up
- A history of every time you did each chore
- Archive chores you no longer want without losing their history
- Works fully offline; no account required

Ideas that are deferred, and ideas deliberately rejected, are in
[BACKLOG.md](BACKLOG.md).

## Tech stack

- Kotlin, Jetpack Compose, Navigation 3
- Room for local persistence, Hilt for wiring
- WorkManager for the daily reminder

Three Gradle modules: `:core:domain` is plain JVM and holds every due-date rule,
`:core:data` implements its ports over Room, and `:app` is UI and platform.

## Building

```
export JAVA_HOME=$HOME/.jdks/jdk-17.0.20.1+1
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew build lint test
```
