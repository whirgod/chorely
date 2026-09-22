package at.woergoetter.chorely

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
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
    // Never the last entry: NavDisplay requires a non-empty back stack and throws on an empty
    // one, and a screen can ask for that without meaning to, since popping leaves it composed
    // and tappable for the length of its exit transition. System back cannot get here to be
    // refused: NavDisplay only intercepts it while the current scene has entries behind it,
    // and otherwise lets the activity finish, which is how back on the agenda leaves the app.
    fun back() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    // The same point about a transition, on the way in: the screen that pushed is still
    // composed and tappable while the new one arrives, so a second tap on the button that
    // opened it pushes an equal NavKey again. Nav3 keys an entry's saved state and its
    // ViewModelStore by the key, so two equal keys are one slot shared by two entries — and
    // popping one of them leaves the other on screen holding a spent one-shot latch, with
    // both its buttons disabled. Equal keys adjacent on the stack are never wanted, so
    // refusing the push is the whole fix.
    fun go(key: NavKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { back() },
        // Without the ViewModelStore decorator every hiltViewModel() below resolves against
        // the Activity's store: one instance per type shared by every entry, never cleared
        // when an entry is popped. Passing `entryDecorators` replaces NavDisplay's defaults
        // rather than adding to them, so the saveable-state one has to be named again here.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<AgendaRoute> {
                AgendaScreen(
                    viewModel = hiltViewModel(),
                    onOpenChore = { go(ChoreRoute(it.value)) },
                    onAddChore = { go(ChoreEditorRoute()) },
                    onOpenArchive = { go(ArchiveRoute) },
                    onOpenSettings = { go(SettingsRoute) },
                )
            }
            entry<ChoreRoute> { route ->
                ChoreScreen(
                    choreId = ChoreId(route.choreId),
                    viewModel = hiltViewModel(),
                    onEdit = { go(ChoreEditorRoute(route.choreId)) },
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
