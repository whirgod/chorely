package at.woergoetter.chorely.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.woergoetter.chorely.domain.Agenda
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Chores
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
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
     * Called once the overview has put a real [agenda] on screen — not when it was merely
     * opened. This is the other half of the auto-skip guard: an occurrence counts as seen
     * if the user was shown it here or by the daily digest, and only a seen occurrence may
     * be recorded as a lapse, so a screen that showed nothing must not advance it.
     *
     * When that moment has arrived is the caller's to judge, because "the first emission is
     * on screen" is a fact about the UI and not a rule about due dates.
     */
    fun onShown() = viewModelScope.launch { chores.markSeen() }

    fun onComplete(id: ChoreId) = viewModelScope.launch { chores.complete(id) }

    fun onSkip(id: ChoreId) = viewModelScope.launch { chores.skip(id) }
}
