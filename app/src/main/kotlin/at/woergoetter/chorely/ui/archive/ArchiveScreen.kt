package at.woergoetter.chorely.ui.archive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** TODO: build the list. Restore and delete are one call each on the view model. */
@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val archived by viewModel.archived.collectAsStateWithLifecycle()

    Column(modifier = modifier.padding(16.dp)) {
        archived.forEach { Text(it.name) }
    }
}
