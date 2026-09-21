package at.woergoetter.chorely.ui.chore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import at.woergoetter.chorely.R
import at.woergoetter.chorely.domain.ChoreId
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Creating and editing a chore: a name, and a choice between the two anchorings —
 * "on these days" or "every N". Those two are the whole of the recurrence model; see
 * CONTEXT.md.
 *
 * The form's rules are in [ChoreEditorState], not here: this file decides only how the
 * fields look and when they are on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoreEditorScreen(
    choreId: ChoreId?,
    viewModel: ChoreEditorViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Null means "not filled in yet", which for a new chore lasts no time at all and for an
    // edit lasts until the read returns. Saveable, so the same null also distinguishes a
    // first composition from a restore after process death: a restored form is already
    // filled in, and re-running the prefill would throw the user's edits away.
    var form by rememberSaveable(stateSaver = ChoreEditorState.Saver) {
        mutableStateOf<ChoreEditorState?>(null)
    }

    LaunchedEffect(choreId, viewModel) {
        if (form != null) return@LaunchedEffect
        if (choreId == null) {
            form = ChoreEditorState()
            return@LaunchedEffect
        }
        // Gone while the editor was being opened — there is nothing here to edit, and a
        // blank form would silently turn the Save into a no-op against a missing chore.
        val chore = viewModel.load(choreId) ?: return@LaunchedEffect onDone()
        form = ChoreEditorState.of(chore)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (choreId == null) R.string.new_chore else R.string.edit_chore))
                },
                navigationIcon = {
                    TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) }
                },
                actions = {
                    val draft = form?.toDraft()
                    // Popping does not take this entry off screen at once: NavDisplay keeps
                    // it composed and hit-testable for the length of the exit transition, so
                    // a second tap a moment after the first still reaches this button. For an
                    // edit that is a repeated write; for a new chore it is a second chore,
                    // with a history of its own, that the user then has to go and archive.
                    var saved by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = {
                            val ready = draft ?: return@TextButton
                            if (saved) return@TextButton
                            saved = true
                            viewModel.onSave(choreId, ready)
                            onDone()
                        },
                        // The latch is what makes the save one-shot; saying it here too is
                        // what keeps a latched screen from showing a button that silently
                        // does nothing. It only ever closes, so nothing can reopen it.
                        enabled = draft != null && !saved,
                    ) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        val shown = form ?: return@Scaffold
        ChoreEditorForm(
            state = shown,
            onChange = { form = it },
            modifier = Modifier
                .padding(padding)
                // Scaffold hands its insets out but does not mark them as spent, and the
                // keyboard inset below is measured from the bottom of the window, so it
                // covers the navigation bar this padding has already made room for.
                // Consuming here is what subtracts the one from the other.
                .consumeWindowInsets(padding)
                // The form is edge-to-edge under an IME that covers the count field on a
                // short screen; Scaffold's insets do not include the keyboard.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}

@Composable
private fun ChoreEditorForm(
    state: ChoreEditorState,
    onChange: (ChoreEditorState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { onChange(state.copy(name = it)) },
            label = { Text(stringResource(R.string.chore_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        RecurrenceKindPicker(
            selected = state.kind,
            onSelect = { onChange(state.copy(kind = it)) },
        )

        when (state.kind) {
            RecurrenceKind.OnWeekdays -> WeekdayPicker(
                selected = state.days,
                onToggle = { day, on -> onChange(state.withDay(day, on)) },
            )

            RecurrenceKind.Every -> PeriodPicker(
                count = state.count,
                unit = state.unit,
                onCountChange = { onChange(state.copy(count = it)) },
                onUnitChange = { onChange(state.copy(unit = it)) },
            )
        }

        // Which anchoring the choice above picked, in the terms the user would use. The
        // difference only shows itself weeks later, when a chore is done late, so saying it
        // at the moment of choosing is the only place it can be said in time to matter.
        Text(
            text = stringResource(
                when (state.kind) {
                    RecurrenceKind.OnWeekdays -> R.string.on_days_explainer
                    RecurrenceKind.Every -> R.string.every_explainer
                },
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceKindPicker(
    selected: RecurrenceKind,
    onSelect: (RecurrenceKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        RecurrenceKind.entries.forEachIndexed { index, kind ->
            SegmentedButton(
                selected = kind == selected,
                onClick = { onSelect(kind) },
                shape = SegmentedButtonDefaults.itemShape(index, RecurrenceKind.entries.size),
            ) {
                Text(
                    stringResource(
                        when (kind) {
                            RecurrenceKind.OnWeekdays -> R.string.on_days
                            RecurrenceKind.Every -> R.string.every
                        },
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayPicker(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    // Starting the week where the user's locale starts it, rather than on Monday: which day
    // a week begins on is the one thing about a weekday picker people notice immediately.
    val first = WeekFields.of(locale).firstDayOfWeek
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(DayOfWeek.entries.size) { offset ->
            val day = first.plus(offset.toLong())
            // The chip is as wide as its label, so the label is abbreviated and the whole
            // day name is what gets announced — "Mon" spelled out loud is not a weekday.
            val spoken = day.getDisplayName(TextStyle.FULL, locale)
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day, day !in selected) },
                label = {
                    Text(
                        text = day.getDisplayName(TextStyle.SHORT, locale),
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                },
                modifier = Modifier.semantics { contentDescription = spoken },
            )
        }
    }
}

/**
 * How long the count field lets the count get, in digits. Derived from the largest count
 * [ChoreEditorState] accepts rather than written out here, so the field cannot come to
 * allow a number the form would then refuse.
 */
private val MAX_COUNT_DIGITS = ChoreEditorState.MAX_COUNT.toString().length

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodPicker(
    count: String,
    unit: PeriodUnit,
    onCountChange: (String) -> Unit,
    onUnitChange: (PeriodUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = count,
            // Digits only, so the field cannot hold something the number pad would not have
            // produced — a hardware keyboard and a paste both bypass the keyboard type.
            // Bounded as well, because a count over [ChoreEditorState.MAX_COUNT] is one the
            // form will not save: a field that took the extra digit would answer that
            // keystroke by turning Save off with nothing on screen saying why, so the digit
            // is refused instead, at the one moment the user can see it being refused.
            onValueChange = { typed ->
                onCountChange(typed.filter(Char::isDigit).take(MAX_COUNT_DIGITS))
            },
            label = { Text(stringResource(R.string.every_count)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PeriodUnit.entries.forEachIndexed { index, candidate ->
                SegmentedButton(
                    selected = candidate == unit,
                    onClick = { onUnitChange(candidate) },
                    shape = SegmentedButtonDefaults.itemShape(index, PeriodUnit.entries.size),
                ) {
                    Text(
                        stringResource(
                            when (candidate) {
                                PeriodUnit.Days -> R.string.unit_days
                                PeriodUnit.Weeks -> R.string.unit_weeks
                                PeriodUnit.Months -> R.string.unit_months
                            },
                        ),
                    )
                }
            }
        }
    }
}
