package at.woergoetter.chorely.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.woergoetter.chorely.domain.Agenda
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Chores
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Nothing here knows about occurrences, catch-up or anchoring: it forwards user intent to
 * [Chores] and exposes what comes back. If a due-date rule ever needs to be expressed in a
 * ViewModel, the rule is in the wrong place.
 */
@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val chores: Chores,
) : ViewModel() {

    /** Null until the store has emitted: a seed value is not something the user was shown. */
    val agenda: StateFlow<Agenda?> = chores.agenda()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Marks each agenda seen, through its own day, as it reaches the screen — to be run while
     * the overview is resumed, and cancelled when it is not. This is the other half of the
     * auto-skip guard: an occurrence counts as seen if the user was shown it here or by the
     * daily digest, and only a seen occurrence may be recorded as a lapse.
     *
     * Every emission and not just the first: resumed on a new day after the store has been let
     * go, the screen first shows the agenda it was left with — yesterday's, still held in
     * [agenda] — and today's once the store answers. Marking only the first would record
     * yesterday for a screen showing today. (Resumed sooner, the store is never asked again, and
     * yesterday's stays on screen and is all that is marked: late, never early.) Nothing before
     * the first emission: a seed value is not something the user was shown.
     *
     * The write itself goes on [viewModelScope], so leaving the screen a moment after an agenda
     * arrived does not roll back the record that it was shown.
     */
    suspend fun markShownWhileResumed() {
        agenda.filterNotNull().map { it.day }.distinctUntilChanged().collect { day ->
            viewModelScope.launch { chores.markSeen(through = day) }
        }
    }

    fun onComplete(id: ChoreId) = viewModelScope.launch { chores.complete(id) }

    fun onSkip(id: ChoreId) = viewModelScope.launch { chores.skip(id) }
}
