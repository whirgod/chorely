package at.woergoetter.chorely.ui.chore

import at.woergoetter.chorely.ChoreRoute
import at.woergoetter.chorely.domain.Agenda
import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreDetail
import at.woergoetter.chorely.domain.ChoreDraft
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Chores
import at.woergoetter.chorely.domain.DueChore
import at.woergoetter.chorely.domain.Occurrence
import at.woergoetter.chorely.domain.Recurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.Period

/**
 * What the chore screen's ViewModel owes it: the detail of the chore its route names, and
 * writes that outlive the screen, since two of the three are made on the way out of it.
 *
 * Unlike the editor's test this one needs a main dispatcher, because [ChoreViewModel.state]
 * is shared on `viewModelScope`.
 */
@OptIn(ExperimentalCoroutinesApi::class) // setMain, advanceUntilIdle
class ChoreViewModelTest {

    private val chores = FakeDetailChores()
    private val applicationJob = SupervisorJob()
    private val main = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(id: Long = 1) = ChoreViewModel(
        route = ChoreRoute(id),
        chores = chores,
        applicationScope = CoroutineScope(applicationJob + StandardTestDispatcher(testScheduler)),
    )

    @Test
    fun `every write launches on the application scope, not on one the screen can cancel`() = runTest(main) {
        val viewModel = viewModel()

        // As in the editor's test: the parent job is the only evidence that survives nothing
        // in a test ever popping the screen. Archive in particular pops in the same tap.
        listOf(viewModel.onComplete(), viewModel.onSkip(), viewModel.onArchive()).forEach {
            assertTrue("each write must belong to the application scope", it in applicationJob.children)
        }

        advanceUntilIdle()
    }

    @Test
    fun `writes go to the chore the route names`() = runTest(main) {
        val viewModel = viewModel(id = 7)

        viewModel.onComplete()
        viewModel.onSkip()
        viewModel.onArchive()
        advanceUntilIdle()

        assertEquals(listOf("complete 7", "skip 7", "archive 7"), chores.calls)
    }

    @Test
    fun `state is loading until the store emits, then shows the route's chore`() = runTest(main) {
        chores.given(chore(id = 1, name = "Vacuum"))
        chores.given(chore(id = 2, name = "Descale the kettle"))
        val viewModel = viewModel(id = 2)
        backgroundScope.launch { viewModel.state.collect {} }

        assertEquals(ChoreState.Loading, viewModel.state.value)
        advanceUntilIdle()

        val shown = viewModel.state.value as ChoreState.Shown
        assertEquals("Descale the kettle", shown.detail.chore.name)
    }

    @Test
    fun `state is gone, not loading, once the chore no longer exists`() = runTest(main) {
        chores.given(chore(id = 1))
        val viewModel = viewModel(id = 1)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        chores.remove(ChoreId(1))
        advanceUntilIdle()

        assertEquals(ChoreState.Gone, viewModel.state.value)
    }

    @Test
    fun `state is gone once the chore is archived, so Done and Skip are not offered`() = runTest(main) {
        chores.given(chore(id = 1))
        val viewModel = viewModel(id = 1)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        chores.given(chore(id = 1).copy(archivedAt = Instant.parse("2026-09-22T08:00:00Z")))
        advanceUntilIdle()

        assertEquals(ChoreState.Gone, viewModel.state.value)
    }

    private fun chore(id: Long, name: String = "Vacuum") = Chore(
        id = ChoreId(id),
        name = name,
        recurrence = Recurrence.Every(Period.ofDays(7)),
        anchoredOn = LocalDate.of(2026, 9, 21),
    )
}

/**
 * Just enough of [Chores] for the chore screen: the one read and the three writes, the writes
 * recorded rather than applied. Everything else throws, as in the editor's fake.
 */
private class FakeDetailChores : Chores {

    private val stored = MutableStateFlow<Map<ChoreId, Chore>>(emptyMap())

    val calls = mutableListOf<String>()

    fun given(chore: Chore) {
        stored.value += chore.id to chore
    }

    fun remove(id: ChoreId) {
        stored.value -= id
    }

    override fun detail(id: ChoreId): Flow<ChoreDetail?> = stored.map { chores ->
        chores[id]?.let { ChoreDetail(it, Occurrence(id, it.anchoredOn), emptyList()) }
    }

    override suspend fun complete(id: ChoreId) {
        calls += "complete ${id.value}"
    }

    override suspend fun skip(id: ChoreId) {
        calls += "skip ${id.value}"
    }

    override suspend fun archive(id: ChoreId) {
        calls += "archive ${id.value}"
    }

    override fun agenda(): Flow<Agenda> = unused()

    override fun archived(): Flow<List<Chore>> = unused()

    override suspend fun due(): List<DueChore> = unused()

    override suspend fun markSeen() = unused()

    override suspend fun add(draft: ChoreDraft): ChoreId = unused()

    override suspend fun edit(id: ChoreId, draft: ChoreDraft) = unused()

    override suspend fun restore(id: ChoreId) = unused()

    override suspend fun delete(id: ChoreId) = unused()

    private fun unused(): Nothing = error("the chore screen does not use this")
}
