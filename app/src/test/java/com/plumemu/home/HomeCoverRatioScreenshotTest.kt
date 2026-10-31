package com.plumemu.home

import androidx.compose.runtime.Composable
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Box fronts whose shape is not their slot's (`HomeCoverRatioTest` measures them): portrait art in
 * the Super Nintendo's wide cells, wide art in Mega Drive's tall cells, square art in PSP's, and the
 * details of a square and a wide box. At 768x432 and 576x432, Graphite and Paper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeCoverRatioScreenshotTest {
    @Test
    fun portraitArtInWideCells() = ReferenceShots.capture("home_art_ratio_grid_portrait_in_wide") { RealCoverGrid("snes", 1, GridSize.Large) { _, _ -> 0.58f } }

    @Test
    fun wideArtInTallCells() = ReferenceShots.capture("home_art_ratio_grid_wide_in_tall") { RealCoverGrid("megadrive", 1, GridSize.Large) { _, _ -> 1.6f } }

    @Test
    fun squareArtInTallCells() = ReferenceShots.capture("home_art_ratio_grid_square_in_tall") { RealCoverGrid("psp", 1) { _, _ -> 1.0f } }

    @Test
    fun detailsSquareBox() = ReferenceShots.capture("home_art_ratio_details_square") { DetailsOfAspect(1.0f) }

    @Test
    fun detailsWideBox() = ReferenceShots.capture("home_art_ratio_details_wide") { DetailsOfAspect(1.6f) }
}

/** Persona 3 Portable's details with its large box front saved at [aspect]. */
@Composable
private fun DetailsOfAspect(aspect: Float) {
    val source = rememberSource { realCoverSource().also { it.withLargeAspect("psp", PERSONA_3_PORTABLE, aspect) } }
    RealCoverDetails("psp", PERSONA_3_PORTABLE, source)
}
