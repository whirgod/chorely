package at.woergoetter.chorely.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.woergoetter.chorely.domain.ReminderSettings
import at.woergoetter.chorely.reminder.Reminders
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : ViewModel() {

    val reminderTime: StateFlow<LocalTime?> = settings.reminderTime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Writing the time and re-deriving the schedule from it are one action, never two. */
    fun onReminderTimeChanged(time: LocalTime?) = viewModelScope.launch {
        settings.setReminderTime(time)
        reminders.sync()
    }
}
