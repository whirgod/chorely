# An occurrence must have been seen before it can be recorded as a lapse

[ADR 0001](0001-occurrence-stream-with-auto-skip.md) settled that a calendar-anchored
occurrence is auto-skipped when its successor falls due, and that auto-skips are written
rather than derived. Taken alone that rule invents history: a phone left off for a month,
opened once, would write four weeks of lapses for a daily chore in a single catch-up — a
record of the user failing at something they were never told about. So auto-skip carries a
second condition: an occurrence is displaced only if the user has already been shown it.

"Shown" is one date for the whole app, `seenThrough`, stored in Room and advanced by
`Chores.markSeen()`. Two things advance it, and both are places the user genuinely saw what
was due: the overview screen appearing, and the daily digest **actually being posted** —
not merely the worker having run, since a digest the system refused for want of
`POST_NOTIFICATIONS` reached nobody.

## Considered options

- **Per-chore rather than global.** Rejected as machinery without a difference: the screen
  and the digest both show every due chore at once, so the per-chore dates would only ever
  move together.
- **Counting any app launch as seen.** Rejected: opening the editor to add a chore is not
  being shown what is due, and it would make lapses depend on which screen the user happened
  to open.
- **Deriving "seen" from the notification history.** Rejected: nothing durable records that
  a notification was displayed rather than posted, and the answer has to survive a reboot.

## Consequences

A user who never grants notification permission and rarely opens the app accumulates no
lapses at all — their chore simply stays outstanding at its original due date, growing more
overdue. That is the correct reading of "overdue is not a judgement": the app records what
it knows, and it does not know the chore was skipped.

It also means the history is a record of *acknowledged* misses rather than of calendar
truth, and the two diverge for anyone with notifications off. The alternative was a history
that asserts things about the user that were never true, which is worse.
