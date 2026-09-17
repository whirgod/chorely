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

    // Careful when wiring the form: ViewModels are scoped to their nav entry, so popping the
    // editor clears this one and cancels the scope. Saving and then navigating back in the
    // same gesture must not leave the write in a cancelled scope — the write needs to outlive
    // the screen, which viewModelScope by itself does not guarantee.
    fun onSave(id: ChoreId?, draft: ChoreDraft) = viewModelScope.launch {
        if (id == null) chores.add(draft) else chores.edit(id, draft)
        // A first chore makes the digest worth scheduling; sync is idempotent, so calling
        // it on every save is cheaper than working out whether this save was the first.
        reminders.sync()
    }
}
