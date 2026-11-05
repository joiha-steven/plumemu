package com.plumemu.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
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

/** The last step with no folder and with no games fits the handhelds' sizes: Start playing lies whole above the hints. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupReadyEmptyFitTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val noFolder = SetupUiState(step = SetupStep.Ready, focus = SetupControl.StartPlaying)
    private val noGames = SetupFakes.ready.copy(folders = listOf(SetupFakes.romsFolder.copy(gameCount = 0, systemCount = 0)), systems = emptyList())

    private fun assertFits(
        state: SetupUiState,
        size: TextSize,
    ) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                Box(Modifier.fillMaxSize()) { SetupLayout(state, ButtonLetters.Default, onAction = {}, clock = { "21:40" }, battery = { "82%" }) }
            }
        }
        compose.waitForIdle()
        val button = compose.onNodeWithTag(SetupTags.control(SetupControl.StartPlaying)).getBoundsInRoot()
        val hints = compose.onNodeWithTag(SetupTags.HINTS).getBoundsInRoot()
        val gap = hints.top.value - button.bottom.value
        assertTrue("Start playing's bottom ${button.bottom} must lie ${PlumDimens.footerGap} above the hints' top ${hints.top}", gap >= PlumDimens.footerGap.value - 0.5f)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun noFolderAt768x432() = assertFits(noFolder, TextSize.Medium)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun noFolderAt576x432Largest() = assertFits(noFolder, TextSize.Largest)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun noGamesAt832x468() = assertFits(noGames, TextSize.Medium)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun noGamesAt576x432Largest() = assertFits(noGames, TextSize.Largest)
}
