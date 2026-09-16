package at.woergoetter.chorely.ui.chore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.woergoetter.chorely.domain.ChoreId

/**
 * One chore: where it stands, and the history of every time it was done.
 *
 * Deliberately a history and not a score — no streaks, no completion rate. See the Rejected
 * section of BACKLOG.md before adding either.
 *
 * TODO: lay this out properly. The seams below it are settled; the presentation is not.
 */
@Composable
fun ChoreScreen(
    choreId: ChoreId,
    viewModel: ChoreViewModel,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail by viewModel.detail(choreId).collectAsStateWithLifecycle(initialValue = null)

    Column(modifier = modifier.padding(16.dp)) {
        val current = detail ?: return@Column
        Text(current.chore.name)
        Text("Due ${current.outstanding.dueDate}")
        current.history.forEach { Text("${it.dueDate} — ${it::class.simpleName}") }
    }
}
