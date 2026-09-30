package at.woergoetter.chorely.reminder

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * Installs the test WorkManager with Hilt's worker factory, as `ChorelyApplication` does for
 * the real one, so the workers under test are built by the same graph the app uses.
 */
fun initializeTestWorkManager(context: Context, workerFactory: HiltWorkerFactory) {
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context,
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(SynchronousExecutor())
            .build(),
    )
}

/**
 * The digest request that is waiting to run, once one matching [where] is. A `CoroutineWorker`
 * runs on `Dispatchers.Default` even under a `SynchronousExecutor`, so "the worker has finished"
 * is something to wait for rather than something that is true when `enqueue` returns.
 */
suspend fun WorkManager.awaitPendingDigest(where: (WorkInfo) -> Boolean = { true }): WorkInfo =
    withTimeout(TIMEOUT_MS) {
        getWorkInfosForUniqueWorkFlow(DailyDigestWorker.NAME)
            .first { infos -> infos.any { it.state == WorkInfo.State.ENQUEUED && where(it) } }
            .single { it.state == WorkInfo.State.ENQUEUED }
    }

/** Waits for [condition], for the things a test can only poll. */
suspend fun eventually(condition: () -> Boolean) = withTimeout(TIMEOUT_MS) {
    while (!condition()) delay(20)
}

// Generous: only a broken test waits this long, and the first test in a cold API 26 process on
// CI pays for class loading and the Hilt component at once.
private const val TIMEOUT_MS = 30_000L
