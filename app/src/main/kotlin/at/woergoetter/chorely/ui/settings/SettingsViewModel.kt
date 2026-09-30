package at.woergoetter.chorely.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.woergoetter.chorely.ApplicationScope
import at.woergoetter.chorely.domain.ReminderSettings
import at.woergoetter.chorely.reminder.Reminders
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: ReminderSettings,
    private val reminders: Reminders,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    val reminderTime: StateFlow<LocalTime?> = settings.reminderTime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Writing the time and re-deriving the schedule from it are one action, never two — and
     * it runs on a scope that outlives the screen, for the reason on `ChoreEditorViewModel.onSave`.
     * Here the half-done version is worse than a lost write: a back gesture landing between
     * the two steps would store a time with no digest scheduled for it, and nothing but a
     * reboot would notice.
     *
     * Null switches reminders off, which is also what a fresh install starts with: reminders
     * are opt-in until onboarding asks (see BACKLOG.md).
     */
    fun onReminderTimeChanged(time: LocalTime?) = applicationScope.launch {
        settings.setReminderTime(time)
        reminders.sync()
    }
}
