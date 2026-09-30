package at.woergoetter.chorely.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.woergoetter.chorely.ApplicationScope
import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Chores
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val chores: Chores,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    /** Null until the store has emitted, so an archive still loading never reads as empty. */
    val archived: StateFlow<List<Chore>?> = chores.archived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Both writes go on the application scope, as the chore screen's do: this screen can be
    // popped, and Restore or a confirmed Delete followed at once by Back is one motion. The
    // row vanishes as the tap lands, which is the user being told it happened.

    fun onRestore(id: ChoreId) = applicationScope.launch { chores.restore(id) }

    /** The deliberate act that discards the history too; archiving never does. */
    fun onDelete(id: ChoreId) = applicationScope.launch { chores.delete(id) }
}
