# Chores recur as a stream of occurrences, resolved or auto-skipped

Chorely tracks recurring housework, and the two obvious models both fail. A
single "next due date" per chore loses the fact that a weekly chore was missed
three weeks running; an unbounded backlog of every missed occurrence produces a
screen full of guilt and demands the user account for a month away. We model a
chore as a stream of **occurrences** in which exactly one is outstanding at a
time: when a calendar-anchored chore's next occurrence falls due, the previous
one is **auto-skipped** rather than accumulating. History stays accurate; the
backlog cannot grow.

Recurrence comes in two kinds, split by where the chore's rhythm comes from
rather than by any technical property. **Calendar-anchored** chores have an
external rhythm — the bins go out on Tuesdays — so a late completion must not
move the next due date, and auto-skip applies. **Completion-anchored** chores
have an internal clock — limescale takes three months regardless of the
calendar — so the next due date is computed from the completion, and nothing
ever arrives to displace an outstanding occurrence. Each kind covers exactly
the gap the other leaves: without the first, weekly chores drift off their day
after any late completion; without the second, quarterly chores are
inexpressible without far more elaborate calendar rules.

## Considered options

- **One next-due slot per chore.** Rejected: under calendar anchoring, missing
  three Tuesdays becomes indistinguishable from missing one, and the missed
  occurrences vanish from a history we deliberately keep.
- **Unbounded occurrence backlog.** Rejected: a month away produces thirty rows
  demanding absolution. Nobody vacuums four times to make up for a missed month.
- **A single recurrence type with an optional calendar anchor.** Rejected: it
  breaks on multi-weekday rules like "Mon and Thu", where there is no single
  period — the two shapes genuinely differ.
- **Storing the outstanding occurrence as a row.** Rejected: auto-skip is a
  time-triggered state change with nothing to trigger it, so correctness would
  depend on a background job having run under Doze.
- **Versioning recurrence rules to replay history across edits.** A reasonable
  fallback, rejected as more machinery than catch-up needs.

## Consequences

The outstanding occurrence is derived on read from the recurrence plus the
newest stored resolution, so what the user sees never depends on a background
job. Resolutions are stored, and because auto-skips have no user action behind
them they are written by an idempotent **catch-up** that runs before any read
or write of a chore. This means opening the app writes to the database, which
is accepted: the alternative was deriving auto-skips too, which silently
rewrites history the moment a user edits a recurrence, since the rule the old
skips were derived from is gone.
