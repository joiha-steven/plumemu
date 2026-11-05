package com.plumemu.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/** The focus halo on the tiles of the app-games grids goes through the same ring pass as Apps: on Graphite, none on Paper. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class AppGamesHaloTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var background = Color.Unspecified

    /** How far (0..255, the largest channel) the pixel left of the focused tile, just outside its ring, is from the background. */
    private fun haloBesideTile(
        theme: PlumThemeSettings,
        entry: Int,
        focused: Int,
    ): Int {
        compose.setContent {
            PlumTheme(theme.copy(animationsOn = false)) {
                background = PlumTheme.colors.bg
                AppGamesBoard(entry, focused = focused)
            }
        }
        compose.waitForIdle()
        val tile = compose.onNodeWithTag(HomeTags.cover(focused), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val x = tile.left - PROBE_DP * compose.density.density
        val p = compose.onRoot().captureToImage().toPixelMap()[x.roundToInt(), tile.center.y.roundToInt()]
        return (maxOf(abs(p.red - background.red), abs(p.green - background.green), abs(p.blue - background.blue)) * 255f).roundToInt()
    }

    @Test
    fun theFocusedAndroidGameTileHasTheHaloOnGraphite() {
        val halo = haloBesideTile(TableText.Graphite, ANDROID_GAMES_ENTRY, focused = 0)
        assertTrue("beside the ring the focused tile has a halo (was $halo)", halo >= HALO_MIN)
    }

    @Test
    fun theFocusedAndroidGameTileHasNoHaloOnPaper() {
        val halo = haloBesideTile(TableText.Paper, ANDROID_GAMES_ENTRY, focused = 0)
        assertTrue("beside the ring the focused tile has no halo (was $halo)", halo <= NO_HALO_MAX)
    }

    private companion object {
        /** Just outside the ring, which here wraps the whole cell (icon and name) and sits about 10 dp from it. */
        const val PROBE_DP = 14f
        const val HALO_MIN = 20
        const val NO_HALO_MAX = 10
    }
}
