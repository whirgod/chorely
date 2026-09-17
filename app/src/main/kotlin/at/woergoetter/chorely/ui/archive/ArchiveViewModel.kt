package at.woergoetter.chorely.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Chores
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val chores: Chores,
) : ViewModel() {

    val archived: StateFlow<List<Chore>> = chores.archived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onRestore(id: ChoreId) = viewModelScope.launch { chores.restore(id) }

    /** The deliberate act that discards the history too; archiving never does. */
    fun onDelete(id: ChoreId) = viewModelScope.launch { chores.delete(id) }
}
