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
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.testing.MediumText
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

    private fun show(state: SetupUiState) = compose.setContent { PlumTheme(MediumText.Default) { SetupAt(state) } }

    @Test
    @Config(qualifiers = WIDE)
    fun wideChooseFolder() {
        show(SetupFakes.chooseFolder)
        val title = bounds(SetupTags.TITLE)
        assertDp("title column left", 24.dp, title.left)
        assertDp("title top: 14 + 18 + 10 + 34", 76.dp, title.top)
        val button = bounds(SetupTags.control(SetupControl.ChooseFolder))
        assertDp("content column: 24 + 260 + 48", 332.dp, button.left)
        assertDp("three points of 32, three gaps of 8, then 12", 208.dp, button.top)
        assertDp("button height", 40.dp, button.bottom - button.top)
        val hints = bounds(SetupTags.HINTS)
        assertDp("hint bar ends at the bottom margin", 420.dp, hints.bottom)
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
            "note ends at ${note.bottom}; the hints start at ${hints.top} and a gap of ${PlumDimens.barGap} must stay",
            note.bottom.value + PlumDimens.barGap.value <= hints.top.value + TOLERANCE,
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
        assertDp("card at the content's top", 76.dp, card.top)
        assertDp("card left", 332.dp, card.left)
        assertDp("card runs to the right margin: 768 - 24", 744.dp, card.right)
        assertDp("choice height", 56.dp, card.bottom - card.top)
        val add = bounds(SetupTags.control(SetupControl.AddFolder))
        assertDp("8 after the card", 140.dp, add.top)
        val next = bounds(SetupTags.control(SetupControl.Continue))
        assertDp("Continue: 8 after Add another folder, as between the cards", 204.dp, next.top)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideLibraryReady() {
        show(SetupFakes.ready)
        assertEquals("the four biggest systems give way to the Ready step's rows, as on narrow screens", 0, compose.onAllNodesWithTag(SetupTags.FOUND).fetchSemanticsNodes().size)
        val emulators = bounds(SetupTags.EMULATORS)
        val stats = bounds(SetupTags.control(SetupControl.UsageStatistics))
        val oneHand = bounds(SetupTags.control(SetupControl.PushRightToPlay))
        assertDp("rows span the content column", 412.dp, stats.right - stats.left)
        assertDp("the one-hand row is as wide as the statistics row", 412.dp, oneHand.right - oneHand.left)
        assertDp("8 between the rows", 8.dp, stats.top - emulators.bottom)
        assertDp("8 between the statistics and one-hand rows", 8.dp, oneHand.top - stats.bottom)
        val start = bounds(SetupTags.control(SetupControl.StartPlaying))
        assertDp("Start playing: 8 + 4 after the switch row", 12.dp, start.top - oneHand.bottom)
        assertReadyFirstViewShowsTheOneHandRowAndStartPlaying(opensUnscrolled = true)
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
        assertDp("button height", 40.dp, use.bottom - use.top)
        assertDp("the two buttons on one line", use.top, notNow.top)
        assertDp("the setup button gap between them", PlumDimens.setupButtonGap, notNow.left - use.right)
        assertEquals("three dots: folder, second screen, library", 3, compose.onAllNodesWithTag(SetupTags.DOT, useUnmergedTree = true).fetchSemanticsNodes().size)
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

    /**
     * The box art question's buttons sit where the second screen's do, at the same size and gap; on
     * narrow screens they follow the description, which may take another line ([sameTop] false).
     */
    private fun assertBoxArtButtonsLikeTheSecondScreens(sameTop: Boolean) {
        // One content per test: the step is switched in state.
        var shown by mutableStateOf(SetupFakes.secondScreen)
        compose.setContent { PlumTheme(MediumText.Default) { SetupAt(shown) } }
        val use = bounds(SetupTags.control(SetupControl.UseSecondScreen))
        val notNow = bounds(SetupTags.control(SetupControl.SkipSecondScreen))
        val title = bounds(SetupTags.TITLE)
        shown = SetupFakes.boxArtQuestion
        compose.waitForIdle()
        val useAccount = bounds(SetupTags.control(SetupControl.UseBoxArt))
        val skip = bounds(SetupTags.control(SetupControl.SkipBoxArt))
        val boxArtTitle = bounds(SetupTags.TITLE)
        assertDp("Use my account starts where Use it does", use.left, useAccount.left)
        if (sameTop) assertDp("Use my account at Use it's height", use.top, useAccount.top) else assertTrue("under the title", useAccount.top > boxArtTitle.bottom)
        assertDp("same button height", use.bottom - use.top, useAccount.bottom - useAccount.top)
        assertDp("the title where the second screen's is", title.top, boxArtTitle.top)
        assertDp("the two buttons on one line", useAccount.top, skip.top)
        assertDp("the setup button gap between them", notNow.left - use.right, skip.left - useAccount.right)
        assertFitsAboveTheHints("Not now", skip)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideBoxArtQuestionLinesItsButtonsUpLikeTheSecondScreens() {
        assertBoxArtButtonsLikeTheSecondScreens(sameTop = true)
        assertDp("Use my account level with the title", bounds(SetupTags.TITLE).top, bounds(SetupTags.control(SetupControl.UseBoxArt)).top)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowBoxArtQuestionLinesItsButtonsUpLikeTheSecondScreens() {
        assertBoxArtButtonsLikeTheSecondScreens(sameTop = false)
        assertDp("Use my account at the left margin", 24.dp, bounds(SetupTags.control(SetupControl.UseBoxArt)).left)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun twoDotsWithoutASecondScreen() {
        show(SetupFakes.scanning)
        assertEquals(2, compose.onAllNodesWithTag(SetupTags.DOT, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowStacksTheTitleOverTheContent() {
        show(SetupFakes.folderChosen)
        val title = bounds(SetupTags.TITLE)
        assertDp("title top: 14 + 18 + 10 + 8", 50.dp, title.top)
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
        assertReadyFirstViewShowsTheOneHandRowAndStartPlaying(opensUnscrolled = false)
    }

    /**
     * What is on screen when the Ready step first opens, before any scrolling: the one-hand switch
     * row and the focused Start playing are fully visible (clipped bounds equal unclipped ones) and end
     * above the hint bar by at least the focus ring's reach: the content area ends at 388 and the hints start
     * 10 below, so a button may end at 398 minus the ring (5.5), i.e. 392.5.
     */
    private fun assertReadyFirstViewShowsTheOneHandRowAndStartPlaying(opensUnscrolled: Boolean) {
        for (control in listOf(SetupControl.PushRightToPlay, SetupControl.StartPlaying)) {
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
            "Start playing ends at ${start.bottom}, ${hints.top - start.bottom} above the hints; the bar gap is ${PlumDimens.barGap}",
            hints.top.value - start.bottom.value >= PlumDimens.barGap.value - TOLERANCE,
        )
    }

    private companion object {
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val CLASSIC = "w620dp-h540dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val TOLERANCE = 0.6f
        const val CONTENT_BOTTOM = 388f
    }
}
