package com.plumemu.settings.screen

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Statistics when the read failed, by gamepad, in a window too short for the whole page at the largest text (the
 * explanation and footer then lie below the edge): Down on the switch scrolls on to them, Up comes back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h300dp-land-xhdpi")
class SettingsStatisticsFailedInputTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter

    private fun send(action: Action) {
        compose.runOnIdle { router.send(action) }
        compose.waitForIdle()
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    private fun bottom(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom.value

    private fun footerInView() = bottom(StatisticsTags.FOOTER) <= bottom(StatisticsTags.PAGE) + 0.5f

    @Test
    fun downOnTheSwitchScrollsOnToTheExplanationAndFooterAndUpComesBack() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) {
                SettingsScreen(
                    PlumSettings(flags = SetupFlags(setupCompleted = true)), router, NoActions, TEST_VERSION,
                    content = SettingsContent(statistics = StatisticsState.Failed), showOnHome = { _, _ -> },
                )
            }
        }
        repeat(SettingsSection.Statistics.shownIndex) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        assertTrue("premise: the footer starts below the fold", !footerInView())
        var presses = 0
        while (!footerInView() && presses < MAX_PRESSES) {
            send(Action.Move(Direction.Down))
            presses++
        }
        assertTrue("the footer came into view after $presses presses", footerInView())
        assertTrue("the explanation is whole too", bottom(StatisticsTags.EXACT_PLAY_TIME_BODY) <= bottom(StatisticsTags.PAGE) + 0.5f)
        // Up first stops where the switch is whole again, then goes on to the top.
        var ups = 0
        while (Math.abs(top(StatisticsTags.UNREADABLE) - top(StatisticsTags.PAGE)) >= 0.5f && ups < MAX_PRESSES) {
            send(Action.Move(Direction.Up))
            ups++
        }
        assertTrue("Up scrolls back to the top", Math.abs(top(StatisticsTags.UNREADABLE) - top(StatisticsTags.PAGE)) < 0.5f)
    }

    private companion object {
        const val MAX_PRESSES = 12
    }
}
