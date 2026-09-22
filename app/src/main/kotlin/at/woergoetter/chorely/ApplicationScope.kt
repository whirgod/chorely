package at.woergoetter.chorely

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A [CoroutineScope] that lives as long as the process does.
 *
 * For the writes that must not be cancelled by the screen that started them: a ViewModel's
 * `viewModelScope` dies with its nav entry, so a save that pops the editor in the same
 * gesture would race its own coroutine. Anything a user has asked for and been told is done
 * belongs here; anything that only feeds the screen belongs in `viewModelScope`, so that
 * leaving the screen stops the work.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
// Narrowed to the two places this codebase injects: a `@Provides` function and a constructor
// parameter. Left wider, a constructor-injected `val` would leave Kotlin choosing between the
// parameter and the property it backs — and only the parameter is what Dagger reads.
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ApplicationScopeModule {

    /**
     * A [SupervisorJob], so one failed write does not take the others down with it. Never
     * cancelled: the process ending is what ends it.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
