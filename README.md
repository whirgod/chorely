# Chorely

An Android app for staying on top of regular home cleaning tasks.

Set a repeating schedule for each chore — vacuum the living room every 7 days,
descale the kettle every 3 months — and Chorely reminds you when each one is
next due.

## Status

Early development. Nothing implemented yet.

## Planned features

- Define cleaning tasks with a repeat interval (daily / weekly / monthly / custom)
- Automatic local notifications when a task becomes due
- Mark a task done to reschedule it from the completion date
- Overview of what is due today, overdue, and coming up
- Works fully offline; no account required

## Tech stack (planned)

- Kotlin
- Jetpack Compose
- Room for local persistence
- WorkManager / AlarmManager for reminder scheduling
