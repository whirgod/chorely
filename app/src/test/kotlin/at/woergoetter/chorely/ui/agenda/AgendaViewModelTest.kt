package at.woergoetter.chorely.ui.agenda

import at.woergoetter.chorely.domain.Agenda
import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreDetail
import at.woergoetter.chorely.domain.ChoreDraft
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Chores
import at.woergoetter.chorely.domain.DueToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * The overview's half of the auto-skip guard: it marks seen only what reached the screen,
 * through the day that agenda was worked out for.
 */
@OptIn(ExperimentalCoroutinesApi::class) // setMain, advanceUntilIdle
class AgendaViewModelTest {

    private val chores = FakeAgendaChores()
    private val main = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `nothing is marked seen before an agenda has reached the screen`() = runTest(main) {
        val viewModel = AgendaViewModel(chores)
        backgroundScope.launch { viewModel.agenda.collect {} }
        backgroundScope.launch { viewModel.markShownWhileResumed() }
        advanceUntilIdle()

        assertEquals(emptyList<LocalDate>(), chores.seen)
    }

    @Test
    fun `each agenda is marked through its own day, so a resume across midnight marks both`() = runTest(main) {
        val viewModel = AgendaViewModel(chores)
        backgroundScope.launch { viewModel.agenda.collect {} }
        backgroundScope.launch { viewModel.markShownWhileResumed() }

        // Yesterday's agenda, still held when the screen resumes; then today's.
        chores.agendas.emit(Agenda(day = SATURDAY))
        advanceUntilIdle()
        chores.agendas.emit(Agenda(day = SUNDAY))
        advanceUntilIdle()
        // A re-emission for the same day, after a write, marks nothing new.
        chores.agendas.emit(Agenda(day = SUNDAY))
        advanceUntilIdle()

        assertEquals(listOf(SATURDAY, SUNDAY), chores.seen)
    }

    private companion object {
        val SATURDAY: LocalDate = LocalDate.of(2026, 9, 19)
        val SUNDAY: LocalDate = LocalDate.of(2026, 9, 20)
    }
}

/** The agenda, driven by hand, and markSeen, recorded. Everything else throws. */
private class FakeAgendaChores : Chores {

    val agendas = MutableSharedFlow<Agenda>(replay = 1)

    val seen = mutableListOf<LocalDate>()

    override fun agenda(): Flow<Agenda> = agendas

    override suspend fun markSeen(through: LocalDate) {
        seen += through
    }

    override fun detail(id: ChoreId): Flow<ChoreDetail?> = unused()

    override fun archived(): Flow<List<Chore>> = unused()

    override suspend fun due(): DueToday = unused()

    override suspend fun add(draft: ChoreDraft): ChoreId = unused()

    override suspend fun edit(id: ChoreId, draft: ChoreDraft) = unused()

    override suspend fun complete(id: ChoreId) = unused()

    override suspend fun skip(id: ChoreId) = unused()

    override suspend fun archive(id: ChoreId) = unused()

    override suspend fun restore(id: ChoreId) = unused()

    override suspend fun delete(id: ChoreId) = unused()

    private fun unused(): Nothing = error("the agenda does not use this")
}
