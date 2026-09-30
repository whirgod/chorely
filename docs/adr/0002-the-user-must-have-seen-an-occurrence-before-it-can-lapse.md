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
`POST_NOTIFICATIONS` reached nobody. Each passes the day it worked its list out for, not
the day it happens to be when the write lands.

The guarantee is therefore per *day*, not per occurrence: the user has been shown the app's
state as of that day. That is deliberately weaker than "this exact occurrence was on
screen" — see the per-chore option below — and strong enough for what the rule exists to
prevent, a month of lapses written for a phone that was off.

## Considered options

- **Per-chore, or per-occurrence, rather than global.** Rejected, though not because it
  would make no difference. The screen and the digest show every chore at once, but each
  only by its *outstanding* occurrence, so a later occurrence of the same chore can be
  counted as seen without having been on screen: a weekly Saturday-and-Sunday chore whose
  Saturday is still outstanding on Sunday shows Saturday, the day is marked seen, and
  Sunday's occurrence — outstanding once Saturday lapses — can lapse in turn at the next
  Saturday. Exactness would need a seen date per chore, or a record of which occurrences
  each view displayed, and the storage and migrations that come with it. The divergence is
  one occurrence at a time, on a chore the user was already being shown as overdue, so the
  global date was kept and the rule relaxed to match it.
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
