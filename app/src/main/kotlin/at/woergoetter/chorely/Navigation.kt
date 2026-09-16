package at.woergoetter.chorely

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.ui.agenda.AgendaScreen
import at.woergoetter.chorely.ui.archive.ArchiveScreen
import at.woergoetter.chorely.ui.chore.ChoreEditorScreen
import at.woergoetter.chorely.ui.chore.ChoreScreen
import at.woergoetter.chorely.ui.settings.SettingsScreen

@Composable
fun ChorelyNavigation() {
    val backStack = rememberNavBackStack(AgendaRoute)
    fun back() = backStack.removeLastOrNull()

    NavDisplay(
        backStack = backStack,
        onBack = { back() },
        entryProvider = entryProvider {
            entry<AgendaRoute> {
                AgendaScreen(
                    viewModel = hiltViewModel(),
                    onOpenChore = { backStack.add(ChoreRoute(it.value)) },
                    onAddChore = { backStack.add(ChoreEditorRoute()) },
                    onOpenArchive = { backStack.add(ArchiveRoute) },
                    onOpenSettings = { backStack.add(SettingsRoute) },
                )
            }
            entry<ChoreRoute> { route ->
                ChoreScreen(
                    choreId = ChoreId(route.choreId),
                    viewModel = hiltViewModel(),
                    onEdit = { backStack.add(ChoreEditorRoute(route.choreId)) },
                    onBack = { back() },
                )
            }
            entry<ChoreEditorRoute> { route ->
                ChoreEditorScreen(
                    choreId = route.choreId?.let(::ChoreId),
                    viewModel = hiltViewModel(),
                    onDone = { back() },
                )
            }
            entry<ArchiveRoute> {
                ArchiveScreen(viewModel = hiltViewModel(), onBack = { back() })
            }
            entry<SettingsRoute> {
                SettingsScreen(viewModel = hiltViewModel(), onBack = { back() })
            }
        },
    )
}
