package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Shots drawn the way the design boards are: Medium text size and Thin weight (the type table's own weights), so a board crop and a
 * shot compare at one size. The app's default is Large text at the Medium weight (one face heavier), which draws about 10 % wider.
 * Files `spec_pair_*`; the top bar and info line of the cover grid, and the empty state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SpecPairShotsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun boardLook(settings: PlumThemeSettings) = settings.copy(textSize = TextSize.Small)

    private fun grid(
        size: String,
        look: String,
        settings: PlumThemeSettings,
    ) {
        compose.mainClock.autoAdvance = false
        var strip: StripState? = null
        compose.setContent {
            PlumTheme(boardLook(settings)) {
                HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 4), gridIds = setOf("snes"), onState = { strip = it })
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { checkNotNull(strip).dispatch(Action.Move(Direction.Right)) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS + 100L)
        compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/spec_pair_grid_${size}_$look.png")
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun grid768Graphite() = grid("768x432", "graphite", PlumThemeSettings.Graphite)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun grid768Paper() = grid("768x432", "paper", PlumThemeSettings.Paper)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun grid576Graphite() = grid("576x432", "graphite", PlumThemeSettings.Graphite)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun grid576Paper() = grid("576x432", "paper", PlumThemeSettings.Paper)

    @Test
    fun emptyState768() {
        RuntimeEnvironment.setQualifiers("w768dp-h432dp-land-xhdpi")
        for ((look, settings) in listOf("graphite" to PlumThemeSettings.Graphite, "paper" to PlumThemeSettings.Paper)) {
            captureRoboImage(filePath = "${ReferenceShots.OUTPUT_DIR}/spec_pair_empty_primary_768x432_$look.png") {
                PlumTheme(boardLook(settings)) {
                    val notices = HomeNotices(emptyLibrary = MutableStateFlow(EmptyLibrary.NoGames)).also { it.emptyFocus.inButtons = true }
                    CompositionLocalProvider(LocalHomeNotices provides notices) { HomeAt(FakeHomeContent.noGames()) }
                }
            }
        }
    }
}
