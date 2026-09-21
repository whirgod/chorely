package at.woergoetter.chorely.ui.chore

import at.woergoetter.chorely.domain.Agenda
import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreDetail
import at.woergoetter.chorely.domain.ChoreDraft
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Chores
import at.woergoetter.chorely.domain.DueChore
import at.woergoetter.chorely.domain.Occurrence
import at.woergoetter.chorely.domain.Recurrence
import at.woergoetter.chorely.reminder.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Period

/**
 * What the editor's ViewModel owes the screen: a chore to prefill from, and a save that
 * outlives the screen that asked for it and writes nothing else.
 *
 * There is no main-dispatcher rule here because there is nothing to rule: the ViewModel
 * never touches `viewModelScope`, which is the whole point of the first test below.
 */
@OptIn(ExperimentalCoroutinesApi::class) // advanceUntilIdle
class ChoreEditorViewModelTest {

    private val chores = FakeChores()

    /**
     * Stands in for the injected `@ApplicationScope` scope. Its own [SupervisorJob], exactly
     * as the real one has, is what the first test interrogates; the scheduler is the test's,
     * so `advanceUntilIdle` drives the writes launched on it.
     */
    private val applicationJob = SupervisorJob()

    private fun TestScope.viewModel() = ChoreEditorViewModel(
        chores = chores,
        applicationScope = CoroutineScope(applicationJob + StandardTestDispatcher(testScheduler)),
    )

    private val draft = ChoreDraft("Descale the kettle", Recurrence.Every(Period.ofMonths(3)))

    @Test
    fun `onSave launches on the application scope, not on one the screen can cancel`() = runTest {
        val save = viewModel().onSave(null, draft)

        // The parent job is the only honest evidence. A write launched on `viewModelScope`
        // would still be observed landing here, because nothing in a test pops the editor —
        // so watching the chore appear would go on passing after the bug came back.
        assertTrue("the save must belong to the application scope", save in applicationJob.children)

        // Let the queued write run, so the test leaves nothing pending on the shared scheduler.
        advanceUntilIdle()
    }

    @Test
    fun `load returns the chore to prefill the form from`() = runTest {
        val id = chores.given(chore(name = "Wipe the skirting boards"))

        assertEquals("Wipe the skirting boards", viewModel().load(id)?.name)
    }

    @Test
    fun `load returns null when the chore is gone`() = runTest {
        // Deleted from the archive while the editor was being opened for it, say.
        assertNull(viewModel().load(ChoreId(404)))
    }

    @Test
    fun `onSave with no id adds a chore`() = runTest {
        viewModel().onSave(null, draft)
        advanceUntilIdle()

        assertEquals(listOf(draft), chores.added)
        assertEquals(emptyList<Pair<ChoreId, ChoreDraft>>(), chores.edited)
    }

    @Test
    fun `onSave with an id edits that chore`() = runTest {
        val id = chores.given(chore(name = "Vacuum"))

        viewModel().onSave(id, draft)
        advanceUntilIdle()

        assertEquals(listOf(id to draft), chores.edited)
        assertEquals(emptyList<ChoreDraft>(), chores.added)
    }

    @Test
    fun `saving a chore cannot touch the reminder schedule`() {
        // `Reminders.sync()` reads the stored reminder time and nothing else — the chore
        // list cannot make a digest schedulable — and it re-enqueues with REPLACE, so a
        // save at 08:40 would throw away this morning's digest, still pending after a
        // night of Doze, and aim the next one at tomorrow.
        //
        // The editor therefore has no `Reminders` to call. That absence is what this
        // asserts: a dependency that is not there cannot be watched by a fake, and a
        // constructor argument is the one place reintroducing it would show up.
        val dependencies = ChoreEditorViewModel::class.java.declaredConstructors.single().parameterTypes

        assertFalse(
            "the editor must not be able to reach the reminder schedule",
            dependencies.any { it == Reminders::class.java },
        )
    }

    private fun chore(id: Long = 1, name: String = "Vacuum") = Chore(
        id = ChoreId(id),
        name = name,
        recurrence = Recurrence.Every(Period.ofDays(7)),
        anchoredOn = LocalDate.of(2026, 9, 21),
    )
}

/**
 * Just enough of [Chores] for the editor: the one read it makes and the two writes.
 *
 * Every other method throws instead of returning a plausible empty value, so a call the
 * editor has no business making fails the test that makes it rather than passing quietly.
 */
private class FakeChores : Chores {

    private val stored = MutableStateFlow<Map<ChoreId, Chore>>(emptyMap())

    val added = mutableListOf<ChoreDraft>()
    val edited = mutableListOf<Pair<ChoreId, ChoreDraft>>()

    /** Puts [chore] in the store as though it had always been there. */
    fun given(chore: Chore): ChoreId {
        stored.value += chore.id to chore
        return chore.id
    }

    override fun detail(id: ChoreId): Flow<ChoreDetail?> = stored.map { chores ->
        // The outstanding occurrence is derived rather than stored, and the editor reads
        // only the chore off the detail, so the anchor date is as much as it needs to be.
        chores[id]?.let { ChoreDetail(it, Occurrence(id, it.anchoredOn), emptyList()) }
    }

    override suspend fun add(draft: ChoreDraft): ChoreId {
        added += draft
        val id = ChoreId(stored.value.size + 1L)
        stored.value += id to Chore(id, draft.name, draft.recurrence, ANCHOR)
        return id
    }

    override suspend fun edit(id: ChoreId, draft: ChoreDraft) {
        edited += id to draft
        val chore = stored.value.getValue(id)
        stored.value += id to chore.copy(name = draft.name, recurrence = draft.recurrence)
    }

    override fun agenda(): Flow<Agenda> = unused()

    override fun archived(): Flow<List<Chore>> = unused()

    override suspend fun due(): List<DueChore> = unused()

    override suspend fun markSeen() = unused()

    override suspend fun complete(id: ChoreId) = unused()

    override suspend fun skip(id: ChoreId) = unused()

    override suspend fun archive(id: ChoreId) = unused()

    override suspend fun restore(id: ChoreId) = unused()

    override suspend fun delete(id: ChoreId) = unused()

    private fun unused(): Nothing = error("the chore editor does not use this")

    private companion object {
        /** Real anchoring is the domain's job and is pinned in `:core:domain`; any day does here. */
        val ANCHOR: LocalDate = LocalDate.of(2026, 9, 21)
    }
}
