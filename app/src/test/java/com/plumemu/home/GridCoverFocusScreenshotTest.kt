package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
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
 * The focused grid cover (larger, ringed, with its halo) at 640x480 and 832x468, Graphite and Paper, on a Super Nintendo grid (one cover
 * shape) and on Recent (mixed shapes), the focus in the middle of the grid and on the first cover (an edge):
 * `grid_cover_focus_<size>_<theme>_<single|mixed>_<middle|edge>.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GridCoverFocusScreenshotTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun shoot(
        size: String,
        look: String,
        settings: PlumThemeSettings,
        mixed: Boolean,
    ) {
        var strip: StripState? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(settings) {
                if (mixed) {
                    val entry = MixedGridLibrary.entries[0]
                    HomeWithArt(rememberSource { mixedGridSource() }, gridActions(0, 0), MixedGridLibrary, gridIds = setOf(entry.id), onState = { strip = it })
                } else {
                    Snes(onState = { strip = it })
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        // The edge cover first: index 0 is the first of the first row, at the grid's left edge (the cover the grid opens on).
        for ((place, index) in listOf("edge" to 0, "middle" to if (mixed) 5 else 4)) {
            compose.runOnUiThread { checkNotNull(strip).dispatch(Action.TapItem(Column.Games, index)) }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS + 100L)
            // The cell records the lifted cover while drawing; apply that write and draw again, as the next frame does.
            compose.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/grid_cover_focus_${size}_${look}_${if (mixed) "mixed" else "single"}_$place.png")
        }
    }

    @Composable
    private fun Snes(onState: (StripState) -> Unit) =
        HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes"), onState = onState)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun fourThreeGraphiteSingle() = shoot("640x480", "graphite", TableText.Graphite, false)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun fourThreePaperSingle() = shoot("640x480", "paper", TableText.Paper, false)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun fourThreeGraphiteMixed() = shoot("640x480", "graphite", TableText.Graphite, true)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun fourThreePaperMixed() = shoot("640x480", "paper", TableText.Paper, true)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xxhdpi")
    fun pocket6GraphiteSingle() = shoot("832x468", "graphite", TableText.Graphite, false)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xxhdpi")
    fun pocket6PaperSingle() = shoot("832x468", "paper", TableText.Paper, false)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xxhdpi")
    fun pocket6GraphiteMixed() = shoot("832x468", "graphite", TableText.Graphite, true)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xxhdpi")
    fun pocket6PaperMixed() = shoot("832x468", "paper", TableText.Paper, true)
}
