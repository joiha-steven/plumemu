package com.plumemu.home

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.plumemu.battery.BatteryPace
import com.plumemu.battery.PickingBatteryTags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FakeGameActions
import com.plumemu.game.GameMenuEntryId
import com.plumemu.game.GamePanelTags
import com.plumemu.game.sampleFacts
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The picking line on the top screen follows Settings › Show battery, as the top bar's level does
 * (no ticker): off, no line under the details and none in Info, and no battery
 * receiver at all; on, the line and one receiver for the screen; switched while up, both follow.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeBatteryLineShowBatteryTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val app = RuntimeEnvironment.getApplication()
    private val pace = BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0)
    private val games = FakeGameActions().apply { facts = { sampleFacts(title = "Super Metroid").copy(batteryPace = pace) } }
    private lateinit var router: InputRouter
    private var showBattery by mutableStateOf(false)

    // The system's battery broadcast is sticky; a test can only send one with this deprecated call.
    @Suppress("DEPRECATION")
    @Before
    fun batteryIs60Percent() {
        app.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 60).putExtra(BatteryManager.EXTRA_SCALE, 100))
    }

    private fun batteryReceivers() = shadowOf(app).registeredReceivers.count { it.intentFilter.hasAction(Intent.ACTION_BATTERY_CHANGED) }

    private fun linesUnder(tag: String) =
        compose.onAllNodes(hasTestTag(PickingBatteryTags.LINE) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun lines() = compose.onAllNodes(hasTestTag(PickingBatteryTags.LINE), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun send(vararg actions: Action) =
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }

    /** Super Metroid focused in the Super Nintendo list, its play facts read (a measured game: a usual pace). */
    private fun showMeasuredGame() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            PlumTheme {
                CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts(pace = pace)) {
                    HomeScreen(LibraryLikeContent.of(), router, HomeCallbacks(games = games), bars = HomeBars(showBattery = showBattery))
                }
            }
        }
        compose.waitForIdle()
        send(*LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID).toTypedArray())
        compose.mainClock.advanceTimeBy(SETTLED_MS)
        compose.waitForIdle()
        compose.onNodeWithTag(HomeTags.DETAILS_FACTS, useUnmergedTree = true).assertExists()
    }

    @Test
    fun showBatteryOffShowsNoLineUnderTheDetailsAndListensToNothing() {
        showMeasuredGame()
        assertEquals("off: no line under the details", 0, linesUnder(HomeTags.DETAILS))
        assertEquals("off: no battery receiver", 0, batteryReceivers())
        showBattery = true
        compose.waitForIdle()
        assertEquals("on: the line under the details", 1, linesUnder(HomeTags.DETAILS))
        assertEquals("on: one receiver for the screen", 1, batteryReceivers())
        showBattery = false
        compose.waitForIdle()
        assertEquals("switched off while up: the line goes", 0, lines())
        assertEquals("switched off while up: unregistered", 0, batteryReceivers())
    }

    @Test
    fun infoFollowsShowBattery() {
        showBattery = true
        showMeasuredGame()
        send(Action.Menu)
        compose.onNodeWithTag(GamePanelTags.row(GameMenuEntryId.Info.name), useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals("on: Info's battery row", 1, linesUnder(GamePanelTags.FACTS))
        showBattery = false
        compose.waitForIdle()
        assertEquals("off: no battery row in Info", 0, linesUnder(GamePanelTags.FACTS))
        assertEquals("off: no line anywhere", 0, lines())
        assertEquals("off: no battery receiver", 0, batteryReceivers())
    }

    private companion object {
        /** Past the details' settle (150 ms of frame time), when the play facts are read. */
        const val SETTLED_MS = 400L
    }
}
