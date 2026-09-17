package at.woergoetter.chorely.ui.agenda

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import at.woergoetter.chorely.R
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.DueChore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    viewModel: AgendaViewModel,
    onOpenChore: (ChoreId) -> Unit,
    onAddChore: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val agenda by viewModel.agenda.collectAsStateWithLifecycle()

    // Being shown what is due is what makes an occurrence "seen", so this waits for the
    // agenda to arrive rather than for the screen to open: until the first emission there is
    // nothing on screen to have seen. An agenda that arrives empty still counts — every
    // active chore is on it, so an empty one is the whole truth. See AgendaViewModel.onShown.
    //
    // Once per resume rather than once per composition: the composition survives being
    // backgrounded, so a screen first shown yesterday is shown again today without ever being
    // recomposed, and keying this on Unit would leave seenThrough stuck on the day the
    // composition began. markSeen() is idempotent within a day, so a spare resume costs
    // nothing.
    if (agenda != null) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle, viewModel) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.onShown() }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onOpenArchive) { Text(stringResource(R.string.archive)) }
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddChore) {
                Text("+")
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            val shown = agenda ?: return@LazyColumn
            dueSection(R.string.overdue, shown.overdue, onOpenChore, viewModel::onComplete, viewModel::onSkip)
            dueSection(R.string.due_today, shown.today, onOpenChore, viewModel::onComplete, viewModel::onSkip)
            dueSection(R.string.coming_up, shown.upcoming, onOpenChore, viewModel::onComplete, viewModel::onSkip)
        }
    }
}

/**
 * An empty section renders nothing at all — no "0 overdue" header. Overdue is not a
 * judgement, and a heading that only ever appears when you are behind reads like one.
 */
private fun LazyListScope.dueSection(
    @StringRes title: Int,
    entries: List<DueChore>,
    onOpenChore: (ChoreId) -> Unit,
    onComplete: (ChoreId) -> Unit,
    onSkip: (ChoreId) -> Unit,
) {
    if (entries.isEmpty()) return
    item(key = "header-$title") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    items(entries, key = { it.chore.id.value }) { entry ->
        ListItem(
            headlineContent = { Text(entry.chore.name) },
            supportingContent = { Text(entry.dueDate.toString()) },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onSkip(entry.chore.id) }) { Text(stringResource(R.string.skip)) }
                    TextButton(onClick = { onComplete(entry.chore.id) }) { Text(stringResource(R.string.done)) }
                }
            },
            modifier = Modifier.clickable { onOpenChore(entry.chore.id) },
        )
    }
}
