package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.InGameDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The battery part measured: at both corners of the drift (-4, -12 and +4, +12 dp),
 * at all six text sizes in Plum Sans and in every font at Largest, in 24 and 12 hours, with the
 * widest words: nothing nearer than 20 dp to an edge, no text cut, the parts in order without
 * overlapping, the hour labels apart, and the chart at least its minimum height, the key giving
 * way where it must (printed, and pinned: only where measured).
 */
abstract class InGameBatteryFitBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var sample by mutableStateOf(InGameBatterySamples.heavy)
    private var shift by mutableStateOf(DpOffset.Zero)
    private var twelveHour by mutableStateOf(false)

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent {
            PlumTheme(settings) {
                val s = sample
                val format = if (twelveHour) InGameBatterySamples.time12 else InGameBatterySamples.time24
                InGameCard(
                    // The widest clock texts: 12:58 PM and 20:08.
                    time = if (twelveHour) "12:58 PM" else "20:08",
                    battery = s.reading,
                    wifi = s.wifi,
                    shift = { shift },
                    part = { s.part },
                    formatTime = format,
                )
            }
        }
    }

    private val density get() = RuntimeEnvironment.getApplication().resources.displayMetrics.density
    private val dimens get() = if (size == SecondSize.Roomy) InGameDimens.Roomy else InGameDimens.Compact

    /**
     * [tag]'s bounds on screen. boundsInRoot takes in the translation of a layer on a parent (the part's frame, the
     * note's box) but not of one on the node itself, as the clock block has without figures
     * (InGameCardScreenshotTest): that one's shift is added.
     */
    private fun placed(tag: String): Rect? {
        val bounds =
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
                .firstOrNull { it.layoutInfo.isPlaced }?.boundsInRoot ?: return null
        val ownLayer = tag == InGameTags.BLOCK && sample.part?.figures == null
        return if (ownLayer) bounds.translate(shift.x.value * density, shift.y.value * density) else bounds
    }


    private fun checkAll(
        textSize: TextSize,
        font: PlumFont,
    ) {
        settings = PlumThemeSettings(font = font, textSize = textSize)
        for (hours12 in listOf(false, true)) {
            twelveHour = hours12
            for (s in InGameBatterySamples.widest + InGameBatterySamples.mockups) {
                sample = s
                for (corner in listOf(DpOffset((-4).dp, (-12).dp), DpOffset(4.dp, 12.dp))) {
                    shift = corner
                    compose.waitForIdle()
                    check("${size.name} ${font.name} ${textSize.name} ${if (hours12) 12 else 24} ${s.name} at $corner", s)
                }
            }
        }
    }

    private fun check(
        what: String,
        s: InGameBatterySample,
    ) {
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val edge = EDGE.value * density
        val tags =
            listOf(
                InGameTags.BLOCK, InGameBatteryTags.TIME_LEFT, InGameBatteryTags.ENDS_AT, InGameBatteryTags.CHART,
                InGameBatteryTags.LABELS, InGameBatteryTags.KEY, InGameBatteryTags.NOTE,
            )
        for (tag in tags) {
            val box = placed(tag) ?: continue
            val room = minOf(box.left - window.left, window.right - box.right, box.top - window.top, window.bottom - box.bottom)
            assertTrue("$what: $tag $box is ${room / density} dp from an edge", room >= edge - 0.5f)
        }
        assertNoTextCut(what)
        val figures = s.part?.figures
        val block = placed(InGameTags.BLOCK)!!
        if (figures == null) {
            val note = placed(InGameBatteryTags.NOTE)
            if (s.part?.note != null) assertTrue("$what: the note under the clock card", note != null && note.top >= block.bottom)
            return
        }
        val case = what.substringBefore(" at ")
        val head = placed(InGameBatteryTags.TIME_LEFT)!!
        val chart = placed(InGameBatteryTags.CHART)!!
        val labels = placed(InGameBatteryTags.LABELS)
        val ends = placed(InGameBatteryTags.ENDS_AT)
        assertTrue("$what: the time left under the clock card", head.top >= block.bottom)
        if (ends != null) {
            assertTrue("$what: 'empty at' beside or under the time left", ends.left >= head.right || ends.top >= head.bottom)
            assertTrue("$what: the chart under 'empty at'", chart.top >= ends.bottom)
        } else {
            gaveWay += "$case: empty at"
        }
        assertTrue("$what: the chart under the time left", chart.top >= head.bottom)
        val bottom = placed(if (figures.driftNote) InGameBatteryTags.NOTE else InGameBatteryTags.KEY)
        if (bottom == null) gaveWay += "$case: key"
        if (labels == null) {
            gaveWay += "$case: labels"
        } else {
            assertTrue("$what: the labels under the chart", labels.top >= chart.bottom)
            if (bottom != null) assertTrue("$what: the key under the labels", bottom.top >= labels.bottom)
            assertLabelsApart(what)
        }
        val chartDp = chart.height / density
        // Under its minimum only once everything else has given way.
        if (labels != null) assertTrue("$what: chart $chartDp dp under ${dimens.chartMinHeight}", chartDp >= dimens.chartMinHeight.value - 0.5f)
        minChart = minOf(minChart, chartDp)
    }

    /** What gave way, as "<size> <font> <text size> <12|24> <state>: <part>". */
    private val gaveWay = mutableListOf<String>()
    private var minChart = Float.MAX_VALUE

    /**
     * Every text whole on one line (`assertNoTextCut`), but a note, which may wrap to its two lines:
     * all of it shown, nothing past its last line.
     */
    private fun assertNoTextCut(what: String) {
        fun visit(node: SemanticsNode) {
            if (node.layoutInfo.isPlaced && SemanticsProperties.Text in node.config) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                val layout = layouts.single()
                val text = node.config[SemanticsProperties.Text].joinToString()
                if (SemanticsProperties.TestTag in node.config && node.config[SemanticsProperties.TestTag] == InGameBatteryTags.NOTE) {
                    assertTrue("$what: the note '$text' overflows", !layout.hasVisualOverflow && layout.lineCount <= 2)
                } else {
                    val natural = layout.multiParagraph.intrinsics.maxIntrinsicWidth
                    assertTrue("$what: '$text' is cut ($natural > ${node.boundsInRoot.width} px)", natural <= node.boundsInRoot.width + 1f)
                }
            }
            node.children.forEach(::visit)
        }
        visit(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
    }

    /** Where the two middle hour labels gave way. */
    private val labelsThinned = mutableListOf<String>()

    /** The hour labels shown do not touch: left to right, at least 8 dp apart; four, or the first and last. */
    private fun assertLabelsApart(what: String) {
        val node = compose.onAllNodesWithTag(InGameBatteryTags.LABELS, useUnmergedTree = true).fetchSemanticsNodes().first()
        val texts = node.children.filter { it.layoutInfo.isPlaced }.map { it.boundsInRoot }.sortedBy { it.left }
        assertTrue("$what: ${texts.size} labels", texts.size == 4 || texts.size == 2)
        if (texts.size == 2) labelsThinned += what.substringBefore(" at ")
        for ((a, b) in texts.zipWithNext()) assertTrue("$what: labels $a and $b", b.left - a.right >= InGameDimens.labelGap.value * density - 0.5f)
    }

    @Test
    fun everyTextSizeInPlumSans() {
        for (textSize in TextSize.entries) checkAll(textSize, PlumFont.Sans)
        report("Plum Sans")
        assertTrue("the middle labels only give way in 12 hours", labelsThinned.all { " 12 " in it })
        val upToLarge = listOf(TextSize.Small, TextSize.Medium, TextSize.Large).map { " $it " }
        assertEquals("nothing gives way up to Large", emptyList<String>(), gaveWay.filter { case -> upToLarge.any { it in case } })
        assertEquals("the hour labels never give way in Plum Sans", emptyList<String>(), gaveWay.filter { it.endsWith(": labels") })
    }

    @Test
    fun everyFontAtLargest() {
        for (font in PlumFont.entries) checkAll(TextSize.Largest, font)
        report("every font at Largest")
        assertEquals("what gives way at Largest (measured)", expectedAtLargest, gaveWay.map { it.substringAfter(": ") }.toSortedSet())
    }

    private fun report(label: String) {
        println("In-game battery part ${size.name}, $label: smallest chart $minChart dp; gave way: ${gaveWay.distinct()}; middle labels gave way: ${labelsThinned.distinct()}")
    }

    /** The parts that give way somewhere at Largest in some font ("empty at", "key", "labels"), measured. */
    protected abstract val expectedAtLargest: Set<String>

    private companion object {
        /** Nothing nearer than this to an edge at any point of the drift. */
        val EDGE = 20.dp
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameBatteryRoomyFitTest : InGameBatteryFitBase(SecondSize.Roomy) {
    override val expectedAtLargest: Set<String> = sortedSetOf("empty at", "key")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameBatteryCompactFitTest : InGameBatteryFitBase(SecondSize.Compact) {
    override val expectedAtLargest: Set<String> = sortedSetOf("empty at", "key")
}
