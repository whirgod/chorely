# The anchor is a due date, not a lower bound on one

[ADR 0001](0001-occurrence-stream-with-auto-skip.md) settled that the outstanding occurrence
is derived and never stored, which leaves `Chore.anchoredOn` as the only knob an edit can
turn to move it. `anchoredOn` used to mean "the first day the current rule *may* place an
occurrence", and catch-up rounded it forward onto the rule's grid on every read. That made
`retarget` a promise the representation could not keep: editing a chore that was overdue
since Wednesday to a Sundays-only rule stored the anchor Wednesday, and the next read
rounded it forward to Sunday — the neglected chore came back clean, which is the one thing
`retarget` exists to prevent. Under `Recurrence.Every` the rounding is the identity, so the
gap was invisible until a `Recurrence.OnWeekdays` rule was edited to days it did not have.

So `anchoredOn` now means the due date of the current rule's first occurrence. Rounding
happens once, in `anchorFor`, where a *day* becomes an anchor — creating a chore, restoring
one, and rebasing the old anchor inside an edit — and never again on read. An edit can
therefore hold an occurrence on a day its new rule would never have chosen, which is
exactly what carrying a lapse across a rule change means; the occurrence rejoins the grid
as soon as it is resolved or displaced, because that step is computed from the rule.

## Considered options

- **Snapping the target back to the nearest day the new rule allows.** Rejected: the anchor
  also decides which history is superseded, so snapping backwards can drag a resolution that
  must stay superseded back into play — last done Monday, overdue since Tuesday, new rule
  Sundays, and the nearest Sunday at or before Tuesday is the 13th, behind the completion.
  In that interval it has no answer at all, and where it has one it invents extra lateness.
- **Splitting the anchor into a placement date and a supersession date.** The honest version
  of the option above, and rejected with it: it is a second stored field, hence a Room
  column and a migration, bought to express a state the single field can already express
  once it stops rounding.
- **Storing the outstanding occurrence.** Still rejected for ADR 0001's reason — auto-skip
  would then need a background job to have run. An anchor is not that: catch-up re-derives
  the whole stream from it on every read.
- **Versioning recurrence rules and replaying history.** Rejected again as more machinery
  than the one case needs, and it would make history depend on rules the user has discarded.

## Consequences

`catchUp` gets marginally smaller — with nothing in play the answer is the anchor, for both
anchorings — at the cost of a rule that callers must respect: an anchor is only ever set
through `anchorFor` or from a computed due date, never from a raw day.

One state remains inexpressible, and it is the mirror image of the one fixed here: the
anchor supersedes resolutions due *before* it, so a chore whose newest resolution is due
after its own outstanding occurrence keeps that resolution in play whatever the anchor says,
and an edit follows the new rule from the resolution instead — the chore comes back clean.
Reaching it takes resolving the same chore twice in one day under a completion-anchored
rule, which records a resolution for an occurrence a period ahead, and then editing the
recurrence. `StoredChoresTest` pins the behaviour rather than leaving it to be rediscovered.
