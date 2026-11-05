package com.plumemu.home

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.ProvideGridSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The grid filling the content width at every Size step: a one-shape system (Nintendo DS) and a mixed one (Super Nintendo), at the
 * Thor's 837x471, 640x480 and 576x432, Graphite and Paper. Files are `grid_fill_<system>_<step>_<size>_<theme>.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GridFillScreenshotTest {
    @Test
    fun oneShapeSmall() = shoot("nds", GridSize.Small)

    @Test
    fun oneShapeMedium() = shoot("nds", GridSize.Medium)

    @Test
    fun oneShapeLarge() = shoot("nds", GridSize.Large)

    @Test
    fun mixedSmall() = shoot("snes", GridSize.Small)

    @Test
    fun mixedMedium() = shoot("snes", GridSize.Medium)

    @Test
    fun mixedLarge() = shoot("snes", GridSize.Large)

    private fun shoot(
        system: String,
        step: GridSize,
    ) {
        val mismatches = ArrayList<String>()
        for ((width, height) in SIZES) {
            RuntimeEnvironment.setQualifiers("w${width}dp-h${height}dp-land-xhdpi")
            for (preset in ReferenceShots.Preset.entries) {
                val name = "grid_fill_${system}_${step.name.lowercase()}_${width}x${height}_${preset.label}.png"
                try {
                    captureRoboImage(filePath = "${ReferenceShots.OUTPUT_DIR}/$name") { PlumTheme(preset.settings) { Board(system, step) } }
                } catch (e: AssertionError) {
                    mismatches += "$name: ${e.message}"
                }
            }
        }
        if (mismatches.isNotEmpty()) throw AssertionError("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
    }

    private companion object {
        val SIZES = listOf(837 to 471, 640 to 480, 576 to 432)
    }
}

/** System [id]'s grid at [step]: Nintendo DS in its own one shape, Super Nintendo with a mix of box shapes; one cover focused. */
@Composable
private fun Board(
    id: String,
    step: GridSize,
) = ProvideGridSize(step) {
    val aspectOf = { system: String, index: Int -> if (system == "nds") NDS_SHAPE.widthOverHeight else MIX[index % MIX.size] }
    if (id == "nds") {
        HomeWithArt(rememberSource { realCoverSource(NdsLibrary, aspectOf) }, gridActions(NDS_INDEX, SQUARE_GRID_FOCUS), NdsLibrary, gridIds = setOf("nds"))
    } else {
        HomeWithArt(rememberSource { realCoverSource(CoverArtLibrary, aspectOf) }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, MEGA_MAN_X), CoverArtLibrary, gridIds = setOf("snes"))
    }
}

/** The board's library with Nintendo DS in its one shape. */
private val NdsLibrary: HomeContent by lazy { CoverArtLibrary.let { board -> board.withEntries(board.entries.map { e -> if (e.id == "nds") e.copy(coverShape = NDS_SHAPE) else e }) } }

/** Landscape, DS-like, square, poster and GBA-like shapes in turn. */
private val MIX = listOf(1.4f, 0.9f, 1.0f, 0.7f, 1.5f, 1.4f, 1.0f, 0.9f, 0.7f, 1.5f, 1.0f)
