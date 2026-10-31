package com.plumemu.home

import com.plumemu.coreui.theme.TextSize
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
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
 * The grid sizes with real-looking box fronts at the systems' measured shapes, as
 * the library gives them: Super Nintendo 1.37 (landscape), PlayStation 1.17 (square), Mega Drive
 * 0.71 (tall). Medium, the default, for all three; Small and Large for the tall grid on the RP5.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeGridSizeScreenshotTest {
    @Test
    fun mediumLandscape() = ReferenceShots.capture("home_grid_medium_landscape") { MeasuredGrid("snes", GridSize.Medium) }

    @Test
    fun mediumSquare() = ReferenceShots.capture("home_grid_medium_square") { MeasuredGrid("psx", GridSize.Medium) }

    @Test
    fun mediumTall() = ReferenceShots.capture("home_grid_medium_tall") { MeasuredGrid("megadrive", GridSize.Medium) }

    @Test
    fun smallTall() = rp5Graphite("home_grid_small_tall") { MeasuredGrid("megadrive", GridSize.Small) }

    @Test
    fun largeTall() = rp5Graphite("home_grid_large_tall") { MeasuredGrid("megadrive", GridSize.Large) }

    /** One shot at 768x432 in Graphite, named as [ReferenceShots] names it. */
    private fun rp5Graphite(
        screen: String,
        content: @Composable () -> Unit,
    ) {
        val size = ReferenceShots.Size.Wide
        val preset = ReferenceShots.Preset.Graphite
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        captureRoboImage(filePath = "${ReferenceShots.OUTPUT_DIR}/${ReferenceShots.fileName(screen, size, preset)}") {
            PlumTheme(preset.settings.copy(textSize = TextSize.Medium)) { content() }
        }
    }
}

/** The systems' measured box-front shapes (platform data). */
private val MEASURED_SHAPES = mapOf("snes" to CoverShape(1.37f), "psx" to CoverShape(1.17f), "megadrive" to CoverShape(0.71f))

/** The board's library with [MEASURED_SHAPES] in place of the fakes' two shapes. */
private val MeasuredLibrary: HomeContent by lazy {
    CoverArtLibrary.let { board -> board.withEntries(board.entries.map { entry -> MEASURED_SHAPES[entry.id]?.let { entry.copy(coverShape = it) } ?: entry }) }
}

/** System [id]'s grid at [size] with real-looking covers, its second cover focused (on the top row at every size). */
@Composable
private fun MeasuredGrid(
    id: String,
    size: GridSize,
) = ProvideGridSize(size) {
    HomeWithArt(rememberSource { realCoverSource() }, gridActions(boardIndexOf(id), 1), MeasuredLibrary, gridIds = setOf(id))
}
