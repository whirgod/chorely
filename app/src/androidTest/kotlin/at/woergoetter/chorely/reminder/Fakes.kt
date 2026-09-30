package at.woergoetter.chorely.reminder

import android.content.Context
import androidx.work.WorkManager
import at.woergoetter.chorely.AppModule
import at.woergoetter.chorely.data.DataModule
import at.woergoetter.chorely.data.DeviceClock
import at.woergoetter.chorely.domain.Agenda
import at.woergoetter.chorely.domain.Chore
import at.woergoetter.chorely.domain.ChoreDetail
import at.woergoetter.chorely.domain.ChoreDraft
import at.woergoetter.chorely.domain.ChoreId
import at.woergoetter.chorely.domain.Chores
import at.woergoetter.chorely.domain.DueChore
import at.woergoetter.chorely.domain.DueToday
import at.woergoetter.chorely.domain.ReminderSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One log shared by every fake below, so a test can assert the order in which the digest
 * worker reached them — the order is the guard, not merely which calls were made.
 */
@Singleton
class Events @Inject constructor() {
    private val log = Collections.synchronizedList(mutableListOf<String>())

    fun record(event: String) {
        log += event
    }

    fun snapshot(): List<String> = synchronized(log) { log.toList() }
}

/**
 * The persistence half of the graph, swapped wholesale: the real one opens the app's own
 * database file, which an instrumented test shares with whatever is installed on the device.
 *
 * `@TestInstallIn` applies to every test in the app's androidTest source set, not just the
 * reminder ones, and [FakeChores] implements only what the reminder path calls. A UI test
 * here needs those methods filled in, or `@UninstallModules` and bindings of its own.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
abstract class FakeDataModule {

    @Binds
    abstract fun chores(fake: FakeChores): Chores

    @Binds
    abstract fun reminderSettings(fake: FakeReminderSettings): ReminderSettings

    companion object {
        @Provides
        @Singleton
        fun clock(): Clock = DeviceClock
    }
}

/**
 * The platform half, with the real [WorkManagerReminders] kept — it is under test — but seen
 * through [RecordingReminders], and the notifier faked so a test decides whether the system
 * "accepted" the post.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
abstract class FakeAppModule {

    @Binds
    abstract fun reminders(recording: RecordingReminders): Reminders

    @Binds
    abstract fun digestNotifier(fake: FakeDigestNotifier): DigestNotifier

    companion object {
        /**
         * Resolved on first use, not at injection: the test instance only exists once
         * `WorkManagerTestInitHelper` has run, and asking before that throws.
         */
        @Provides
        fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
    }
}

@Singleton
class FakeChores @Inject constructor(private val events: Events) : Chores {

    /** What `due()` returns; replace with a throwing lambda to make it fail. */
    @Volatile
    var dueResult: () -> List<DueChore> = { emptyList() }

    /**
     * The day `due()` reports; `markSeen` is asserted to be told this one. Decades from any
     * clock, so a worker that worked the day out for itself could not pass by coincidence.
     */
    val today: LocalDate = LocalDate.of(2001, 1, 1)

    var seenThrough: LocalDate? = null

    override suspend fun due(): DueToday {
        events.record("due")
        return DueToday(today, dueResult())
    }

    override suspend fun markSeen(through: LocalDate) {
        events.record("markSeen")
        seenThrough = through
    }

    override fun agenda(): Flow<Agenda> = unused()

    override fun detail(id: ChoreId): Flow<ChoreDetail?> = unused()

    override fun archived(): Flow<List<Chore>> = unused()

    override suspend fun add(draft: ChoreDraft): ChoreId = unused()

    override suspend fun edit(id: ChoreId, draft: ChoreDraft) = unused()

    override suspend fun complete(id: ChoreId) = unused()

    override suspend fun skip(id: ChoreId) = unused()

    override suspend fun archive(id: ChoreId) = unused()

    override suspend fun restore(id: ChoreId) = unused()

    override suspend fun delete(id: ChoreId) = unused()

    private fun unused(): Nothing = error("the reminder path does not use this")
}

@Singleton
class FakeReminderSettings @Inject constructor() : ReminderSettings {

    val time = MutableStateFlow<LocalTime?>(null)

    /** Replace with a throwing lambda to make the read fail, as a database that is down would. */
    @Volatile
    var readResult: () -> Unit = {}

    override fun reminderTime(): Flow<LocalTime?> {
        readResult()
        return time
    }

    override suspend fun setReminderTime(time: LocalTime?) {
        this.time.value = time
    }
}

@Singleton
class FakeDigestNotifier @Inject constructor(private val events: Events) : DigestNotifier {

    /** Whether the system "accepts" the post; replace with a throwing lambda to make it fail. */
    @Volatile
    var postResult: () -> Boolean = { true }

    override suspend fun post(due: List<DueChore>): Boolean {
        events.record("post")
        return postResult()
    }
}

@Singleton
class RecordingReminders @Inject constructor(
    private val real: WorkManagerReminders,
    private val events: Events,
) : Reminders {

    /** Recorded once the real sync has returned, so seeing it means the schedule is settled. */
    override suspend fun sync() {
        real.sync()
        events.record("sync")
    }
}
