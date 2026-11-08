package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.AccentChoice
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.FocusLift
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The focused cover at the grid's edges at 576, 640, 768, 832 and 853, Graphite and Paper, and the grid flush to the right margin (helpers: [GridCoverFocusBase]). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = SIZE_640)
class GridCoverFocusEdgesTest : GridCoverFocusBase() {
    @Test
    @Config(qualifiers = SIZE_640)
    fun liftIsWholeAtTheEdgesSingleGraphiteAt640() {
        show(TableText.Graphite)
        assertWholeAtEdges("640 single graphite")
    }

    @Test
    @Config(qualifiers = SIZE_640)
    fun liftIsWholeAtTheEdgesMixedGraphiteAt640() {
        show(TableText.Graphite, entry = 0, id = "recent")
        assertWholeAtEdges("640 mixed graphite", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_640)
    fun liftIsWholeAtTheEdgesSinglePaperAt640() {
        show(TableText.Paper)
        assertWholeAtEdges("640 single paper")
    }

    @Test
    @Config(qualifiers = SIZE_640)
    fun liftIsWholeAtTheEdgesMixedPaperAt640() {
        show(TableText.Paper, entry = 0, id = "recent")
        assertWholeAtEdges("640 mixed paper", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_576)
    fun liftIsWholeAtTheEdgesSingleGraphiteAt576() {
        show(TableText.Graphite)
        assertWholeAtEdges("576 single graphite")
    }

    @Test
    @Config(qualifiers = SIZE_576)
    fun liftIsWholeAtTheEdgesMixedGraphiteAt576() {
        show(TableText.Graphite, entry = 0, id = "recent")
        assertWholeAtEdges("576 mixed graphite", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_576)
    fun liftIsWholeAtTheEdgesSinglePaperAt576() {
        show(TableText.Paper)
        assertWholeAtEdges("576 single paper")
    }

    @Test
    @Config(qualifiers = SIZE_576)
    fun liftIsWholeAtTheEdgesMixedPaperAt576() {
        show(TableText.Paper, entry = 0, id = "recent")
        assertWholeAtEdges("576 mixed paper", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_832)
    fun liftIsWholeAtTheEdgesSingleGraphiteAt832() {
        show(TableText.Graphite)
        assertWholeAtEdges("832 single graphite")
    }

    @Test
    @Config(qualifiers = SIZE_832)
    fun liftIsWholeAtTheEdgesMixedGraphiteAt832() {
        show(TableText.Graphite, entry = 0, id = "recent")
        assertWholeAtEdges("832 mixed graphite", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_832)
    fun liftIsWholeAtTheEdgesSinglePaperAt832() {
        show(TableText.Paper)
        assertWholeAtEdges("832 single paper")
    }

    @Test
    @Config(qualifiers = SIZE_832)
    fun liftIsWholeAtTheEdgesMixedPaperAt832() {
        show(TableText.Paper, entry = 0, id = "recent")
        assertWholeAtEdges("832 mixed paper", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_853)
    fun liftIsWholeAtTheEdgesSingleGraphiteAt853() {
        show(TableText.Graphite)
        assertWholeAtEdges("853 single graphite")
    }

    @Test
    @Config(qualifiers = SIZE_853)
    fun liftIsWholeAtTheEdgesMixedGraphiteAt853() {
        show(TableText.Graphite, entry = 0, id = "recent")
        assertWholeAtEdges("853 mixed graphite", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_853)
    fun liftIsWholeAtTheEdgesSinglePaperAt853() {
        show(TableText.Paper)
        assertWholeAtEdges("853 single paper")
    }

    @Test
    @Config(qualifiers = SIZE_853)
    fun liftIsWholeAtTheEdgesMixedPaperAt853() {
        show(TableText.Paper, entry = 0, id = "recent")
        assertWholeAtEdges("853 mixed paper", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_768)
    fun liftIsWholeAtTheEdgesSingleGraphiteAt768() {
        show(TableText.Graphite)
        assertWholeAtEdges("768 single graphite")
    }

    @Test
    @Config(qualifiers = SIZE_768)
    fun liftIsWholeAtTheEdgesMixedGraphiteAt768() {
        show(TableText.Graphite, entry = 0, id = "recent")
        assertWholeAtEdges("768 mixed graphite", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_768)
    fun liftIsWholeAtTheEdgesSinglePaperAt768() {
        show(TableText.Paper)
        assertWholeAtEdges("768 single paper")
    }

    @Test
    @Config(qualifiers = SIZE_768)
    fun liftIsWholeAtTheEdgesMixedPaperAt768() {
        show(TableText.Paper, entry = 0, id = "recent")
        assertWholeAtEdges("768 mixed paper", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_576)
    fun theGridIsFlushToTheRightMarginAt576() {
        show(TableText.Graphite)
        assertFlushToTheRightMargin("576")
    }

    @Test
    @Config(qualifiers = SIZE_640)
    fun theGridIsFlushToTheRightMarginAt640() {
        show(TableText.Graphite)
        assertFlushToTheRightMargin("640")
    }

    @Test
    @Config(qualifiers = SIZE_768)
    fun theGridIsFlushToTheRightMarginAt768() {
        show(TableText.Paper)
        assertFlushToTheRightMargin("768")
    }

    @Test
    @Config(qualifiers = SIZE_832)
    fun theGridIsFlushToTheRightMarginAt832() {
        show(TableText.Graphite)
        assertFlushToTheRightMargin("832")
    }

    @Test
    @Config(qualifiers = SIZE_853)
    fun theGridIsFlushToTheRightMarginAt853() {
        show(TableText.Paper)
        assertFlushToTheRightMargin("853")
    }
}
