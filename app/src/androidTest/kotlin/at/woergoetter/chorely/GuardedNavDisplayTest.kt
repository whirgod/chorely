package at.woergoetter.chorely

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Nodes are found in the unmerged tree, because a departing screen clears its semantics —
 * a finder looking where an accessibility service does would not see it at all.
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
    private val poked = mutableListOf<NavKey>()
    private lateinit var navigator: Navigator

    private fun show(vararg keys: NavKey, blockOffTop: Boolean = true) {
        stack.addAll(keys)
        rule.setContent {
            navigator = remember { Navigator(stack) }
            GuardedNavDisplay(navigator, blockOffTop) {
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
            Button(onClick = { navigator.back(from = key) }, Modifier.testTag("$key back")) { Text("$key back") }
            Button(onClick = { if (navigator.isActive(key)) acted += key }, Modifier.testTag("$key act")) {
                Text("$key act")
            }
            // Ungated, as the agenda's and the settings screen's controls are: only the
            // display's touch blocking stands between it and a tap on a departing screen.
            Button(onClick = { poked += key }, Modifier.testTag("$key poke")) { Text("$key poke") }
            Button(onClick = { navigator.go(from = key, to = B) }, Modifier.testTag("$key open B")) {
                Text("$key open B")
            }
        }
    }

    private fun click(label: String) {
        rule.onNodeWithTag(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
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
        rule.onNodeWithText("screen C", useUnmergedTree = true).assertExists() // still leaving
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
        rule.onNodeWithText("screen B", useUnmergedTree = true).assertExists() // still leaving
        // Gone from the tree an accessibility service reads, so TalkBack cannot activate it...
        rule.onNodeWithText("B act").assertDoesNotExist()
        // ...and an action reaching it anyway, as this one does, is refused by the gate.
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

    @Test
    fun systemBackWorksAgainOnceTheTransitionHasSettled() {
        show(A, B, C)

        midTransition { click("C back") }
        settle()
        systemBack()
        settle()

        // Settled by B's own resume, not by the state the navigator started in.
        assertEquals(listOf(A), stack)
    }

    @Test
    fun aQuickBackAfterAPushLeavesTheRootUnsettled() {
        show(A)

        midTransition { click("A open B") }
        click("B back")
        rule.mainClock.advanceTimeBy(50)
        systemBack()
        settle()

        assertEquals(listOf(A), stack)
        assertFalse("the activity must not finish", rule.activity.isFinishing)
    }

    @Test
    fun controlADepartingScreenWouldTakeTheTouchWithoutTheBlock() {
        // The same touch as below, with only the block switched off: it does land on B, so the
        // test below passing is the block's doing and not the two screens' layering.
        show(A, B, blockOffTop = false)

        midTransition { systemBack() }
        rule.onNodeWithTag("B poke", useUnmergedTree = true).performClick()
        settle()

        assertEquals(listOf<NavKey>(B), poked)
    }

    @Test
    fun aDepartingScreenTakesNoTouches() {
        show(A, B)
        rule.onNodeWithTag("B poke", useUnmergedTree = true).performClick()
        assertEquals("control: a settled screen takes the touch", listOf<NavKey>(B), poked)

        midTransition { systemBack() }
        rule.onNodeWithTag("B poke", useUnmergedTree = true).performClick()
        settle()

        assertEquals(listOf<NavKey>(B), poked)
    }

    @Test
    fun aPredictiveBackGesturePopsASettledScreen() {
        // The gesture seeks NavDisplay's transition as it goes, which holds the top entry at
        // STARTED until it commits: the settled screen must not stop counting as settled.
        // Dispatched in-process, so it runs on every API level, CI's included. B is settled by
        // a real push and its own resume, not by the state the navigator starts in.
        show(A)
        click("A open B")
        rule.waitForIdle()

        rule.runOnUiThread {
            val dispatcher = rule.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(300f, 500f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        rule.waitForIdle()
        // The gesture reached NavDisplay: the screen behind is being revealed.
        rule.onNodeWithText("screen A", useUnmergedTree = true).assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()

        assertEquals(listOf(A), stack)
    }

    @Test
    fun aScreenStillLeavingCannotBeReopenedOntoItsDepartingSelf() {
        show(A, B)

        midTransition { click("B back") }
        click("A open B")
        settle()

        // Refused while B was still composed on its way out; B's next opening is a fresh one.
        assertEquals(listOf(A), stack)
        click("A open B")
        settle()
        assertEquals(listOf(A, B), stack)
    }
}
