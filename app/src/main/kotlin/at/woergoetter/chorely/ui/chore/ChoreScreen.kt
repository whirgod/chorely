package at.woergoetter.chorely.ui.chore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.woergoetter.chorely.R
import at.woergoetter.chorely.domain.ChoreDetail
import at.woergoetter.chorely.domain.Recurrence
import at.woergoetter.chorely.domain.Resolution
import at.woergoetter.chorely.ui.rememberDateFormatter
import at.woergoetter.chorely.ui.toLocalDateHere
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * One chore: where it stands, and the history of every time it was done.
 *
 * Deliberately a history and not a score — no streaks, no completion rate. See the Rejected
 * section of BACKLOG.md before adding either.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoreScreen(
    viewModel: ChoreViewModel,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The editor's latch, for the same reason: a screen stays composed and tappable for the
    // length of the transition that takes it off screen. Back and Archive both pop, so a
    // second tap on either pops again — harmless today only because `back()` refuses to pop
    // the agenda, not because anything knew the tap was spent — and a second Archive is a
    // second write. Edit pushes, and a Back or Archive landing while the editor slides in
    // would pop the editor instead of this screen, so all three share the one way out.
    var leaving by remember { mutableStateOf(false) }
    fun leave(onWayOut: () -> Unit) {
        if (leaving) return
        leaving = true
        onWayOut()
    }

    // Edit is a way out that comes back. Usually this entry leaves composition behind the
    // editor and returns with a fresh latch, but an editor popped mid-transition hands the
    // screen back still composed and still latched. Nav3 resumes an entry only once it is on
    // top and settled, so a resume is the moment the screen is fully back and may be left again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { leaving = false }

    LaunchedEffect(state) {
        if (state is ChoreState.Gone) leave(onBack)
    }

    val detail = (state as? ChoreState.Shown)?.detail

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(detail?.chore?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    TextButton(onClick = { leave(onBack) }, enabled = !leaving) {
                        Text(stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { leave(onEdit) }, enabled = detail != null && !leaving) {
                        Text(stringResource(R.string.edit))
                    }
                    // No confirmation, unlike delete: archiving keeps the history, and the
                    // archive screen restores it in one tap.
                    TextButton(
                        onClick = {
                            leave {
                                viewModel.onArchive()
                                onBack()
                            }
                        },
                        enabled = detail != null && !leaving,
                    ) { Text(stringResource(R.string.archive_chore)) }
                },
            )
        },
    ) { padding ->
        val shown = detail ?: return@Scaffold
        ChoreDetailList(
            detail = shown,
            onComplete = viewModel::onComplete,
            onSkip = viewModel::onSkip,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun ChoreDetailList(
    detail: ChoreDetail,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val dates = rememberDateFormatter(locale)

    // Done and Skip each resolve whichever occurrence is outstanding when the write lands, so
    // a double tap resolves this one and then its successor, which the user has never seen.
    // Resolving always appends to the history, so the buttons stay off from the first tap
    // until the history has grown — the moment the screen shows what the tap did, since the
    // outstanding occurrence is derived from that same history (see ChoreDetail). Keyed on
    // the size and not the outstanding occurrence, since completing an "every day" chore a
    // day early produces a successor with the very same due date.
    var resolving by remember(detail.history.size) { mutableStateOf(false) }
    fun resolve(action: () -> Unit) {
        if (resolving) return
        resolving = true
        action()
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "outstanding") {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Only "due", never "overdue": the date says so already, and overdue is not a
                // judgement worth a second word.
                Text(
                    text = stringResource(R.string.due_on, dates.format(detail.outstanding.dueDate)),
                    style = MaterialTheme.typography.titleMedium,
                )
                recurrenceText(detail.chore.recurrence, locale)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { resolve(onSkip) }, enabled = !resolving) {
                        Text(stringResource(R.string.skip))
                    }
                    Button(onClick = { resolve(onComplete) }, enabled = !resolving) {
                        Text(stringResource(R.string.done))
                    }
                }
            }
        }

        item(key = "history-header") {
            Text(
                text = stringResource(R.string.history),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (detail.history.isEmpty()) {
            item(key = "history-empty") {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        // Keyed by position from the oldest end: the history is append-only and newest first,
        // so an entry's distance from the bottom never changes, while its index from the top
        // moves on every resolution.
        itemsIndexed(detail.history, key = { index, _ -> detail.history.size - index }) { _, resolution ->
            ResolutionRow(resolution, dates)
        }
    }
}

@Composable
private fun ResolutionRow(resolution: Resolution, dates: DateTimeFormatter) {
    val due = dates.format(resolution.dueDate)
    ListItem(
        headlineContent = {
            Text(
                stringResource(
                    when (resolution) {
                        is Resolution.Completion -> R.string.resolution_done
                        is Resolution.Skip -> when (resolution.kind) {
                            Resolution.Skip.Kind.Manual -> R.string.resolution_skipped
                            Resolution.Skip.Kind.Displaced -> R.string.resolution_displaced
                        }
                    },
                ),
            )
        },
        supportingContent = {
            // Only a completion says when it happened, and only when that was not the due
            // date: early and late are both worth seeing, on-the-day is the due date again.
            // A skip's moment is when it was recorded, which for a displaced one is merely
            // when the app next looked, and means nothing to the user.
            val doneOn = (resolution as? Resolution.Completion)
                ?.let { it.at.toLocalDateHere() }
                ?.takeIf { it != resolution.dueDate }
            Text(
                if (doneOn == null) {
                    stringResource(R.string.due_on, due)
                } else {
                    stringResource(R.string.resolution_done_on, due, dates.format(doneOn))
                },
            )
        },
    )
}

/**
 * The recurrence in the words the editor offered it in, or null for a period the editor
 * cannot express — unreachable from this app, and better left unsaid than rounded.
 */
@Composable
private fun recurrenceText(recurrence: Recurrence, locale: Locale): String? = when (recurrence) {
    is Recurrence.OnWeekdays -> {
        // In the locale's week order, as the editor's picker lays them out.
        val first = WeekFields.of(locale).firstDayOfWeek
        val days = (0L until 7L).map(first::plus).filter { it in recurrence.days }
        stringResource(R.string.on_weekdays, days.joinToString { it.getDisplayName(TextStyle.SHORT, locale) })
    }

    is Recurrence.Every -> recurrence.period.inOneUnit()?.let { (count, unit) ->
        val plural = when (unit) {
            PeriodUnit.Days -> R.plurals.every_days
            PeriodUnit.Weeks -> R.plurals.every_weeks
            PeriodUnit.Months -> R.plurals.every_months
        }
        pluralStringResource(plural, count, count)
    }
}
