package com.plumemu.home

import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
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
 * The cover grid with every cover in its collar and the focused cover's groove lit, at 768x432 and 576x432, Graphite and Paper:
 * `home_grid_focus_<size>_<theme>.png`. The clock is pinned: the shot is taken once the groove has finished lighting.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeCoverFocusScreenshotTest {
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
                // The grid opens on the cover before the middle row's one; a move then brings the focus to it (covers on every side of it).
                HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 4), gridIds = setOf("snes"), onState = { strip = it })
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { checkNotNull(strip).dispatch(Action.Move(Direction.Right)) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS + 100L)
        val mismatch = snap("home_grid_focus_${size}_${look}")
        if (mismatch != null) throw AssertionError("screenshot differs:\n$mismatch")
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
    fun narrowSmallTallPlaceholders() = smallTallPlaceholders("576x432")

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun wideSmallTallPlaceholders() = smallTallPlaceholders("768x432")

    private fun smallTallPlaceholders(size: String) {
        compose.setContent {
            PlumTheme(TableText.Default.copy(gridSize = GridSize.Small)) {
                val tall = CoverArtLibrary.let { board -> board.withEntries(board.entries.map { if (it.id == "psp") it.copy(coverShape = CoverShape(0.575f)) else it }) }
                // No pictures: every cover is the titled tile.
                HomeAt(tall, gridActions(PSP_INDEX, 1), gridIds = setOf("psp"))
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/home_grid_small_tall_titles_$size.png")
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

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xxhdpi")
    fun pocket6Graphite() = shoot("832x468", "graphite", PlumThemeSettings.Graphite)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xxhdpi")
    fun pocket6Paper() = shoot("832x468", "paper", PlumThemeSettings.Paper)
}
