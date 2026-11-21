package com.plumemu.textsize

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.FakePlayFacts
import com.plumemu.home.GameDetails
import com.plumemu.home.HomeAt
import com.plumemu.home.HomeTags
import com.plumemu.home.LONG_DETAILS_TITLE
import com.plumemu.home.LibraryLikeContent
import com.plumemu.home.LocalHomePlayFacts
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * When the cover at its floor, the PLAYED / LAST PLAYED row and the title block do not all fit, the line under the title (publisher and year) leaves
 * first, then the title's second line. Measured with a title too long for one line, at every text size, so the rule is the fit, never a font or a size.
 */
abstract class HomeDetailsTitleFitBase {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun texts(node: SemanticsNode): List<SemanticsNode> =
        (if (SemanticsProperties.Text in node.config && node.layoutInfo.isPlaced) listOf(node) else emptyList()) + node.children.flatMap(::texts)

    private fun text(node: SemanticsNode) = node.config[SemanticsProperties.Text].joinToString("").replace(' ', ' ')

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first()
    }

    protected class Shown(val coverDp: Float, val titleLines: Int, val titleEllipsis: Boolean, val hasSubline: Boolean, val subtitleLines: Int, val subtitleEllipsis: Boolean, val rows: List<String>, val problems: List<String>)

    private var fontNow by mutableStateOf(PlumFont.Jost)
    private var sizeNow by mutableStateOf(TextSize.Small)
    private var isSet = false

    protected fun show(
        font: PlumFont,
        size: TextSize,
        title: String,
    ): Shown {
        fontNow = font
        sizeNow = size
        if (!isSet) {
            isSet = true
            val details = GameDetails(year = 1994, publisher = "Nintendo", genre = "Action, Adventure", players = "1", description = "", rating = 17)
            compose.setContent {
                PlumTheme(PlumThemeSettings.Graphite.copy(textSize = sizeNow)) {
                    CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts()) {
                        HomeAt(LibraryLikeContent.of(hasBoxArt = true, metroidDetails = details, metroidTitle = title), LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID))
                    }
                }
            }
        }
        compose.waitForIdle()
        return measured(font, size)
    }

    private fun measured(
        font: PlumFont,
        size: TextSize,
    ): Shown {
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val column = findTagged(root, HomeTags.DETAILS)!!
        val cover = findTagged(root, HomeTags.DETAILS_COVER)!!.boundsInRoot
        val titleNode = findTagged(root, HomeTags.DETAILS_TITLE)!!
        val titleLayout = layoutOf(titleNode)
        val all = texts(column)
        val problems = mutableListOf<String>()
        for (each in all) {
            val box = each.boundsInRoot
            if (box.height + 1f < each.size.height || box.bottom > column.boundsInRoot.bottom + 1f) problems += "'${text(each)}' is cut or outside the column: $box of ${each.size}"
        }
        for ((i, a) in all.withIndex()) {
            for (b in all.drop(i + 1)) {
                val x = a.boundsInRoot
                val y = b.boundsInRoot
                if (x.left < y.right - 0.5f && y.left < x.right - 0.5f && x.top < y.bottom - 0.5f && y.top < x.bottom - 0.5f) problems += "'${text(a)}' overlaps '${text(b)}'"
            }
        }
        // A title given less height than its lines need is clipped, not cut to fewer lines: its node would be shorter than its shown lines.
        val shownHeight = titleLayout.getLineBottom(titleLayout.lineCount - 1)
        val titleShort = shownHeight - titleNode.size.height
        if (titleShort > 1f) problems += "the title is clipped: its node is ${titleNode.size.height} px high for ${titleLayout.lineCount} line(s) of ${shownHeight} px ($titleShort px short, ${titleShort / DENSITY} dp)"
        val facts = findTagged(column, HomeTags.DETAILS_FACTS)!!
        // The subtitle may be left out whole when the room is short (no layout then); EllipsisText cuts the text itself and ends it with "…".
        val subtitleLayout = findTagged(column, HomeTags.DETAILS_TITLE_SUBTITLE)?.let { n -> mutableListOf<TextLayoutResult>().also { n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.firstOrNull() }
        return Shown(
            coverDp = cover.height / DENSITY,
            titleLines = titleLayout.lineCount,
            titleEllipsis = (0 until titleLayout.lineCount).any { titleLayout.isLineEllipsized(it) },
            hasSubline = all.any { text(it).contains("Nintendo") },
            subtitleLines = subtitleLayout?.lineCount ?: 0,
            subtitleEllipsis = subtitleLayout?.let { l -> l.layoutInput.text.text.endsWith("\u2026") || (0 until l.lineCount).any { l.isLineEllipsized(it) } } ?: false,
            rows = texts(facts).map(::text),
            problems = problems.map { "${font.name} ${size.name}: $it" },
        )
    }

    protected fun golden(name: String) = compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$name.png")

    protected fun assertTimeRowWhole(
        shown: Shown,
        label: String,
    ) {
        assertTrue("$label: PLAYED and LAST PLAYED stay: ${shown.rows}", "Played" in shown.rows && "Last played" in shown.rows)
        assertTrue("$label: the time values are whole: ${shown.rows}", shown.rows.any { it.contains("days ago") } && shown.rows.any { it.contains("4 h") })
        assertTrue("$label: nothing cut or overlapping: ${shown.problems}", shown.problems.isEmpty())
    }

    @Test
    fun everySizeKeepsTheCoverTheTimeRowAndAtMostTwoTitleLines() {
        val failures = mutableListOf<String>()
        run {
            for (size in TextSize.entries) {
                val shown = show(PlumFont.Jost, size, LONG_PLAIN_TITLE)
                val label = size.name
                // Requirement changed: under a title of two lines the cover may go to 0.77 of its floor so the facts keep their three rows.
                if (shown.coverDp < PlumDimens.detailsCoverMinWide.value * SQUEEZED_COVER_SHARE - 0.5f) failures += "$label: cover ${shown.coverDp} dp"
                if ("Played" !in shown.rows || "Last played" !in shown.rows) failures += "$label: time row missing ${shown.rows}"
                failures += shown.problems
                if (shown.titleLines !in 1..2 || !shown.titleEllipsis) failures += "$label: title ${shown.titleLines} lines, ellipsis ${shown.titleEllipsis}"
                // The line under the title goes before the title loses its second line.
                if (shown.titleLines == 1 && shown.hasSubline) failures += "$label: the title lost its second line while the line under it stayed"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // A title with a subtitle mark reads as a main title and a subtitle line (TitleSplit.kt): the main one stays on one line and the subtitle takes at
    // most two, cut with an ellipsis, or leaves whole when the room is short; at every size nothing is cut or overlapping and the time row stays.
    @Test
    fun aSplitTitleKeepsItsMainLineAndCutsOnlyItsSubtitle() {
        val failures = mutableListOf<String>()
        var shownAtSmall = false
        for (size in TextSize.entries) {
            val shown = show(PlumFont.Jost, size, LONG_SPLIT_TITLE)
            if (shown.titleLines != 1) failures += "${size.name}: the main title takes ${shown.titleLines} lines"
            if (shown.subtitleLines > 1) failures += "${size.name}: subtitle ${shown.subtitleLines} lines under a wide box"
            if (shown.subtitleLines > 0 && !shown.subtitleEllipsis) failures += "${size.name}: a subtitle too long for its line is not cut with an ellipsis"
            if (size == TextSize.Small && shown.subtitleLines > 0) shownAtSmall = true
            if ("Played" !in shown.rows || "Last played" !in shown.rows) failures += "${size.name}: time row missing ${shown.rows}"
            if (shown.coverDp < PlumDimens.detailsCoverMinWide.value * SQUEEZED_COVER_SHARE - 0.5f) failures += "${size.name}: cover ${shown.coverDp} dp"
            failures += shown.problems
        }
        if (subtitleShowsAtSmall) assertTrue("the subtitle shows at Small", shownAtSmall) else assertTrue("the subtitle leaves whole at Small (no room under the cover's floor)", !shownAtSmall)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** Whether the column has room for the subtitle line at Small (one line under a wide box, so it does on 768 x 432 and the Thor). */
    protected open val subtitleShowsAtSmall = true

    protected companion object {
        /** A split title whose subtitle is too long for two lines at any size. */
        val LONG_SPLIT_TITLE = "$LONG_DETAILS_TITLE and the Hidden Depths of Planet Zebes Revisited Once More"

        /** [LONG_DETAILS_TITLE] without its subtitle mark: one long title that only wraps (the cases of two lines and an ellipsis). */
        val LONG_PLAIN_TITLE = LONG_DETAILS_TITLE.replace(":", "")
        const val DENSITY = 2f
        const val SQUEEZED_COVER_SHARE = 0.77f
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsTitleFit768Test : HomeDetailsTitleFitBase() {
    @Test
    fun largestKeepsTheCoverAndTheTimeRowAndLosesTheLineUnderTheTitleFirst() {
        val shown = show(PlumFont.Jost, TextSize.Largest, LONG_PLAIN_TITLE)
        assertTrue("the cover keeps ${PlumDimens.detailsCoverMinWide} dp: ${shown.coverDp}", shown.coverDp >= PlumDimens.detailsCoverMinWide.value - 0.5f)
        assertTimeRowWhole(shown, "Largest")
        assertTrue("the line under the title left", !shown.hasSubline)
        assertTrue("the title ends in an ellipsis", shown.titleEllipsis)
    }

    @Test
    fun aTitleThatFitsKeepsItsSecondLine() {
        val shown = show(PlumFont.Jost, TextSize.Small, LONG_PLAIN_TITLE)
        // Measured for Jost (128 dp at 768x432): two title lines in Jost's taller line box take the squeezed floor, not the full one, to keep the facts' three rows.
        assertTrue("the cover keeps its squeezed floor: ${shown.coverDp}", shown.coverDp >= PlumDimens.detailsCoverMinWide.value * SQUEEZED_COVER_SHARE - 0.5f)
        assertTimeRowWhole(shown, "Small")
        assertEquals("the title keeps two lines when they fit", 2, shown.titleLines)
        assertTrue("the line under the title stays when everything fits", shown.hasSubline)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w853dp-h480dp-land-xhdpi")
class HomeDetailsTitleFitThorTest : HomeDetailsTitleFitBase()
