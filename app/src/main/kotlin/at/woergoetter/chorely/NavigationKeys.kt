package at.woergoetter.chorely

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** What is due today, overdue, and coming up. The app opens here. */
@Serializable
data object AgendaRoute : NavKey

/** One chore: where it stands, and every time it has been done. */
@Serializable
data class ChoreRoute(val choreId: Long) : NavKey

/** Creating a chore, or editing an existing one when [choreId] is non-null. */
@Serializable
data class ChoreEditorRoute(val choreId: Long? = null) : NavKey

/** Archived chores, to restore or delete. */
@Serializable
data object ArchiveRoute : NavKey

/** The one reminder time for the whole app. */
@Serializable
data object SettingsRoute : NavKey
