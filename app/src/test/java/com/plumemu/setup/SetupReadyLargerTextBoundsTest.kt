package com.plumemu.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.ButtonLetters
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Retroid Pocket Nova with setup's larger text: on 640 x 432 (with Android's navigation bar) and 640 x 480
 * the Ready step's Start playing sits whole above the hint bar once the first reveal has run, with every line
 * the step can show (box art coming, names still being filled in).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h432dp-land-xhdpi")
class SetupReadyLargerTextBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun startPlayingIsWholeAboveTheHintsOnTheNavigationBarScreen() = check(SetupFakes.readyWithBoxArt, "box art line")

    @Test
    fun startPlayingIsWholeAboveTheHintsWhileNamesAreFilledIn() = check(SetupFakes.readyStillNaming, "naming line")

    @Test
    fun startPlayingIsWholeAboveTheHintsWithoutExtraLines() = check(SetupFakes.ready, "plain")

    @Test
    fun startPlayingStaysWholeWhenALineAppearsAfterTheStepOpened() {
        val state = mutableStateOf(SetupFakes.ready)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Larger)) {
                Box(Modifier.fillMaxSize()) { SetupLayout(state.value, ButtonLetters.Default, onAction = {}, clock = { "21:40" }, battery = { "82%" }) }
            }
        }
        compose.mainClock.advanceTimeBy(SETTLED_MS)
        state.value = SetupFakes.readyWithBoxArt
        compose.mainClock.advanceTimeBy(SETTLED_MS)
        assertAbove("line appeared")
    }

    @Test
    fun startPlayingIsWholeWhenTheScanStepHandsOverToReady() {
        val state = mutableStateOf(SetupFakes.scanning)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Larger)) {
                Box(Modifier.fillMaxSize()) { SetupLayout(state.value, ButtonLetters.Default, onAction = {}, clock = { "21:40" }, battery = { "82%" }) }
            }
        }
        compose.mainClock.advanceTimeBy(SETTLED_MS)
        state.value = SetupFakes.readyWithBoxArt
        compose.mainClock.advanceTimeBy(SETTLED_MS)
        assertAbove("after the scan")
    }

    private fun assertAbove(what: String) {
        val button = compose.onNodeWithTag(SetupTags.control(SetupControl.StartPlaying)).getBoundsInRoot()
        val hints = compose.onNodeWithTag(SetupTags.HINTS).getBoundsInRoot()
        assertTrue("$what: Start playing bottom ${button.bottom} must be above the hints' top ${hints.top}", button.bottom <= hints.top - GAP_MIN.dp)
    }

    private fun check(
        state: SetupUiState,
        what: String,
    ) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Larger)) {
                Box(Modifier.fillMaxSize()) { SetupLayout(state, ButtonLetters.Default, onAction = {}, clock = { "21:40" }, battery = { "82%" }) }
            }
        }
        compose.waitForIdle()
        assertAbove(what)
    }

    private companion object {
        const val SETTLED_MS = 2_000L
        const val GAP_MIN = 4
    }
}
