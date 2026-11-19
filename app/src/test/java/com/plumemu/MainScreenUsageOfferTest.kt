package com.plumemu

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.game.FakeGameActions
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.UsageAccessOfferTags
import com.plumemu.home.WhatsNewFixtures
import com.plumemu.home.WhatsNewTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.update.UpdateManifest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The one-time Usage access offer card on the main screen: when it shows, the gamepad and touch answers, and its place after "What's new". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class MainScreenUsageOfferTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val due = MutableStateFlow(false)
    private val whatsNew = MutableStateFlow<UpdateManifest?>(null)
    private var shown = 0
    private var allowed = 0
    private lateinit var router: InputRouter

    // A state, as in MainScreen: the wait for the home to be here reads it with snapshotFlow, not frame by frame.
    private var isLeaving by mutableStateOf(false)

    private fun show() = show { compose.setContent(it) }

    private fun show(setContent: (@androidx.compose.runtime.Composable () -> Unit) -> Unit) {
        setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            MainScreen(
                PlumSettings.Defaults,
                HomeState.Ready(FakeHomeContent.board()),
                router,
                HomeCallbacks(
                    games = FakeGameActions(),
                    whatsNew = whatsNew,
                    usageAccessOfferDue = due,
                    // As the graph does: once shown, nothing is due any more.
                    onUsageAccessOfferShown = { shown++; due.value = false },
                    onUsageAccessAllow = { allowed++ },
                    isLeaving = { isLeaving },
                ),
                onRetry = {},
            )
        }
        if (!isLeaving) compose.waitForIdle()
    }

    private fun press(vararg actions: Action) {
        actions.forEach {
            compose.runOnIdle { router.send(it) }
            compose.waitForIdle()
        }
    }

    private fun card() = compose.onNodeWithTag(UsageAccessOfferTags.CARD)

    @Test
    fun nothingShowsWhileNoOfferIsDue() {
        show()
        card().assertDoesNotExist()
        assertEquals(0, shown)
    }

    @Test
    fun anOfferDueShowsTheCardOnceAndCountsItShown() {
        due.value = true
        show()
        card().assertIsDisplayed()
        assertEquals(1, shown)
    }

    @Test
    fun aOnTheFirstButtonAllowsAndCloses() {
        due.value = true
        show()
        press(Action.Enter)
        card().assertDoesNotExist()
        assertEquals(1, allowed)
    }

    @Test
    fun rightThenAIsNotNowAndAllowsNothing() {
        due.value = true
        show()
        press(Action.Move(Direction.Right), Action.Enter)
        card().assertDoesNotExist()
        assertEquals(0, allowed)
    }

    @Test
    fun bIsNotNow() {
        due.value = true
        show()
        press(Action.Back)
        card().assertDoesNotExist()
        assertEquals(0, allowed)
    }

    @Test
    fun aTapOnAllowAllows() {
        due.value = true
        show()
        compose.onNodeWithTag(UsageAccessOfferTags.button(0), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        card().assertDoesNotExist()
        assertEquals(1, allowed)
    }

    @Test
    fun aTapOnNotNowClosesWithoutAllowing() {
        due.value = true
        show()
        compose.onNodeWithTag(UsageAccessOfferTags.button(1), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        card().assertDoesNotExist()
        assertEquals(0, allowed)
    }

    @Test
    fun aClosedCardDoesNotReturnWhileTheOfferStaysDue() {
        due.value = true
        show()
        press(Action.Back)
        compose.waitForIdle()
        card().assertDoesNotExist()
        assertEquals(1, shown)
    }

    @Test
    fun theCardWaitsForWhatsNewAndComesAfterIt() {
        whatsNew.value = WhatsNewFixtures.grouped.copy(versionCode = 13, versionName = "0.1.2")
        due.value = true
        show()
        compose.onNodeWithTag(WhatsNewTags.CARD).assertIsDisplayed()
        card().assertDoesNotExist()
        assertEquals(0, shown)
        press(Action.Enter)
        whatsNew.value = null
        compose.waitForIdle()
        card().assertIsDisplayed()
        assertEquals(1, shown)
    }

    @Test
    fun whatsNewArrivingLateWaitsForTheOpenOffer() {
        due.value = true
        show()
        card().assertIsDisplayed()
        whatsNew.value = WhatsNewFixtures.grouped.copy(versionCode = 13, versionName = "0.1.2")
        compose.waitForIdle()
        compose.onNodeWithTag(WhatsNewTags.CARD).assertDoesNotExist()
        press(Action.Enter)
        assertEquals("the press went to the offer only", 1, allowed)
        compose.onNodeWithTag(WhatsNewTags.CARD).assertIsDisplayed()
    }

    @Test
    fun theCardIsNeitherShownNorCountedWhileTheHomeIsLeavingForSettings() {
        // The clock is stepped by hand while the card waits for the home.
        compose.mainClock.autoAdvance = false
        isLeaving = true
        due.value = true
        show()
        repeat(5) { compose.mainClock.advanceTimeByFrame() }
        card().assertDoesNotExist()
        assertEquals(0, shown)
        isLeaving = false
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        card().assertIsDisplayed()
        assertEquals(1, shown)
    }

    @Test
    fun anActivityRecreatedWithTheCardOpenShowsItAgainWithoutCountingItTwice() {
        val tester = StateRestorationTester(compose)
        due.value = true
        show { tester.setContent(it) }
        card().assertIsDisplayed()
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        card().assertIsDisplayed()
        assertEquals(1, shown)
        press(Action.Back)
        card().assertDoesNotExist()
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        card().assertDoesNotExist()
    }
}
