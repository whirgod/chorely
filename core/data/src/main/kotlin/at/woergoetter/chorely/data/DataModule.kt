package at.woergoetter.chorely.data

import android.content.Context
import at.woergoetter.chorely.domain.ChoreStore
import at.woergoetter.chorely.domain.Chores
import at.woergoetter.chorely.domain.ReminderSettings
import at.woergoetter.chorely.domain.StoredChores
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * The composition root for persistence. This is the only place that knows Room exists:
 * everything else is handed a [Chores] or a [ReminderSettings].
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    internal fun database(@ApplicationContext context: Context): ChorelyDatabase =
        ChorelyDatabase.open(context)

    @Provides
    @Singleton
    internal fun choreStore(database: ChorelyDatabase): ChoreStore = RoomChoreStore(database)

    @Provides
    @Singleton
    internal fun reminderSettings(database: ChorelyDatabase): ReminderSettings =
        RoomReminderSettings(database)

    /**
     * The device's own clock and zone. Injected rather than reached for, so tests can
     * advance time and travel; due dates are whole local days in the *current* timezone,
     * with no correction for travel.
     */
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun chores(store: ChoreStore, clock: Clock): Chores = StoredChores(store, clock)
}
