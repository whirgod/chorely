package at.woergoetter.chorely

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
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
import at.woergoetter.chorely.ui.chore.ChoreViewModel
import at.woergoetter.chorely.ui.settings.SettingsScreen

/**
 * Pops the top entry, unless it is the last one: NavDisplay requires a non-empty back stack
 * and throws on an empty one, and a screen can ask for that without meaning to, since popping
 * leaves it composed and tappable for the length of its exit transition. System back cannot
 * get here to be refused: NavDisplay only intercepts it while the current scene has entries
 * behind it, and otherwise lets the activity finish, which is how back on the agenda leaves
 * the app.
 */
internal fun MutableList<NavKey>.back() {
    if (size > 1) removeLastOrNull()
}

/**
 * Pushes [key], unless it is already on top. The same point about a transition, on the way in:
 * the screen that pushed is still composed and tappable while the new one arrives, so a second
 * tap on the button that opened it pushes an equal NavKey again. Nav3 keys an entry's saved
 * state and its ViewModelStore by the key, so two equal keys are one slot shared by two
 * entries — and popping one of them leaves the other on screen holding a spent one-shot latch,
 * with both its buttons disabled. Equal keys adjacent on the stack are never wanted, so
 * refusing the push is the whole fix.
 */
internal fun MutableList<NavKey>.go(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/**
 * The back stack, and the one fact about it that NavDisplay does not keep: whether the entry
 * on top has *settled* — finished arriving — or is still in the transition that brought it.
 *
 * A screen stays composed and tappable for the length of its exit transition, and the system
 * back gesture reaches NavDisplay throughout. Screens latch their own buttons, but a latch
 * can only refuse what its screen originates, so two things are refused here instead:
 *
 * - A screen's navigation, from anywhere but the top: a tap that lands on a screen already
 *   on its way out must not pop or push on behalf of the screen that replaced it.
 * - A system back before the top has settled: Cancel then a swipe would otherwise cost two
 *   screens, and on `[Agenda, Chore]` Back then a swipe would leave the app.
 *
 * A screen popping itself before it has settled stays allowed — the editor finding its chore
 * gone while it is still sliding in has to be able to leave.
 */
@Stable
class Navigator(private val stack: MutableList<NavKey>) {

    /** The entry that last reached `ON_RESUME` while on top. Nav3 resumes one only once settled. */
    private var settled: NavKey? by mutableStateOf(stack.lastOrNull())

    /** Whether [key] is on top, and so may still act: a screen off the top is on its way out. */
    fun isActive(key: NavKey): Boolean = stack.lastOrNull() == key

    /** Whether the top entry has finished arriving. */
    val isSettled: Boolean get() = stack.lastOrNull().let { it != null && it == settled }

    /** [from] leaving itself; refused unless it is on top. */
    fun back(from: NavKey) {
        if (isActive(from)) stack.back()
    }

    /** [from] opening [to]; refused unless [from] is on top. */
    fun go(from: NavKey, to: NavKey) {
        if (isActive(from)) stack.go(to)
    }

    /** The system back gesture or button; refused until the top has settled. */
    fun systemBack() {
        if (isSettled) stack.back()
    }

    /** Called when [key]'s entry resumes. */
    fun settle(key: NavKey) {
        if (isActive(key)) settled = key
    }
}

@Composable
fun ChorelyNavigation() {
    val backStack = rememberNavBackStack(AgendaRoute)
    val navigator = remember(backStack) { Navigator(backStack) }

    GuardedNavDisplay(navigator, backStack) {
        entry<AgendaRoute> { key ->
            AgendaScreen(
                viewModel = hiltViewModel(),
                onOpenChore = { navigator.go(key, ChoreRoute(it.value)) },
                onAddChore = { navigator.go(key, ChoreEditorRoute()) },
                onOpenArchive = { navigator.go(key, ArchiveRoute) },
                onOpenSettings = { navigator.go(key, SettingsRoute) },
            )
        }
        entry<ChoreRoute> { route ->
            ChoreScreen(
                // The entry's ViewModelStore is keyed by the route, so this factory runs
                // once per ChoreRoute and never hands one chore's ViewModel to another.
                viewModel = hiltViewModel<ChoreViewModel, ChoreViewModel.Factory>(
                    creationCallback = { it.create(route) },
                ),
                isActive = { navigator.isActive(route) },
                onEdit = { navigator.go(route, ChoreEditorRoute(route.choreId)) },
                onBack = { navigator.back(route) },
            )
        }
        entry<ChoreEditorRoute> { route ->
            ChoreEditorScreen(
                choreId = route.choreId?.let(::ChoreId),
                viewModel = hiltViewModel(),
                isActive = { navigator.isActive(route) },
                onDone = { navigator.back(route) },
            )
        }
        entry<ArchiveRoute> { key ->
            ArchiveScreen(
                viewModel = hiltViewModel(),
                isActive = { navigator.isActive(key) },
                onBack = { navigator.back(key) },
            )
        }
        entry<SettingsRoute> { key ->
            SettingsScreen(viewModel = hiltViewModel(), onBack = { navigator.back(key) })
        }
    }
}

/**
 * NavDisplay with [navigator]'s guards wired in: system back goes through
 * [Navigator.systemBack], each entry reports its resume to [Navigator.settle], and a back
 * that NavDisplay would not intercept — on a one-entry stack, where the activity would finish
 * — is swallowed until the top has settled.
 *
 * Separate from [ChorelyNavigation] so the guards can be tested through a real transition
 * without the app's screens.
 */
@Composable
fun GuardedNavDisplay(
    navigator: Navigator,
    backStack: List<NavKey>,
    entries: EntryProviderScope<NavKey>.() -> Unit,
) {
    val provider = entryProvider<NavKey> { entries() }
    NavDisplay(
        backStack = backStack,
        onBack = { navigator.systemBack() },
        // Without the ViewModelStore decorator every hiltViewModel() below resolves against
        // the Activity's store: one instance per type shared by every entry, never cleared
        // when an entry is popped. Passing `entryDecorators` replaces NavDisplay's defaults
        // rather than adding to them, so the saveable-state one has to be named again here.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { key ->
            val entry = provider(key)
            NavEntry(navEntry = entry) { _: NavKey ->
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { navigator.settle(key) }
                entry.Content()
            }
        },
    )
    // Registered after NavDisplay's own, so it is asked first; only ever on while unsettled.
    // This, not systemBack()'s own check, is what refuses an early back on the emulator the
    // tests run on — the check stays as a backstop should NavDisplay's handler ever be asked
    // first. GuardedNavDisplayTest fails with both removed.
    BackHandler(enabled = !navigator.isSettled) {}
}
