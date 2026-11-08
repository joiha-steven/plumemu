package com.plumemu.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performSemanticsAction
import com.plumemu.coreui.theme.PlumThemeSettings
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.PlumTheme
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
 * Setup's parts, measured, against the board's own numbers (RP5-O1 markup: padding 14 24 12,
 * top bar 18, gap 10; wide: padding-top 34, title column 260, gap 48; narrow: padding-top 8,
 * stacked, gap 14; points 32 high, content gap 8, Choose folder after 12 more, choices at least
 * 56, Continue after the same 8; buttons 40 high; hint bar 22 at the bottom margin).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertDp(
        what: String,
        expected: Dp,
        actual: Dp,
    ) = assertEquals(what, expected.value, actual.value, TOLERANCE)

    /** The content area ends at 432 - 12 - 22 - 10 = 388: nothing needs scrolling at the reference sizes. */
    private fun assertFitsAboveTheHints(
        what: String,
        item: DpRect,
    ) = assertTrue("$what ends at ${item.bottom}, past 388", item.bottom.value <= CONTENT_BOTTOM + TOLERANCE)

    private fun show(state: SetupUiState) = compose.setContent { PlumTheme(TableText.Default) { SetupAt(state) } }

    @Test
    @Config(qualifiers = WIDE)
    fun wideChooseFolder() {
        show(SetupFakes.chooseFolder)
        val title = bounds(SetupTags.TITLE)
        assertDp("title column left", 24.dp, title.left)
        assertDp("title top: 14 + 23 + 10 + 34", 81.dp, title.top)
        val button = bounds(SetupTags.control(SetupControl.ChooseFolder))
        assertDp("content column: 24 + 260 + 48", 332.dp, button.left)
        // The first two points ("Android's folder picker opens. …" and "Then confirm … a touch of the screen.") wrap to two lines here: 30 more than three single lines.
        assertDp("three points (two of two lines), three gaps of 8, then 12", 243.dp, button.top)
        assertDp("button height: the 38 cap and its collar", BUTTON_WITH_COLLAR, button.bottom - button.top)
        val hints = bounds(SetupTags.HINTS)
        assertDp("hint bar ends at the bottom margin", 432.dp - PlumDimens.footerBelow, hints.bottom)
        assertDp("hint bar height", 22.dp, hints.bottom - hints.top)
    }

    /** Set up later sits beside Choose folder, and the note under them ends above the hint bar by the bar gap. */
    private fun assertChooseFolderRowAndNoteFit() {
        show(SetupFakes.chooseFolder)
        val choose = bounds(SetupTags.control(SetupControl.ChooseFolder))
        val later = bounds(SetupTags.control(SetupControl.SetUpLater))
        assertDp("Set up later top = Choose folder top", choose.top, later.top)
        assertTrue("Set up later starts after Choose folder ends", later.left.value >= choose.right.value)
        val note = bounds(SetupTags.NOTE)
        val hints = bounds(SetupTags.HINTS)
        assertTrue(
            "note ends at ${note.bottom}; the hints start at ${hints.top} and a gap of ${PlumDimens.footerGap} must stay",
            note.bottom.value + PlumDimens.footerGap.value <= hints.top.value + TOLERANCE,
        )
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideSetUpLaterBesideChooseFolderNoteAboveHints() = assertChooseFolderRowAndNoteFit()

    @Test
    @Config(qualifiers = NARROW)
    fun narrowSetUpLaterBesideChooseFolderNoteAboveHints() = assertChooseFolderRowAndNoteFit()

    @Test
    @Config(qualifiers = WIDE)
    fun wideFolderChosen() {
        show(SetupFakes.folderChosen)
        val card = bounds(SetupTags.FOLDER_CARD)
        assertDp("card at the content's top", 81.dp, card.top)
        assertDp("card left", 332.dp, card.left)
        assertDp("card runs to the right margin: 768 - 24", 744.dp, card.right)
        // 56 is the minimum; Jost's two lines (16 sp and 13 sp at 1.445 em) plus 2 x 8 padding make 61.5.
        assertDp("choice height", 61.5.dp, card.bottom - card.top)
        val add = bounds(SetupTags.control(SetupControl.AddFolder))
        assertDp("8 after the card", 150.5.dp, add.top)
        val next = bounds(SetupTags.control(SetupControl.Continue))
        assertDp("Continue: 8 + 4 after Add another folder, so its collar keeps its distance", 224.dp, next.top)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideLibraryReady() {
        show(SetupFakes.ready)
        assertEquals("the four biggest systems give way to the Ready step's rows, as on narrow screens", 0, compose.onAllNodesWithTag(SetupTags.FOUND).fetchSemanticsNodes().size)
        val emulators = bounds(SetupTags.EMULATORS)
        val stats = bounds(SetupTags.control(SetupControl.UsageStatistics))
        assertDp("rows span the content column", 412.dp, stats.right - stats.left)
        assertDp("8 between the rows", 8.dp, stats.top - emulators.bottom)
        val start = bounds(SetupTags.control(SetupControl.StartPlaying))
        assertDp("Start playing: 8 + 4 after the switch row", 12.dp, start.top - stats.bottom)
        assertReadyFirstViewShowsTheStatisticsRowAndStartPlaying(opensUnscrolled = true)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideSecondScreenQuestionPutsItsButtonsBesideTheTitle() {
        show(SetupFakes.secondScreen)
        val title = bounds(SetupTags.TITLE)
        val use = bounds(SetupTags.control(SetupControl.UseSecondScreen))
        val notNow = bounds(SetupTags.control(SetupControl.SkipSecondScreen))
        assertDp("Use it in the content column", 332.dp, use.left)
        assertDp("Use it level with the title", title.top, use.top)
        assertDp("button height: the 38 cap and its collar", BUTTON_WITH_COLLAR, use.bottom - use.top)
        assertDp("the two buttons on one line", use.top, notNow.top)
        assertDp("the setup button gap between them", PlumDimens.setupButtonGap, notNow.left - use.right)
        assertEquals("five dots: folder, look, box art, second screen, library", 5, compose.onAllNodesWithTag(SetupTags.DOT, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowSecondScreenQuestionStacksItsButtonsUnderTheTitle() {
        show(SetupFakes.secondScreen)
        val title = bounds(SetupTags.TITLE)
        val use = bounds(SetupTags.control(SetupControl.UseSecondScreen))
        val notNow = bounds(SetupTags.control(SetupControl.SkipSecondScreen))
        assertDp("Use it at the left margin", 24.dp, use.left)
        assertTrue("the buttons come under the title", use.top > title.bottom)
        assertDp("the two buttons on one line", use.top, notNow.top)
        assertFitsAboveTheHints("Not now", notNow)
    }

    /** One page of the box art step: two rows of one width, one above the other with the setup gap, the first under the title or beside it. */
    private fun assertBoxArtChoicesStacked(state: SetupUiState): List<DpRect> {
        show(state)
        val rows = state.controls.map { bounds(SetupTags.control(it)) }
        assertEquals("two choices", 2, rows.size)
        assertDp("same width", rows[0].right - rows[0].left, rows[1].right - rows[1].left)
        assertDp("same left", rows[0].left, rows[1].left)
        assertDp("the setup gap", PlumDimens.setupContentGap, rows[1].top - rows[0].bottom)
        return rows
    }

    private fun assertWideBoxArtPage(state: SetupUiState) {
        val rows = assertBoxArtChoicesStacked(state)
        assertDp("the first choice level with the title", bounds(SetupTags.TITLE).top, rows[0].top)
        assertFitsAboveTheHints("last choice", rows[1])
    }

    private fun assertNarrowBoxArtPage(state: SetupUiState) {
        val rows = assertBoxArtChoicesStacked(state)
        assertDp("left margin", 24.dp, rows[0].left)
        assertTrue("under the title", rows[0].top > bounds(SetupTags.TITLE).bottom)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideBoxArtQuestionStandsBesideTheTitleAndFitsAboveTheHints() = assertWideBoxArtPage(SetupFakes.boxArtQuestion)

    @Test
    @Config(qualifiers = WIDE)
    fun wideBoxArtSourcesStandBesideTheTitleAndFitAboveTheHints() = assertWideBoxArtPage(SetupFakes.boxArtSources)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowBoxArtQuestionFollowsTheTitleAtTheLeftMargin() = assertNarrowBoxArtPage(SetupFakes.boxArtQuestion)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowBoxArtSourcesFollowTheTitleAtTheLeftMargin() = assertNarrowBoxArtPage(SetupFakes.boxArtSources)

    @Test
    @Config(qualifiers = WIDE)
    fun fourDotsWithoutASecondScreenOrAGamepad() {
        show(SetupFakes.scanning)
        assertEquals("folders, look, box art, library", 4, compose.onAllNodesWithTag(SetupTags.DOT, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowStacksTheTitleOverTheContent() {
        show(SetupFakes.folderChosen)
        val title = bounds(SetupTags.TITLE)
        assertDp("title top: 14 + 23 + 10 + 8", 55.dp, title.top)
        assertDp("title left", 24.dp, title.left)
        val card = bounds(SetupTags.FOLDER_CARD)
        assertDp("card left", 24.dp, card.left)
        assertDp("card width: 576 - 48", 528.dp, card.right - card.left)
        val next = bounds(SetupTags.control(SetupControl.Continue))
        assertFitsAboveTheHints("Continue", next)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowLibraryReadyHidesTheBiggestSystems() {
        show(SetupFakes.ready)
        assertEquals(0, compose.onAllNodesWithTag(SetupTags.FOUND).fetchSemanticsNodes().size)
        assertReadyFirstViewShowsTheStatisticsRowAndStartPlaying(opensUnscrolled = false)
    }

    /**
     * What is on screen when the Ready step first opens, before any scrolling: the statistics switch
     * row and the focused Start playing are fully visible (clipped bounds equal unclipped ones) and end
     * above the hint bar by at least the focus ring's reach: the content area ends at 388 and the hints start
     * 10 below, so a button may end at 398 minus the ring (5.5), i.e. 392.5.
     */
    private fun assertReadyFirstViewShowsTheStatisticsRowAndStartPlaying(opensUnscrolled: Boolean) {
        for (control in listOf(SetupControl.UsageStatistics, SetupControl.StartPlaying)) {
            val node = compose.onNodeWithTag(SetupTags.control(control), useUnmergedTree = true)
            val unclipped = node.getUnclippedBoundsInRoot()
            val clipped = node.getBoundsInRoot()
            assertEquals("$control is cut at the bottom of the scroll area", unclipped.bottom.value, clipped.bottom.value, TOLERANCE)
            assertEquals("$control is cut at the top of the scroll area", unclipped.top.value, clipped.top.value, TOLERANCE)
            assertTrue(
                "$control ends at ${unclipped.bottom}; with its focus ring it must stay above the hints at ${bounds(SetupTags.HINTS).top}",
                unclipped.bottom.value + PlumDimens.focusRingOutset.value <= bounds(SetupTags.HINTS).top.value + TOLERANCE,
            )
        }
        // The focused Start playing's ring is drawn whole: the scroll's clip
        // (its bounds, which reach down to the hint bar's gap) holds it. Wide, the Ready step opens
        // unscrolled, so it is whole from the first frame: a scroll asked for after the first frame
        // showed the ring cut for a moment, and in the reference shots. Narrow stacks the title over
        // the rows, so it scrolls there.
        if (opensUnscrolled) {
            val scrolled = compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertEquals("the Ready step opens without scrolling", 0f, scrolled, TOLERANCE)
        }
        val start = compose.onNodeWithTag(SetupTags.control(SetupControl.StartPlaying), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val scroll = bounds(SetupTags.SCROLL)
        assertTrue(
            "Start playing's ring ends at ${start.bottom.value + PlumDimens.focusRingOutset.value}, past the scroll's clip at ${scroll.bottom}",
            start.bottom.value + PlumDimens.focusRingOutset.value <= scroll.bottom.value + TOLERANCE,
        )
    }

    /**
     * The Retroid Pocket Classic (620 x 540) at the Largest text size (the content then runs long), the Ready step with the
     * "names are still being filled in" line: scrolled to the end, Start playing keeps the bar gap
     * (10) above the hint bar. It used to end 4.5 above it (the scroll's bottom padding was only the ring's reach).
     */
    @Test
    @Config(qualifiers = CLASSIC)
    fun readyStillNamingScrolledToTheEndKeepsTheBarGapAboveTheHints() {
        compose.setContent { PlumTheme(PlumThemeSettings.Default.copy(textSize = com.plumemu.coreui.theme.TextSize.Largest)) { SetupAt(SetupFakes.ready.copy(isScanning = true, isNaming = true)) } }
        assertEquals("the quiet line is shown", 1, compose.onAllNodesWithTag(SetupTags.NAMING_LINE, useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle()
        val start = bounds(SetupTags.control(SetupControl.StartPlaying))
        val hints = bounds(SetupTags.HINTS)
        assertTrue(
            "Start playing ends at ${start.bottom}, ${hints.top - start.bottom} above the hints; the footer gap is ${PlumDimens.footerGap}",
            hints.top.value - start.bottom.value >= PlumDimens.footerGap.value - TOLERANCE,
        )
    }

    private companion object {
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val CLASSIC = "w620dp-h540dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val TOLERANCE = 0.6f
        val BUTTON_WITH_COLLAR = PlumDimens.setupButtonHeight + CollarSize.Control.topPadding + CollarSize.Control.bottomPadding
        const val CONTENT_BOTTOM = 388f
    }
}
