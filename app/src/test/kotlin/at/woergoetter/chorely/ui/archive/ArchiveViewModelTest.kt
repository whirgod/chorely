package at.woergoetter.chorely.ui.archive

import at.woergoetter.chorely.domain.Agenda
import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreDetail
import at.woergoetter.chorely.domain.ChoreDraft
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Chores
import at.woergoetter.chorely.domain.DueChore
import at.woergoetter.chorely.domain.Recurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.Period

/**
 * What the archive screen's ViewModel owes it: the archived chores, with "not read yet" kept
 * apart from "none", and writes that outlive the screen.
 */
@OptIn(ExperimentalCoroutinesApi::class) // setMain, advanceUntilIdle
class ArchiveViewModelTest {

    private val chores = FakeArchiveChores()
    private val applicationJob = SupervisorJob()
    private val main = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = ArchiveViewModel(
        chores = chores,
        applicationScope = CoroutineScope(applicationJob + StandardTestDispatcher(testScheduler)),
    )

    @Test
    fun `both writes launch on the application scope, not on one the screen can cancel`() = runTest(main) {
        val viewModel = viewModel()

        listOf(viewModel.onRestore(ChoreId(1)), viewModel.onDelete(ChoreId(1))).forEach {
            assertTrue("each write must belong to the application scope", it in applicationJob.children)
        }

        advanceUntilIdle()
    }

    @Test
    fun `writes go to the chore they name`() = runTest(main) {
        val viewModel = viewModel()

        viewModel.onRestore(ChoreId(3))
        viewModel.onDelete(ChoreId(4))
        advanceUntilIdle()

        assertEquals(listOf("restore 3", "delete 4"), chores.calls)
    }

    @Test
    fun `archived is null until the store emits, so loading never reads as empty`() = runTest(main) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.archived.collect {} }
        advanceUntilIdle()

        assertNull(viewModel.archived.value)

        chores.archived.emit(emptyList())
        advanceUntilIdle()
        assertEquals(emptyList<Chore>(), viewModel.archived.value)

        chores.archived.emit(listOf(chore(id = 1, name = "Descale the kettle")))
        advanceUntilIdle()
        assertEquals(listOf("Descale the kettle"), viewModel.archived.value?.map { it.name })
    }

    private fun chore(id: Long, name: String) = Chore(
        id = ChoreId(id),
        name = name,
        recurrence = Recurrence.Every(Period.ofDays(7)),
        anchoredOn = LocalDate.of(2026, 9, 21),
        archivedAt = Instant.parse("2026-09-22T08:00:00Z"),
    )
}

/**
 * Just enough of [Chores] for the archive screen: the one read, driven by hand, and the two
 * writes, recorded rather than applied. Everything else throws.
 */
private class FakeArchiveChores : Chores {

    val archived = MutableSharedFlow<List<Chore>>(replay = 1)

    val calls = mutableListOf<String>()

    override fun archived(): Flow<List<Chore>> = archived

    override suspend fun restore(id: ChoreId) {
        calls += "restore ${id.value}"
    }

    override suspend fun delete(id: ChoreId) {
        calls += "delete ${id.value}"
    }

    override fun agenda(): Flow<Agenda> = unused()

    override fun detail(id: ChoreId): Flow<ChoreDetail?> = unused()

    override suspend fun due(): List<DueChore> = unused()

    override suspend fun markSeen() = unused()

    override suspend fun add(draft: ChoreDraft): ChoreId = unused()

    override suspend fun edit(id: ChoreId, draft: ChoreDraft) = unused()

    override suspend fun complete(id: ChoreId) = unused()

    override suspend fun skip(id: ChoreId) = unused()

    override suspend fun archive(id: ChoreId) = unused()

    private fun unused(): Nothing = error("the archive screen does not use this")
}
