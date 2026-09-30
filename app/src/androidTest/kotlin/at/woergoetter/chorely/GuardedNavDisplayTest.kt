package at.woergoetter.chorely

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The navigation guards through a real NavDisplay transition, which is the only place the
 * bugs they exist for can happen: a screen stays composed and clickable while it animates
 * out, and the system back gesture reaches NavDisplay throughout.
 *
 * The clock is stopped a moment into each transition, and clicks go through the semantics
 * action rather than a touch, so they reach the departing screen however the two overlap.
 * Every guard has a control, a settled case where the same input does act, so a guard cannot
 * pass merely because the input never arrived.
 */
@RunWith(AndroidJUnit4::class)
class GuardedNavDisplayTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private data object A : NavKey
    private data object B : NavKey
    private data object C : NavKey

    private val stack = mutableStateListOf<NavKey>()
    private val acted = mutableListOf<NavKey>()
    private lateinit var navigator: Navigator

    private fun show(vararg keys: NavKey) {
        stack.addAll(keys)
        rule.setContent {
            navigator = remember { Navigator(stack) }
            GuardedNavDisplay(navigator, stack) {
                for (key in listOf(A, B, C)) {
                    addEntryProvider(key) { Screen(key) }
                }
            }
        }
        rule.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    private fun Screen(key: NavKey) {
        Column {
            Text("screen $key")
            Button(onClick = { navigator.back(from = key) }) { Text("$key back") }
            Button(onClick = { if (navigator.isActive(key)) acted += key }) { Text("$key act") }
        }
    }

    private fun click(label: String) {
        rule.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun systemBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    }

    /** Stops the clock a little way into whatever transition [start] begins. */
    private fun midTransition(start: () -> Unit) {
        rule.mainClock.autoAdvance = false
        start()
        rule.mainClock.advanceTimeBy(100)
    }

    private fun settle() {
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    @Test
    fun systemBackPopsOnceSettled() {
        show(A, B, C)

        systemBack()
        settle()

        assertEquals(listOf(A, B), stack)
    }

    @Test
    fun systemBackDuringAScreensOwnExitDoesNotPopASecondScreen() {
        show(A, B, C)

        midTransition { click("C back") }
        rule.onNodeWithText("screen C").assertExists() // still leaving
        systemBack()
        settle()

        assertEquals(listOf(A, B), stack)
    }

    @Test
    fun aScreenActsWhileItIsOnTop() {
        show(A, B)

        click("B act")

        assertEquals(listOf<NavKey>(B), acted)
    }

    @Test
    fun aScreenLeavingUnderASystemBackCannotAct() {
        show(A, B)

        midTransition { systemBack() }
        rule.onNodeWithText("screen B").assertExists() // still leaving
        click("B act")
        click("B back")
        settle()

        assertEquals(emptyList<NavKey>(), acted)
        assertEquals(listOf(A), stack)
    }

    @Test
    fun systemBackWhileReturningToTheRootDoesNotLeaveTheApp() {
        show(A, B)

        midTransition { click("B back") }
        systemBack()
        settle()

        assertEquals(listOf(A), stack)
        assertFalse("the activity must not finish", rule.activity.isFinishing)
    }
}
