package at.woergoetter.chorely.ui.chore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.woergoetter.chorely.domain.ChoreId

/**
 * Creating and editing a chore: a name, and a choice between the two anchorings —
 * "on these days" or "every N". Those two are the whole of the recurrence model; see
 * CONTEXT.md.
 *
 * TODO: build the form. The domain accepts a ChoreDraft and decides everything else.
 */
@Composable
fun ChoreEditorScreen(
    choreId: ChoreId?,
    viewModel: ChoreEditorViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(if (choreId == null) "New chore" else "Edit chore")
    }
}
