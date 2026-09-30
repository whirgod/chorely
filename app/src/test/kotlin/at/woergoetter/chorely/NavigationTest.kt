package at.woergoetter.chorely

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two rules the back stack is edited by. Both exist because a screen stays composed and
 * tappable for the length of a transition, so every navigation callback can fire twice: the
 * guards are what keeps the second firing from doing damage.
 *
 * These are plain list operations on purpose. Reaching the same conclusions through
 * `NavDisplay` would take a Compose UI test, and the app module's instrumented tests are never
 * run anywhere — CI's emulator job runs `:core:data` only.
 */
class NavigationTest {

    private fun stackOf(vararg keys: NavKey) = mutableListOf<NavKey>(*keys)

    @Test
    fun `back pops the top entry`() {
        val stack = stackOf(AgendaRoute, ChoreRoute(1), ChoreEditorRoute(1))

        stack.back()

        assertEquals(listOf(AgendaRoute, ChoreRoute(1)), stack)
    }

    @Test
    fun `back leaves the last entry alone, because NavDisplay throws on an empty stack`() {
        val stack = stackOf(AgendaRoute)

        stack.back()
        stack.back()

        assertEquals(listOf(AgendaRoute), stack)
    }

    @Test
    fun `go pushes a key that is not on top`() {
        val stack = stackOf(AgendaRoute)

        stack.go(ChoreEditorRoute())

        assertEquals(listOf(AgendaRoute, ChoreEditorRoute()), stack)
    }

    /**
     * The double tap this guard exists for. `ChoreEditorRoute()` defaults its id to null, so
     * two taps on the agenda's add button produce two *equal* keys — and nav3 keys an entry's
     * saved state and its ViewModelStore by the key, so the second entry would share the
     * first's slot rather than get one of its own.
     */
    @Test
    fun `go refuses a key equal to the one already on top`() {
        val stack = stackOf(AgendaRoute)

        stack.go(ChoreEditorRoute())
        stack.go(ChoreEditorRoute())

        assertEquals(listOf(AgendaRoute, ChoreEditorRoute()), stack)
    }

    /** A double tap on a chore row is the same hazard with an id attached. */
    @Test
    fun `go refuses an equal key with the same id`() {
        val stack = stackOf(AgendaRoute)

        stack.go(ChoreRoute(7))
        stack.go(ChoreRoute(7))

        assertEquals(listOf(AgendaRoute, ChoreRoute(7)), stack)
    }

    /** Only equality decides, so editing a different chore is still a push. */
    @Test
    fun `go pushes a key of the same type carrying a different id`() {
        val stack = stackOf(AgendaRoute, ChoreEditorRoute())

        stack.go(ChoreEditorRoute(7))

        assertEquals(listOf(AgendaRoute, ChoreEditorRoute(), ChoreEditorRoute(7)), stack)
    }

    /**
     * The guard is about adjacency and nothing more: two equal keys deeper in the stack are
     * the caller's business, and refusing them here would be a routing decision this function
     * has no standing to make.
     */
    @Test
    fun `go pushes a key that appears further down the stack`() {
        val stack = stackOf(AgendaRoute, ChoreRoute(7), ChoreEditorRoute(7))

        stack.go(ChoreRoute(7))

        assertEquals(
            listOf(AgendaRoute, ChoreRoute(7), ChoreEditorRoute(7), ChoreRoute(7)),
            stack,
        )
    }
}
