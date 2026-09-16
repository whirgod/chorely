package at.woergoetter.chorely.ui.chore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.woergoetter.chorely.domain.ChoreDraft
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Chores
import at.woergoetter.chorely.reminder.Reminders
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChoreEditorViewModel @Inject constructor(
    private val chores: Chores,
    private val reminders: Reminders,
) : ViewModel() {

    fun onSave(id: ChoreId?, draft: ChoreDraft) = viewModelScope.launch {
        if (id == null) chores.add(draft) else chores.edit(id, draft)
        // A first chore makes the digest worth scheduling; sync is idempotent, so calling
        // it on every save is cheaper than working out whether this save was the first.
        reminders.sync()
    }
}
