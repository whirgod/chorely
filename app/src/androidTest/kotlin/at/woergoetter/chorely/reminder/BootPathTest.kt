package at.woergoetter.chorely.reminder

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.Lazy
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import kotlin.math.abs

/**
 * After a reboot the schedule is rebuilt from what is stored and nothing else: `BootReceiver`
 * hands off to `ReminderSyncWorker`, which asks `WorkManagerReminders` to derive the digest
 * from the stored reminder time. AGENTS.md requires this path to have its own test.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BootPathTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var settings: FakeReminderSettings

    @Inject lateinit var events: Events

    @Inject lateinit var clock: Clock

    // Lazy: building it builds WorkManagerReminders, which needs the test WorkManager to exist.
    @Inject lateinit var reminders: Lazy<Reminders>

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        hilt.inject()
        initializeTestWorkManager(context, workerFactory)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun aBootSchedulesTheDigestAtTheStoredReminderTime() = runBlocking {
        // Two hours out rather than a fixed hour, so the reminder time cannot pass between the
        // worker computing the delay and this test computing it again.
        val time = LocalTime.now(clock).plusHours(2).withSecond(0).withNano(0)
        settings.time.value = time

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val digest = workManager.awaitPendingDigest()
        // The request can be visible before the worker's thread has recorded the sync.
        eventually { events.snapshot().isNotEmpty() }
        assertEquals(listOf("sync"), events.snapshot())
        // Against the pure function the schedule is unit-tested through, with a minute's slack
        // for the time that passes between the worker reading the clock and this line.
        val expected = nextDigestDelay(time, clock).toMillis()
        assertTrue(
            "digest delay ${digest.initialDelayMillis} ms, expected about $expected ms",
            abs(digest.initialDelayMillis - expected) < 60_000,
        )
    }

    @Test
    fun aBootWithRemindersOffCancelsAnyPendingDigest() = runBlocking {
        // A digest from before reminders were switched off, which the boot must not leave behind.
        settings.time.value = LocalTime.now(clock).plusHours(2)
        reminders.get().sync()
        workManager.awaitPendingDigest()
        settings.time.value = null

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        eventually { events.snapshot().count { it == "sync" } == 2 }
        val digests = workManager.getWorkInfosForUniqueWork(DailyDigestWorker.NAME).get()
        assertTrue("no digest may be pending: $digests", digests.none { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun aSecondBootWhileASyncIsPendingAddsNoSecondSync() {
        // Held by its backoff: the first attempt fails, so the request stays pending.
        settings.readResult = { error("database unavailable") }

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        runBlocking {
            eventually {
                workManager.getWorkInfosForUniqueWork(ReminderSyncWorker.NAME).get()
                    .any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount == 1 }
            }
        }
        val retrying = workManager.getWorkInfosForUniqueWork(ReminderSyncWorker.NAME).get().single().id
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        // The one already retrying, not a fresh request in its place, which REPLACE would leave.
        val syncs = workManager.getWorkInfosForUniqueWork(ReminderSyncWorker.NAME).get()
        assertEquals("one sync, retrying: $syncs", listOf(retrying), syncs.map { it.id })
    }

    @Test
    fun theBootSyncGivesUpAfterItsLastAttempt() = runBlocking {
        settings.readResult = { error("database unavailable") }
        val worker = TestListenableWorkerBuilder<ReminderSyncWorker>(context)
            .setWorkerFactory(workerFactory)
            .setRunAttemptCount(DailyDigestWorker.MAX_ATTEMPTS - 1)
            .build()

        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }

    @Test
    fun aTimezoneChangeReaimsThePendingDigest() = runBlocking {
        settings.time.value = LocalTime.now(clock).plusHours(2).withSecond(0).withNano(0)

        BootReceiver().onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))

        workManager.awaitPendingDigest()
        eventually { events.snapshot().isNotEmpty() }
        assertEquals(listOf("sync"), events.snapshot())
    }

    @Test
    fun anyOtherBroadcastIsIgnored() {
        settings.time.value = LocalTime.of(19, 0)

        BootReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        // The receiver's enqueue is synchronous under the test executor, so nothing having
        // been enqueued by now means nothing will be.
        val all = workManager.getWorkInfos(WorkQuery.fromStates(WorkInfo.State.entries)).get()
        assertTrue("nothing may be enqueued: $all", all.isEmpty())
    }
}
