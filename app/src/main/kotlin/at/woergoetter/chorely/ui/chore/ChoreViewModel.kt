package at.woergoetter.chorely.ui.chore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.woergoetter.chorely.domain.ChoreDetail
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Chores
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChoreViewModel @Inject constructor(
    private val chores: Chores,
) : ViewModel() {

    /**
     * A cold Flow, built afresh on every call, which the caller must hold onto for as long
     * as it collects it — see the note in ChoreScreen.
     *
     * The other ViewModels here expose a StateFlow instead, and this one does not yet: that
     * needs the chore id at construction, and Navigation 3 hands its key to the entry rather
     * than to a SavedStateHandle, so nothing carries the id into Hilt's factory on its own.
     * The remaining step is assisted injection — `@HiltViewModel(assistedFactory = ...)` here
     * and `hiltViewModel(creationCallback = { it.create(route) })` at the entry — after which
     * the id can move to the constructor and `detail` can become a StateFlow like its peers.
     * Left until the screen itself is built, since its shape will decide what this exposes.
     */
    fun detail(id: ChoreId): Flow<ChoreDetail?> = chores.detail(id)

    fun onComplete(id: ChoreId) = viewModelScope.launch { chores.complete(id) }

    fun onSkip(id: ChoreId) = viewModelScope.launch { chores.skip(id) }

    fun onArchive(id: ChoreId) = viewModelScope.launch { chores.archive(id) }
}
