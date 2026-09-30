package at.woergoetter.chorely.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.woergoetter.chorely.R
import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.ui.rememberDateFormatter
import at.woergoetter.chorely.ui.toLocalDateHere

/**
 * Archived chores, each with the two ways out of the archive.
 *
 * Restore is one tap and Delete asks first: delete discards the history and cannot be undone,
 * while restore — like archive, on the chore screen — loses no history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    isActive: () -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val archived by viewModel.archived.collectAsStateWithLifecycle()

    // The chore awaiting a yes to Delete, by id so it survives rotation. A chore that leaves
    // the archive by other means while the dialog is up drops the dialog with it.
    var confirmingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    // Chores whose Restore or Delete has been tapped and not yet landed. The row stays composed
    // until the store emits without it, and the buttons should not offer a second write that
    // the store would only refuse. Not saved: the write it waits on lives in memory, so a
    // latch that outlived the process would disable a row whose write never happened, and
    // the store already makes a rotation's second tap harmless. Pruned to what is still
    // archived, so a chore that leaves and comes back is not born disabled.
    var spent by remember { mutableStateOf(emptySet<ChoreId>()) }
    LaunchedEffect(archived) {
        val still = archived.orEmpty().mapTo(mutableSetOf()) { it.id }
        spent = spent intersect still
    }
    // And not once the screen is off the top: a Restore landing while it slides out would be a
    // write made from a screen the user has already left.
    fun spend(id: ChoreId, write: (ChoreId) -> Unit) {
        if (id in spent || !isActive()) return
        spent = spent + id
        write(id)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archive)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val shown = archived ?: return@Scaffold
        if (shown.isEmpty()) {
            Text(
                text = stringResource(R.string.archive_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        ArchiveList(
            chores = shown,
            spent = spent,
            onRestore = { spend(it, viewModel::onRestore) },
            onDelete = { if (isActive()) confirmingDelete = it.value },
            modifier = Modifier.padding(padding),
        )
    }

    val pending = archived?.find { it.id.value == confirmingDelete }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text(stringResource(R.string.delete_chore_title, pending.name)) },
            text = { Text(stringResource(R.string.delete_chore_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = null
                        spend(pending.id, viewModel::onDelete)
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ArchiveList(
    chores: List<Chore>,
    spent: Set<ChoreId>,
    onRestore: (ChoreId) -> Unit,
    onDelete: (ChoreId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(chores, key = { it.id.value }) { chore ->
            val enabled = chore.id !in spent
            ListItem(
                headlineContent = { Text(chore.name) },
                supportingContent = {
                    // The actions sit under the name rather than beside it: trailing content
                    // is not constrained, so at a large font scale two buttons beside the
                    // name squeeze it to a sliver — and the name is the only thing saying
                    // which chore a Delete is for.
                    Column {
                        chore.archivedAt?.let {
                            Text(stringResource(R.string.archived_on, dates.format(it.toLocalDateHere())))
                        }
                        // Named per row, so a screen reader says which chore a Delete is for
                        // rather than reading the same two buttons down the list. The visible
                        // word is cleared, as on the agenda's add button, so it is not read twice.
                        val deleteLabel = stringResource(R.string.delete_named, chore.name)
                        val restoreLabel = stringResource(R.string.restore_named, chore.name)
                        // Flow, so that at the largest scales the second button wraps rather than
                        // clipping.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { onRestore(chore.id) },
                                enabled = enabled,
                                modifier = Modifier.semantics { contentDescription = restoreLabel },
                            ) { Text(stringResource(R.string.restore), modifier = Modifier.clearAndSetSemantics {}) }
                            TextButton(
                                onClick = { onDelete(chore.id) },
                                enabled = enabled,
                                modifier = Modifier.semantics { contentDescription = deleteLabel },
                            ) { Text(stringResource(R.string.delete), modifier = Modifier.clearAndSetSemantics {}) }
                        }
                    }
                },
            )
        }
    }
}
