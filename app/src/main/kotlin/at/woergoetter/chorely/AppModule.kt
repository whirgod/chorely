package at.woergoetter.chorely

import android.content.Context
import androidx.work.WorkManager
import at.woergoetter.chorely.reminder.DigestNotifier
import at.woergoetter.chorely.reminder.Reminders
import at.woergoetter.chorely.reminder.SystemDigestNotifier
import at.woergoetter.chorely.reminder.WorkManagerReminders
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The platform half of the composition root: the two seams where Android supplies the
 * implementation. `:core:data` binds the persistence half.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun reminders(impl: WorkManagerReminders): Reminders

    @Binds
    @Singleton
    abstract fun digestNotifier(impl: SystemDigestNotifier): DigestNotifier

    companion object {
        @Provides
        @Singleton
        fun workManager(@ApplicationContext context: Context): WorkManager =
            WorkManager.getInstance(context)
    }
}
