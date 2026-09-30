package at.woergoetter.chorely.ui.settings

import at.woergoetter.chorely.domain.ReminderSettings
import at.woergoetter.chorely.reminder.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * What the settings ViewModel owes the schedule: a changed reminder time is stored and
 * then synced, in that order, on a scope the screen cannot cancel.
 *
 * Like `ChoreEditorViewModelTest`, no main-dispatcher rule: the one write never touches
 * `viewModelScope`, and `reminderTime` is not collected here.
 */
@OptIn(ExperimentalCoroutinesApi::class) // advanceUntilIdle
class SettingsViewModelTest {

    /** Every call on either fake, in order, so the tests can assert the sequence. */
    private val calls = mutableListOf<String>()
    private val settings = FakeReminderSettings(calls)
    private val reminders = FakeReminders(settings, calls)

    private val applicationJob = SupervisorJob()

    private fun TestScope.viewModel() = SettingsViewModel(
        settings = settings,
        reminders = reminders,
        applicationScope = CoroutineScope(applicationJob + StandardTestDispatcher(testScheduler)),
    )

    @Test
    fun `onReminderTimeChanged launches on the application scope, not on one the screen can cancel`() = runTest {
        val change = viewModel().onReminderTimeChanged(LocalTime.of(19, 0))

        // As in the editor's test: the parent job is the only evidence that survives nothing
        // in a test ever popping the screen.
        assertTrue("the change must belong to the application scope", change in applicationJob.children)

        advanceUntilIdle()
    }

    @Test
    fun `setting a time stores it, then syncs against the stored value`() = runTest {
        viewModel().onReminderTimeChanged(LocalTime.of(19, 30))
        advanceUntilIdle()

        assertEquals(listOf("set 19:30", "sync at 19:30"), calls)
    }

    @Test
    fun `switching reminders off stores null, then syncs so the pending digest is cancelled`() = runTest {
        settings.time.value = LocalTime.of(8, 0)

        viewModel().onReminderTimeChanged(null)
        advanceUntilIdle()

        assertEquals(listOf("set null", "sync at null"), calls)
    }
}

private class FakeReminderSettings(private val calls: MutableList<String>) : ReminderSettings {

    /** Null to begin with, as on a fresh install: reminders are opt-in. */
    val time = MutableStateFlow<LocalTime?>(null)

    override fun reminderTime(): Flow<LocalTime?> = time

    override suspend fun setReminderTime(time: LocalTime?) {
        calls += "set $time"
        this.time.value = time
    }
}

/** Records what it would have scheduled against, which is the stored time and nothing else. */
private class FakeReminders(
    private val settings: FakeReminderSettings,
    private val calls: MutableList<String>,
) : Reminders {

    override suspend fun sync() {
        calls += "sync at ${settings.time.value}"
    }
}
