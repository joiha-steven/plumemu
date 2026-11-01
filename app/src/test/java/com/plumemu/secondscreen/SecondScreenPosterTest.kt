package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.home.HomeTags
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
import kotlin.math.abs

/**
 * The details card (layout C) of a game with no picture for good: its poster stands where the picture would, in the same slot,
 * with the facts known without ScreenScraper and no description; never over a grid (the top screen shows the cover there), and
 * not for a game whose result is still to be read.
 */
abstract class SecondScreenPosterBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { PlumTheme(PlumThemeSettings.Default) { SecondScreenCard(content) } }
    }

    private fun show(card: SecondScreenContent) {
        content = card
        compose.waitForIdle()
    }

    private val dimens get() = if (size == SecondSize.Roomy) SecondScreenDimens.Roomy else SecondScreenDimens.Compact

    private fun density(): Float = RuntimeEnvironment.getApplication().resources.displayMetrics.density

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun aGameWithNoPictureShowsItsPosterTopLeftInTheCoverSlot() {
        show(POSTER_GAME)
        assertEquals("the details card, not the picking block", 1, count(SecondScreenTags.DETAILS))
        assertEquals("one poster", 1, count(HomeTags.POSTER_COVER))
        compose.onNode(hasTestTag(HomeTags.POSTER_COVER) and hasAnyAncestor(hasTestTag(SecondScreenTags.COVER)), useUnmergedTree = true).assertExists()
        val cover = bounds(SecondScreenTags.COVER)
        val slot = dimens.artSlot.value * density()
        val margin = dimens.margin.value * density()
        assertEquals("left margin", margin, cover.left, 0.5f)
        assertEquals("top margin", margin, cover.top, 0.5f)
        assertEquals("a landscape poster takes the slot's width", slot, cover.width, 0.5f)
        assertTrue("and no more than its height", cover.height <= slot + 0.5f)
        assertEquals("the poster's shape is the system's", POSTER_GAME.coverAspect, cover.width / cover.height, 0.02f)
    }

    @Test
    fun theTextBesideItHoldsOnlyWhatIsKnownWithoutAScreenScraperResult() {
        show(POSTER_GAME)
        assertEquals("a title", 1, count(SecondScreenTags.TITLE))
        assertEquals("no fact line (year, genre)", 0, count(SecondScreenTags.FACT_LINE))
        assertEquals("no description", 0, count(SecondScreenTags.DESCRIPTION))
        assertEquals("the file name stays", 1, count(SecondScreenTags.FILE_NAME))
        val poster = bounds(SecondScreenTags.COVER)
        val title = bounds(SecondScreenTags.TITLE)
        assertTrue("the title is beside the poster, not under it", title.left >= poster.right)
    }

    @Test
    fun aPortraitSystemsPosterIsTallerThanWide() {
        show(POSTER_GAME.copy(coverAspect = 0.7f))
        val cover = bounds(SecondScreenTags.COVER)
        assertEquals(0.7f, cover.width / cover.height, 0.02f)
        assertTrue("a tall poster takes the slot's height", abs(cover.height - dimens.artSlot.value * density()) < 1f)
    }

    @Test
    fun aGameWhoseResultIsStillToBeReadHasNoPoster() {
        show(POSTER_GAME.copy(hasNoPicture = false))
        assertEquals(0, count(HomeTags.POSTER_COVER))
        assertEquals("the picking block stands", 0, count(SecondScreenTags.DETAILS))
    }

    @Test
    fun aPictureThatCannotBeReadTurnsIntoThePosterAsInTheGrid() {
        // The card's picture is a file the test's image loader does not hold: it reports it unreadable and the poster takes over.
        show(POSTER_GAME.copy(hasNoPicture = false, art = com.plumemu.home.ArtPicture("/media/missing/front.webp", 1240, 886, version = 1)))
        compose.waitUntil(5_000) { count(HomeTags.POSTER_COVER) == 1 }
        assertEquals(1, count(HomeTags.POSTER_COVER))
    }

    @Test
    fun overAGridTheTopScreenShowsTheCoverSoTheCardLeavesItOut() {
        show(POSTER_GAME.copy(isCoverOnTopScreen = true))
        assertEquals(0, count(HomeTags.POSTER_COVER))
    }

    @Test
    fun thePosterNamesItsGame() {
        show(POSTER_GAME)
        compose.onNode(hasContentDescription(POSTER_GAME.title) and hasTestTag(SecondScreenTags.COVER), useUnmergedTree = true).assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenPosterRoomyTest : SecondScreenPosterBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenPosterCompactTest : SecondScreenPosterBase(SecondSize.Compact)
