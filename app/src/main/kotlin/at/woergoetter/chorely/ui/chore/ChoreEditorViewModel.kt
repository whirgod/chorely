package at.woergoetter.chorely.ui.chore

import androidx.lifecycle.ViewModel
import at.woergoetter.chorely.ApplicationScope
import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreDraft
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Chores
import at.woergoetter.chorely.reminder.Reminders
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChoreEditorViewModel @Inject constructor(
    private val chores: Chores,
    private val reminders: Reminders,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    /**
     * The chore to prefill the form from, or null if it is gone.
     *
     * A one-shot read and not a Flow: the form is the user's copy of the chore from the
     * moment it opens, and a later emission from Room arriving underneath their typing
     * would overwrite it. The screen holds the result — the chore id cannot reach this
     * constructor, for the reason written out on `ChoreViewModel.detail`.
     */
    suspend fun load(id: ChoreId): Chore? = chores.detail(id).first()?.chore

    /**
     * Saves, on a scope that outlives the screen.
     *
     * ViewModels are scoped to their nav entry, so popping the editor clears this one and
     * cancels `viewModelScope` — and saving then navigating back is one gesture, so the
     * write would race the pop and usually lose. The user has been told the chore is saved;
     * the write has to happen whether or not the screen is still there.
     */
    fun onSave(id: ChoreId?, draft: ChoreDraft) = applicationScope.launch {
        if (id == null) chores.add(draft) else chores.edit(id, draft)
        // A first chore makes the digest worth scheduling; sync is idempotent, so calling
        // it on every save is cheaper than working out whether this save was the first.
        reminders.sync()
    }
}
