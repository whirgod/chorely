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

    fun detail(id: ChoreId): Flow<ChoreDetail?> = chores.detail(id)

    fun onComplete(id: ChoreId) = viewModelScope.launch { chores.complete(id) }

    fun onSkip(id: ChoreId) = viewModelScope.launch { chores.skip(id) }

    fun onArchive(id: ChoreId) = viewModelScope.launch { chores.archive(id) }
}
