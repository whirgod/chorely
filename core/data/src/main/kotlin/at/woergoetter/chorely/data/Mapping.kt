package at.woergoetter.chorely.data

import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Recurrence
import at.woergoetter.chorely.domain.Resolution
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Period

/**
 * Translation between the domain's language and the columns.
 *
 * All of it is here rather than spread over the DAO, the store and the entities, so that
 * adding a recurrence kind (the backlog's monthly schedules) means editing one file on this
 * side of the seam and one on the other.
 */

private const val KIND_WEEKDAYS = "weekdays"
private const val KIND_PERIOD = "period"

internal fun ChoreEntity.toDomain(): Chore = Chore(
    id = ChoreId(id),
    name = name,
    recurrence = toRecurrence(),
    anchoredOn = LocalDate.ofEpochDay(anchoredOn),
    archivedAt = archivedAt?.let(Instant::ofEpochMilli),
)

internal fun Chore.toEntity(): ChoreEntity = ChoreEntity(
    id = id.value,
    name = name,
    recurrenceKind = when (recurrence) {
        is Recurrence.OnWeekdays -> KIND_WEEKDAYS
        is Recurrence.Every -> KIND_PERIOD
    },
    weekdays = (recurrence as? Recurrence.OnWeekdays)
        ?.days?.sortedBy { it.value }?.joinToString(",") { it.value.toString() },
    period = (recurrence as? Recurrence.Every)?.period?.toString(),
    anchoredOn = anchoredOn.toEpochDay(),
    archivedAt = archivedAt?.toEpochMilli(),
)

private fun ChoreEntity.toRecurrence(): Recurrence = when (recurrenceKind) {
    KIND_WEEKDAYS -> Recurrence.OnWeekdays(
        checkNotNull(weekdays) { "chore $id is a weekday recurrence with no days" }
            .split(",").map { DayOfWeek.of(it.toInt()) }.toSet(),
    )

    KIND_PERIOD -> Recurrence.Every(
        Period.parse(checkNotNull(period) { "chore $id is a period recurrence with no period" }),
    )

    else -> error("chore $id has unknown recurrence kind '$recurrenceKind'")
}

private const val COMPLETION = "completion"
private const val SKIP_MANUAL = "skip_manual"
private const val SKIP_DISPLACED = "skip_displaced"

internal fun ResolutionEntity.toDomain(): Resolution {
    val dueDate = LocalDate.ofEpochDay(dueDate)
    val at = Instant.ofEpochMilli(resolvedAt)
    return when (kind) {
        COMPLETION -> Resolution.Completion(dueDate, at)
        SKIP_MANUAL -> Resolution.Skip(dueDate, at, Resolution.Skip.Kind.Manual)
        SKIP_DISPLACED -> Resolution.Skip(dueDate, at, Resolution.Skip.Kind.Displaced)
        else -> error("resolution $id has unknown kind '$kind'")
    }
}

internal fun Resolution.toEntity(choreId: ChoreId): ResolutionEntity = ResolutionEntity(
    choreId = choreId.value,
    dueDate = dueDate.toEpochDay(),
    resolvedAt = at.toEpochMilli(),
    kind = when (this) {
        is Resolution.Completion -> COMPLETION
        is Resolution.Skip -> when (kind) {
            Resolution.Skip.Kind.Manual -> SKIP_MANUAL
            Resolution.Skip.Kind.Displaced -> SKIP_DISPLACED
        }
    },
)
