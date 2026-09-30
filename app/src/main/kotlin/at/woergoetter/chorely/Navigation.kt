package at.woergoetter.chorely

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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
 *
 * Settled means the top has resumed since it became the top. NavDisplay holds every entry at
 * `STARTED` for the length of a transition and resumes the one on top once it ends, so that
 * resume is the moment it has arrived. Two things this deliberately does not mean:
 *
 * - "Resumed right now". A predictive back gesture seeks the transition as the finger moves,
 *   which pauses the settled top until the gesture commits — and the gesture must not be
 *   refused for the pause it caused itself. Nor must a system dialog's pause of the activity.
 * - "The last entry to settle". A quick pop can make that match the new top while it is still
 *   sliding in, which is exactly the early back this exists to refuse.
 *
 * Only a change of top clears it. An entry popped and pushed back before NavDisplay
 * recomposed never left `RESUMED`, so no resume will come; one resumed right now is settled
 * too, which is what keeps that from locking back out for good.
 */
@Stable
class Navigator(private val stack: MutableList<NavKey>) {

    /** The top as of its last resume; cleared whenever the top changes. */
    private var settledTop: NavKey? by mutableStateOf(stack.lastOrNull())

    /**
     * How many live compositions of each key are `RESUMED` right now. Counted rather than a
     * set, so that two compositions of equal keys — a pane layout, say — cannot un-resume each
     * other.
     */
    private val resumed = mutableStateMapOf<NavKey, Int>()

    /** Whether [key] is on top, and so may still act: a screen off the top is on its way out. */
    fun isActive(key: NavKey): Boolean = stack.lastOrNull() == key

    /** Whether the top entry has finished arriving. */
    val isSettled: Boolean
        get() {
            val top = stack.lastOrNull() ?: return false
            return top == settledTop || (resumed[top] ?: 0) > 0
        }

    /** [from] leaving itself; refused unless it is on top. */
    fun back(from: NavKey) {
        if (isActive(from)) changeTop { stack.back() }
    }

    /** [from] opening [to]; refused unless [from] is on top. */
    fun go(from: NavKey, to: NavKey) {
        if (isActive(from)) changeTop { stack.go(to) }
    }

    /** The system back gesture or button; refused until the top has settled. */
    fun systemBack() {
        if (isSettled) changeTop { stack.back() }
    }

    /** Called when [key]'s entry resumes. */
    fun onResumed(key: NavKey) {
        resumed[key] = (resumed[key] ?: 0) + 1
        if (isActive(key)) settledTop = key
    }

    /** Called when [key]'s entry pauses or leaves composition. */
    fun onPaused(key: NavKey) {
        val left = (resumed[key] ?: 0) - 1
        if (left > 0) resumed[key] = left else resumed.remove(key)
    }

    private inline fun changeTop(edit: () -> Unit) {
        val before = stack.lastOrNull()
        edit()
        if (stack.lastOrNull() != before) settledTop = null
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
 * [Navigator.systemBack], each entry reports its lifecycle to the navigator, an entry off the
 * top takes no input, and a back that NavDisplay would not intercept — on a one-entry stack,
 * where the activity would finish — is swallowed until the top has settled.
 *
 * "No input" is touches, focus and accessibility actions alike, so it covers every screen,
 * including any added later, and a keyboard Enter or a TalkBack double-tap as much as a
 * finger. The editor, chore and archive screens gate on [Navigator.isActive] as well, since
 * their writes cannot be taken back. [blockOffTop] exists for the test that proves the block
 * is what stops a touch.
 *
 * Separate from [ChorelyNavigation] so the guards can be tested through a real transition
 * without the app's screens.
 */
@Composable
fun GuardedNavDisplay(
    navigator: Navigator,
    backStack: List<NavKey>,
    blockOffTop: Boolean = true,
    entries: EntryProviderScope<NavKey>.() -> Unit,
) {
    val provider = remember(entries) { entryProvider<NavKey> { entries() } }
    val guarded: (NavKey) -> NavEntry<NavKey> = remember(provider, navigator, blockOffTop) {
        { key ->
            val entry = provider(key)
            NavEntry(navEntry = entry) { _: NavKey ->
                LifecycleResumeEffect(key, navigator) {
                    navigator.onResumed(key)
                    onPauseOrDispose { navigator.onPaused(key) }
                }
                val inert = blockOffTop && !navigator.isActive(key)
                Box(
                    // Installed once and consulted per event, so a block that starts mid-gesture
                    // sees the whole stream rather than a node attached halfway through it.
                    Modifier
                        .pointerInput(key, navigator, blockOffTop) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (blockOffTop && !navigator.isActive(key)) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                        .then(if (inert) Inert else Modifier),
                ) { entry.Content() }
            }
        }
    }
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
        entryProvider = guarded,
    )
    SwallowEarlyBack(navigator)
}

/**
 * Registered after NavDisplay's own handler, so it is asked first, and only ever on while the
 * top is unsettled. This, not systemBack()'s own check, is what refuses an early back on the
 * emulators the tests run on; the check stays as a backstop should NavDisplay's handler ever
 * be asked first. GuardedNavDisplayTest fails with both removed. Its own composable, so that
 * settling recomposes this and not the display.
 */
@Composable
private fun SwallowEarlyBack(navigator: Navigator) {
    BackHandler(enabled = !navigator.isSettled) {}
}

/** No focus and no semantics, so neither a keyboard nor an accessibility service can act. */
private val Inert = Modifier
    .focusProperties { canFocus = false }
    .clearAndSetSemantics {}
