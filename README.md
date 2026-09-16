# Chorely

An Android app for staying on top of regular home cleaning chores.

Give each chore a repeating schedule — vacuum every Saturday, descale the
kettle every 3 months — and Chorely works out what is due and reminds you.

## Status

Early development. Nothing implemented yet. The domain model is settled; see
[CONTEXT.md](CONTEXT.md).

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

## Tech stack (planned)

- Kotlin
- Jetpack Compose
- Room for local persistence
- WorkManager / AlarmManager for reminder scheduling
