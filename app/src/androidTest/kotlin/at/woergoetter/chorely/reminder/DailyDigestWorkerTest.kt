package at.woergoetter.chorely.reminder

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.DueChore
import at.woergoetter.chorely.domain.Occurrence
import at.woergoetter.chorely.domain.Recurrence
import dagger.Lazy
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period

/**
 * The digest worker's two obligations, run through WorkManager as the app runs it:
 *
 * - `markSeen()` only after a notification was actually posted, and through the day the list
 *   was worked out for, the guard that stops an
 *   occurrence lapsing before the user was told about it (docs/adr/0002).
 * - The chain never ends: a run that worked schedules its successor, and one that failed is
 *   retried, since the chain is one-shot work and a run that does neither ends reminders
 *   for good.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DailyDigestWorkerTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var chores: FakeChores

    @Inject lateinit var notifier: FakeDigestNotifier

    @Inject lateinit var settings: FakeReminderSettings

    @Inject lateinit var events: Events

    // Lazy: building it builds WorkManagerReminders, which needs the test WorkManager to exist.
    @Inject lateinit var reminders: Lazy<Reminders>

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        hilt.inject()
        initializeTestWorkManager(context, workerFactory)
        workManager = WorkManager.getInstance(context)
        settings.time.value = LocalTime.of(19, 0)
    }

    @Test
    fun aPostedDigestMarksWhatWasDueAsSeenThenSchedulesTheNext() {
        chores.dueResult = { listOf(dueChore()) }

        assertEquals(listOf("due", "post", "markSeen", "sync"), runDigest())
        // Through the day due() worked the list out for, not whatever day it is by now.
        assertEquals(chores.today, chores.seenThrough)
    }

    @Test
    fun aRefusedPostMarksNothingSeen() {
        chores.dueResult = { listOf(dueChore()) }
        notifier.postResult = { false }

        assertEquals(listOf("due", "post", "sync"), runDigest())
    }

    @Test
    fun nothingDuePostsNothingAndMarksNothingSeen() {
        chores.dueResult = { emptyList() }

        assertEquals(listOf("due", "sync"), runDigest())
    }

    @Test
    fun aFailedReadIsRetriedRatherThanEndingTheChain() {
        chores.dueResult = { error("database unavailable") }

        assertEquals(listOf("due"), runFailingDigest())
    }

    @Test
    fun aFailedPostIsRetriedAndMarksNothingSeen() {
        chores.dueResult = { listOf(dueChore()) }
        notifier.postResult = { error("notification service unavailable") }

        assertEquals(listOf("due", "post"), runFailingDigest())
    }

    @Test
    fun aFailedSyncIsRetriedToo() {
        // The database down for the reminder time as well as the chores: syncing past the
        // failure would throw again, and only WorkManager's own retry keeps the chain.
        chores.dueResult = { listOf(dueChore()) }

        val events = runFailingDigest { settings.readResult = { error("database unavailable") } }

        assertEquals(listOf("due", "post", "markSeen"), events)
    }

    @Test
    fun theLastAttemptGivesTodayUpButStillSchedulesTomorrow() = runBlocking {
        chores.dueResult = { error("database unavailable") }
        val worker = TestListenableWorkerBuilder<DailyDigestWorker>(context)
            .setWorkerFactory(workerFactory)
            .setRunAttemptCount(DailyDigestWorker.MAX_ATTEMPTS - 1)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(listOf("due", "sync"), events.snapshot())
        workManager.awaitPendingDigest()
        Unit
    }

    /**
     * Schedules a digest as the app does, lets its delay pass, and waits for the request that
     * run must leave behind. Returns what the run did, in order. Times out — failing the test —
     * if the run leaves no successor.
     */
    private fun runDigest(): List<String> = runBlocking {
        val scheduled = scheduleDigest()
        val before = events.snapshot().size

        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(scheduled.id)

        workManager.awaitPendingDigest { it.id != scheduled.id }
        // The successor can be visible before the worker's thread has recorded the sync.
        eventually { "sync" in events.snapshot().drop(before) }
        events.snapshot().drop(before)
    }

    /**
     * As [runDigest], for a run that fails: waits for the same request to be back in the queue
     * for a second attempt — a retry, not a successor — and returns what the failed run did.
     * [beforeRun] breaks whatever must still work for the digest to be scheduled at all.
     */
    private fun runFailingDigest(beforeRun: () -> Unit = {}): List<String> = runBlocking {
        val scheduled = scheduleDigest()
        val before = events.snapshot().size
        beforeRun()

        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(scheduled.id)

        workManager.awaitPendingDigest { it.id == scheduled.id && it.runAttemptCount == 1 }
        events.snapshot().drop(before)
    }

    private suspend fun scheduleDigest() = reminders.get().sync().let { workManager.awaitPendingDigest() }

    private fun dueChore(): DueChore {
        val id = ChoreId(1)
        val today = LocalDate.now()
        return DueChore(
            chore = Chore(id = id, name = "Vacuum", recurrence = Recurrence.Every(Period.ofDays(7)), anchoredOn = today),
            occurrence = Occurrence(id, today),
        )
    }
}
