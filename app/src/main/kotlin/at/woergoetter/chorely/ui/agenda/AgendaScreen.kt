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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    // Being on screen is what makes an occurrence "seen"; see AgendaViewModel.onShown.
    LaunchedEffect(Unit) { viewModel.onShown() }

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
            dueSection(R.string.overdue, agenda.overdue, onOpenChore, viewModel::onComplete, viewModel::onSkip)
            dueSection(R.string.due_today, agenda.today, onOpenChore, viewModel::onComplete, viewModel::onSkip)
            dueSection(R.string.coming_up, agenda.upcoming, onOpenChore, viewModel::onComplete, viewModel::onSkip)
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
