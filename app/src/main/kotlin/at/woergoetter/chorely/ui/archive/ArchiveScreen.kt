package at.woergoetter.chorely.ui.archive

import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.woergoetter.chorely.R
import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreId
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Archived chores, each with the two ways out of the archive.
 *
 * Restore is one tap and Delete asks first: delete discards the history and cannot be undone,
 * while restore — like archive, on the chore screen — loses nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val archived by viewModel.archived.collectAsStateWithLifecycle()

    // The chore awaiting a yes to Delete, by id so it survives rotation. A chore that leaves
    // the archive by other means while the dialog is up drops the dialog with it.
    var confirmingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    // Chores whose Restore or Delete has been tapped and not yet landed. The row stays composed
    // until the store emits without it, and a second Delete in that window is a second write
    // at best; a second Restore is refused by the store, but the button should not offer it.
    var spent by remember { mutableStateOf(emptySet<ChoreId>()) }
    fun spend(id: ChoreId, write: (ChoreId) -> Unit) {
        if (id in spent) return
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
            onDelete = { confirmingDelete = it.value },
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
    val locale = Locale.getDefault()
    val dates = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(chores, key = { it.id.value }) { chore ->
            val enabled = chore.id !in spent
            ListItem(
                headlineContent = { Text(chore.name) },
                supportingContent = {
                    // Read in the zone the device is in now, like every other date in the app.
                    // Not `LocalDate.ofInstant`, which Android has only from API 34.
                    chore.archivedAt?.let {
                        val on = it.atZone(ZoneId.systemDefault()).toLocalDate()
                        Text(stringResource(R.string.archived_on, dates.format(on)))
                    }
                },
                trailingContent = {
                    Row {
                        TextButton(onClick = { onDelete(chore.id) }, enabled = enabled) {
                            Text(stringResource(R.string.delete))
                        }
                        TextButton(onClick = { onRestore(chore.id) }, enabled = enabled) {
                            Text(stringResource(R.string.restore))
                        }
                    }
                },
            )
        }
    }
}
