package com.plumemu.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.ButtonLetters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Ready step at the sizes of the handhelds: Start playing lies whole above the hint bar once the step has
 * settled (640 x 480 and 576 x 432 with larger text, 768 x 432), and its three info rows start their text at one x.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupReadyFitTest {
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

    private fun assertStartPlayingAboveTheHints(what: String = "") {
        val button = compose.onNodeWithTag(SetupTags.control(SetupControl.StartPlaying)).getBoundsInRoot()
        val hints = compose.onNodeWithTag(SetupTags.HINTS).getBoundsInRoot()
        val gap = hints.top.value - button.bottom.value
        assertTrue("$what: Start playing's bottom ${button.bottom} must lie ${PlumDimens.footerGap} above the hints' top ${hints.top}", gap >= PlumDimens.footerGap.value - TOLERANCE)
    }

    private fun textLeft(tag: String): Float =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and hasText("", substring = true)).onFirst().getBoundsInRoot().left.value

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun startPlayingIsWholeAbove640x480WithLargerText() {
        show(SetupFakes.readyWithBoxArt, TextSize.Larger)
        assertStartPlayingAboveTheHints()
    }

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun startPlayingIsWholeAbove640x480WithNamesBeingFilledIn() {
        show(SetupFakes.readyStillNaming.copy(hasScreenScraper = true, isBoxArtComing = true, boxArtLine = BoxArtLine.Coming), TextSize.Larger)
        assertStartPlayingAboveTheHints()
    }

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun startPlayingIsWholeAbove640x480WithTableText() {
        show(SetupFakes.readyWithBoxArt, TextSize.Small)
        assertStartPlayingAboveTheHints()
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun startPlayingIsWholeAbove576x432WithLargerText() {
        show(SetupFakes.readyWithBoxArt, TextSize.Larger)
        assertStartPlayingAboveTheHints()
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun startPlayingIsWholeAbove768x432WithLargerText() {
        show(SetupFakes.readyWithBoxArt, TextSize.Larger)
        assertStartPlayingAboveTheHints()
    }

    /** The emulator check answers after the step opened: its row grows by its description's lines, and the button must follow. */
    private fun assertStaysWholeWhenTheEmulatorRowGrows(vararg sizes: TextSize) {
        val state = mutableStateOf(SetupFakes.readyWithBoxArt.copy(emulators = null))
        val size = mutableStateOf(sizes.first())
        compose.setContent {
            key(size.value) {
                PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size.value)) {
                    Box(Modifier.fillMaxSize()) { SetupLayout(state.value, ButtonLetters.Default, onAction = {}, clock = { "21:40" }, battery = { "82%" }) }
                }
            }
        }
        for (each in sizes) {
            state.value = SetupFakes.readyWithBoxArt.copy(emulators = null)
            size.value = each
            compose.waitForIdle()
            state.value = SetupFakes.readyWithBoxArt.copy(emulators = EmulatorCoverage(12, 12, emptyList()))
            compose.waitForIdle()
            assertStartPlayingAboveTheHints("$each")
        }
    }

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun startPlayingStaysWholeAs640x480EmulatorRowGrows() = assertStaysWholeWhenTheEmulatorRowGrows(TextSize.Larger, TextSize.Huge, TextSize.Largest)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun startPlayingStaysWholeAs576x432EmulatorRowGrows() = assertStaysWholeWhenTheEmulatorRowGrows(TextSize.Larger, TextSize.Huge, TextSize.Largest)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun startPlayingStaysWholeAs768x432EmulatorRowGrows() = assertStaysWholeWhenTheEmulatorRowGrows(TextSize.Larger, TextSize.Huge, TextSize.Largest)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun everyInfoRowStartsItsTextAtTheSameX() {
        show(SetupFakes.readyWithBoxArt.copy(asksAboutStatistics = true), TextSize.Small)
        val emulators = textLeft(SetupTags.EMULATORS)
        val boxArt = textLeft(SetupTags.BOX_ART_COMING)
        val statistics = textLeft(SetupTags.control(SetupControl.UsageStatistics))
        assertEquals("Box art row text x vs the emulators row", emulators, boxArt, 0.01f)
        assertEquals("Statistics row text x vs the emulators row", emulators, statistics, 0.01f)
    }

    private companion object {
        const val TOLERANCE = 0.6f
    }
}
