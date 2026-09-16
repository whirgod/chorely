package at.woergoetter.chorely.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * One reminder time for the whole app. Per-chore times are rejected in BACKLOG.md.
 *
 * TODO: add the time picker and the POST_NOTIFICATIONS permission request.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time by viewModel.reminderTime.collectAsStateWithLifecycle()

    Column(modifier = modifier.padding(16.dp)) {
        Text(time?.let { "Daily reminder at $it" } ?: "Reminders off")
    }
}
