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
     * The other ViewModels here expose a StateFlow instead, and this one cannot: that needs
     * the chore id at construction, and Navigation 3 hands its key to the entry rather than
     * to a SavedStateHandle, so nothing carries the id into Hilt's factory. The route that
     * would is assisted injection with a `creationCallback` at the entry, plus a
     * `rememberViewModelStoreNavEntryDecorator()` on the NavDisplay so the ViewModel is
     * scoped to the entry at all — both of which live in Navigation.kt.
     */
    fun detail(id: ChoreId): Flow<ChoreDetail?> = chores.detail(id)

    fun onComplete(id: ChoreId) = viewModelScope.launch { chores.complete(id) }

    fun onSkip(id: ChoreId) = viewModelScope.launch { chores.skip(id) }

    fun onArchive(id: ChoreId) = viewModelScope.launch { chores.archive(id) }
}
