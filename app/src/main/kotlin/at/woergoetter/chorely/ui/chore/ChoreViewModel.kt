package at.woergoetter.chorely.ui.chore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.woergoetter.chorely.ApplicationScope
import at.woergoetter.chorely.ChoreRoute
import at.woergoetter.chorely.domain.ChoreDetail
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Chores
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the chore screen has to show, with "not read yet" and "gone" kept apart. */
sealed interface ChoreState {

    /** The store has not emitted yet: nothing to show, and nothing to act on. */
    data object Loading : ChoreState

    /**
     * No such chore. Deleting happens only on the archive screen, which this one never sits
     * above, so this is a stale key rather than a user journey — but it must not be a blank
     * screen with Done and Skip on it.
     */
    data object Gone : ChoreState

    data class Shown(val detail: ChoreDetail) : ChoreState
}

/**
 * The chore id arrives through assisted injection: Navigation 3 hands its key to the entry
 * rather than to a SavedStateHandle, so the entry passes its [ChoreRoute] to [Factory] itself.
 */
@HiltViewModel(assistedFactory = ChoreViewModel.Factory::class)
class ChoreViewModel @AssistedInject constructor(
    @Assisted route: ChoreRoute,
    private val chores: Chores,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val id = ChoreId(route.choreId)

    val state: StateFlow<ChoreState> = chores.detail(id)
        .map { detail -> detail?.let(ChoreState::Shown) ?: ChoreState.Gone }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChoreState.Loading)

    // All three writes go on the application scope, unlike the agenda's, because this screen
    // can be popped: Done and then Back is one motion, and archiving pops the screen in the
    // same tap. The user has been told each of these happened, so none may die with the entry.

    fun onComplete() = applicationScope.launch { chores.complete(id) }

    fun onSkip() = applicationScope.launch { chores.skip(id) }

    fun onArchive() = applicationScope.launch { chores.archive(id) }

    @AssistedFactory
    interface Factory {
        fun create(route: ChoreRoute): ChoreViewModel
    }
}
