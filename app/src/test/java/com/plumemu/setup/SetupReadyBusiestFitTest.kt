package com.plumemu.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
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

/**
 * The Ready step with every row it can show (names still filling in, box art coming, the statistics question) at 640 x 480 and
 * 832 x 468: Start playing lies whole above the hint bar once the step has settled, and at the default size nothing needs a scroll.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupReadyBusiestFitTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(
        state: SetupUiState,
        size: TextSize,
    ) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                Box(Modifier.fillMaxSize()) { SetupLayout(state, ButtonLetters.Default, onAction = {}, clock = { "21:40" }, battery = { "82%" }) }
            }
        }
        compose.waitForIdle()
    }

    private val busiest get() = SetupFakes.readyStillNaming.copy(hasScreenScraper = true, isBoxArtComing = true, boxArtLine = BoxArtLine.Coming, asksAboutStatistics = true)

    private fun assertAbove(what: String) {
        val button = compose.onNodeWithTag(SetupTags.control(SetupControl.StartPlaying)).getBoundsInRoot()
        val hints = compose.onNodeWithTag(SetupTags.HINTS).getBoundsInRoot()
        val gap = hints.top.value - button.bottom.value
        assertTrue("$what: Start playing's bottom ${button.bottom} must lie ${PlumDimens.footerGap} above the hints' top ${hints.top}", gap >= PlumDimens.footerGap.value - 0.6f)
    }

    private fun assertWholeScreen(what: String) {
        val scroll = compose.onNodeWithTag(SetupTags.SCROLL).fetchSemanticsNode()
        val title = compose.onNodeWithText("Your library is ready").getBoundsInRoot()
        assertTrue("$what: the title must not be scrolled off the top: ${title.top}", title.top.value >= 0f)
        assertAbove(what)
        assertTrue("$what: nothing needs a scroll at the default size", scroll.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke() == 0f)
    }

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun fitsWithoutScrollAt640x480() {
        show(busiest, TextSize.Small)
        assertWholeScreen("640x480")
    }

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun fitsWithoutScrollAt832x468() {
        show(busiest, TextSize.Small)
        assertWholeScreen("832x468")
    }

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun startPlayingIsAboveTheHintsAt640x480WithLargerText() {
        show(busiest, TextSize.Larger)
        assertAbove("640x480 Larger")
    }

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun startPlayingIsAboveTheHintsAt832x468WithLargerText() {
        show(busiest, TextSize.Larger)
        assertAbove("832x468 Larger")
    }
}
