package com.plumemu.home

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.components.BAR_FADE_BOTTOM
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.ProvideGridSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A grid row the strip's end cuts shows its top part fading out above the hint keys, as the games list does; it is left out only when under a
 * third of it would show. The strip ends [PlumDimens.footerGap] above the hint bar, where the fade has finished: that gap is part of the room a
 * row is judged by, or a row a hair under a third of the way in vanishes and the grid ends in an empty band.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class GridCutRowBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    protected fun aCutRowAGapShortOfAThirdStillShows(step: GridSize) {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { ProvideGridSize(step) { ArtGridSquareBoard() } } }
        compose.waitForIdle()
        val d = compose.density.density
        val fadeEnd = compose.onNodeWithTag(BAR_FADE_BOTTOM, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom.value
        val fadeStart = compose.onNodeWithTag(BAR_FADE_BOTTOM, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value
        val gap = PlumDimens.footerGap.value
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val background = image.getPixel(2, image.height / 2)
        val rows = HashMap<Float, Triple<Float, Float, Float>>()
        for (i in 0 until 60) {
            val n = compose.onAllNodesWithTag(HomeTags.cover(i), useUnmergedTree = true)
            if (n.fetchSemanticsNodes().isEmpty()) continue
            val b = n[0].getUnclippedBoundsInRoot()
            rows.putIfAbsent(b.top.value, Triple(b.bottom.value - b.top.value, b.left.value, b.right.value))
        }
        var checked = 0
        for ((top, shape) in rows) {
            val (height, left, right) = shape
            val isCutAtTheEnd = top < fadeEnd && top + height > fadeEnd
            if (!isCutAtTheEnd || fadeEnd + gap - top < height / 3f) continue
            checked++
            var most = 0
            for (y in ((top + 1f) * d).toInt() until (minOf(fadeStart, top + height) * d).toInt()) {
                for (x in (left * d).toInt() until (right * d).toInt().coerceAtMost(image.width)) {
                    val p = image.getPixel(x, y)
                    most = maxOf(most, abs((p shr 16 and 255) - (background shr 16 and 255)), abs((p shr 8 and 255) - (background shr 8 and 255)), abs((p and 255) - (background and 255)))
                }
            }
            assertTrue("$step: the row cut at the strip's end (top $top dp, ${fadeEnd + gap - top} of $height dp in) is left out (ink $most)", most > INK)
        }
        assertTrue("$step: no row is cut at the strip's end in this board", checked > 0)
    }

    private companion object {
        /** More than a step a person does not see (the slab and glow ramps stay under it on a plain background). */
        const val INK = 12
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class GridCutRow640Test : GridCutRowBase() {
    @Test fun large() = aCutRowAGapShortOfAThirdStillShows(GridSize.Large)

    @Test fun medium() = aCutRowAGapShortOfAThirdStillShows(GridSize.Medium)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GridCutRow576Test : GridCutRowBase() {
    @Test fun large() = aCutRowAGapShortOfAThirdStillShows(GridSize.Large)

    @Test fun medium() = aCutRowAGapShortOfAThirdStillShows(GridSize.Medium)
}
