package com.plumemu.setup

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
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
 * Setup's dead ends and uneven spots, measured at 768 x 432
 * and 576 x 432: the failed load offers Set up later, the Ready step draws "B Back" and keeps the bar
 * gap above the hints with its first card fully in view, the folder step's gaps and edges are equal, and
 * the scan step's two count lines share a centre.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupFlowFixesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun show(state: SetupUiState) = compose.setContent { PlumTheme(MediumText.Default) { SetupAt(state) } }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    // A2

    private fun assertFailedOffersSetUpLaterBesideTryAgain() {
        show(SetupFakes.loadFailed)
        val tryAgain = bounds(SetupTags.control(SetupControl.TryAgain))
        val later = bounds(SetupTags.control(SetupControl.SetUpLater))
        assertEquals("Set up later level with Try again", tryAgain.top.value, later.top.value, TOLERANCE)
        assertEquals("the setup button gap between them", PlumDimens.setupButtonGap.value, later.left.value - tryAgain.right.value, TOLERANCE)
        assertTrue("Set up later fits above the hints", later.bottom.value <= bounds(SetupTags.HINTS).top.value)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideFailedLoadOffersSetUpLater() = assertFailedOffersSetUpLaterBesideTryAgain()

    @Test
    @Config(qualifiers = NARROW)
    fun narrowFailedLoadOffersSetUpLater() = assertFailedOffersSetUpLaterBesideTryAgain()

    @Test
    @Config(qualifiers = WIDE)
    fun aTapOnSetUpLaterOnTheFailedLoadSendsItsOwnControlIndex() {
        val actions = mutableListOf<Action>()
        compose.setContent { PlumTheme(MediumText.Default) { SetupLayout(SetupFakes.loadFailed, com.plumemu.settings.ButtonLetters.Default, { actions += it }, { "21:40" }, { null }) } }
        compose.onNodeWithTag(SetupTags.control(SetupControl.SetUpLater), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertTrue("a tap reached the router: $actions", actions.any { it is Action.TapItem && it.index == 1 })
    }

    // A6

    private fun assertReadyDrawsBackHint() {
        show(SetupFakes.ready)
        assertEquals("B Back is drawn on the last step", 1, count(SetupTags.HINT_BACK))
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideReadyDrawsBackHint() = assertReadyDrawsBackHint()

    @Test
    @Config(qualifiers = NARROW)
    fun narrowReadyDrawsBackHint() = assertReadyDrawsBackHint()

    // B4

    private fun assertReadyKeepsTheBarGapAndShowsItsFirstCard(state: SetupUiState = SetupFakes.ready) {
        show(state)
        val start = bounds(SetupTags.control(SetupControl.StartPlaying))
        val hints = bounds(SetupTags.HINTS)
        assertTrue(
            "Start playing ends at ${start.bottom}; the hints start at ${hints.top}, ${hints.top - start.bottom} below; the bar gap is ${PlumDimens.barGap}",
            hints.top.value - start.bottom.value >= PlumDimens.barGap.value - TOLERANCE,
        )
        // The first card (the emulators row) is whole, and the scroll's top padding (the ring's reach) is above it.
        val node = compose.onNodeWithTag(SetupTags.EMULATORS, useUnmergedTree = true)
        val card = node.getUnclippedBoundsInRoot()
        val scroll = bounds(SetupTags.SCROLL)
        assertEquals("the first card is cut at the top", card.top.value, node.getBoundsInRoot().top.value, TOLERANCE)
        assertTrue(
            "the first card starts at ${card.top}, ${card.top - scroll.top} under the scroll's top; its top padding ${PlumDimens.focusRingOutset} is scrolled out",
            card.top.value - scroll.top.value >= PlumDimens.focusRingOutset.value - TOLERANCE,
        )
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideReadyKeepsTheBarGapAndShowsItsFirstCard() = assertReadyKeepsTheBarGapAndShowsItsFirstCard()

    @Test
    @Config(qualifiers = NARROW)
    fun narrowReadyKeepsTheBarGapAndShowsItsFirstCard() = assertReadyKeepsTheBarGapAndShowsItsFirstCard()

    @Test
    @Config(qualifiers = WIDE)
    fun wideReadyWithTheBoxArtLineKeepsTheBarGap() = assertReadyKeepsTheBarGapAndShowsItsFirstCard(SetupFakes.readyWithBoxArt)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowReadyWithTheBoxArtLineKeepsTheBarGap() = assertReadyKeepsTheBarGapAndShowsItsFirstCard(SetupFakes.readyWithBoxArt)

    // B8

    private fun assertFolderGapsAndEdgesEqual() {
        show(SetupFakes.folderChosen.copy(folders = listOf(SetupFakes.romsFolder, SetupFakes.romsFolder.copy(id = 2))))
        val cards = compose.onAllNodesWithTag(SetupTags.FOLDER_CARD, useUnmergedTree = true)
        val first = cards[0].getUnclippedBoundsInRoot()
        val second = cards[1].getUnclippedBoundsInRoot()
        val add = bounds(SetupTags.control(SetupControl.AddFolder))
        val next = bounds(SetupTags.control(SetupControl.Continue))
        val cardGap = second.top.value - first.bottom.value
        assertEquals("card to card is the content gap", PlumDimens.setupContentGap.value, cardGap, TOLERANCE)
        assertEquals("the last card to Continue is the same gap as card to card", cardGap, next.top.value - add.bottom.value, TOLERANCE)
        val note = bounds(SetupTags.NOTE)
        assertEquals("the note starts at the cards' left edge", first.left.value, note.left.value, TOLERANCE)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideFolderStepGapsAndEdgesAreEqual() = assertFolderGapsAndEdgesEqual()

    @Test
    @Config(qualifiers = NARROW)
    fun narrowFolderStepGapsAndEdgesAreEqual() = assertFolderGapsAndEdgesEqual()

    private fun assertScanCountsShareACentre() {
        show(SetupFakes.scanning)
        val games = bounds(SetupTags.GAME_COUNT)
        val systems = bounds(SetupTags.SYSTEM_COUNT)
        assertEquals(
            "games centre ${(games.top.value + games.bottom.value) / 2}, systems centre ${(systems.top.value + systems.bottom.value) / 2}",
            (games.top.value + games.bottom.value) / 2,
            (systems.top.value + systems.bottom.value) / 2,
            TOLERANCE, // text boxes of two sizes round to a quarter of a pixel; the audit measured 1.2 before
        )
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideScanCountsShareACentre() = assertScanCountsShareACentre()

    @Test
    @Config(qualifiers = NARROW)
    fun narrowScanCountsShareACentre() = assertScanCountsShareACentre()

    private companion object {
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val TOLERANCE = 0.3f
    }
}
