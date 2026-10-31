package com.plumemu.home

import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * The cover grid with the soft shadow under every cover and the focused cover lifted: at rest (grown, sweep over) and caught in the middle of its one light sweep, at
 * 768x432 and 576x432, Graphite and Paper: `home_grid_lift_<size>_<theme>_{rest,sweep}.png`.
 * The sweep frame is fixed by pinning the test clock: the rest delay and half the sweep after the
 * focus arrived.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeCoverLiftScreenshotTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun shoot(
        size: String,
        look: String,
        settings: PlumThemeSettings,
    ) {
        compose.mainClock.autoAdvance = false
        var strip: StripState? = null
        compose.setContent {
            PlumTheme(settings) {
                // The grid opens on the cover before the middle row's one; a move then brings the focus to it (covers on
                // every side of it), because a cover focused as the grid opens is lifted at once and never sweeps.
                HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 4), gridIds = setOf("snes"), onState = { strip = it })
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { checkNotNull(strip).dispatch(Action.Move(Direction.Right)) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS + PlumMotion.FOCUS_SWEEP_MS / 2L)
        val mismatches = listOfNotNull(snap("home_grid_lift_${size}_${look}_sweep"))
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_MS.toLong())
        val all = mismatches + listOfNotNull(snap("home_grid_lift_${size}_${look}_rest"))
        if (all.isNotEmpty()) throw AssertionError("${all.size} screenshot(s) differ:\n" + all.joinToString("\n"))
    }

    private fun snap(name: String): String? =
        try {
            compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$name.png")
            null
        } catch (e: AssertionError) {
            "$name: ${e.message}"
        }

    /** A Small-size tall grid (PSP-like, 0.575): the titled tiles' narrowest width (70 dp is accepted), titles must still read. */
    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun narrowSmallTallPlaceholders() {
        compose.setContent {
            PlumTheme(MediumText.Default.copy(gridSize = GridSize.Small)) {
                val tall = CoverArtLibrary.let { board -> board.withEntries(board.entries.map { if (it.id == "psp") it.copy(coverShape = CoverShape(0.575f)) else it }) }
                // No pictures: every cover is the titled tile.
                HomeAt(tall, gridActions(PSP_INDEX, 1), gridIds = setOf("psp"))
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/home_grid_small_tall_titles_576x432.png")
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun wideGraphite() = shoot("768x432", "graphite", PlumThemeSettings.Graphite)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun widePaper() = shoot("768x432", "paper", PlumThemeSettings.Paper)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun narrowGraphite() = shoot("576x432", "graphite", PlumThemeSettings.Graphite)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun narrowPaper() = shoot("576x432", "paper", PlumThemeSettings.Paper)
}
