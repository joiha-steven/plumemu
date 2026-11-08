package com.plumemu.secondscreen

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.FAVORITE_STAR
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val STAR = FAVORITE_STAR

/** Measured, not looked at: where the App and Apps blocks sit, what the game card's title does at every text size, and how a change of card is drawn. */
abstract class SecondScreenCardsFitBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)

    @Before
    fun hostTheCard() {
        // xhdpi like the other screenshot tests: twice the dp. A host is set once, so one class per size.
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { PlumTheme(settings) { SecondScreenCard(content) } }
    }

    private fun show(
        settings: PlumThemeSettings,
        content: SecondScreenContent,
    ) {
        this.settings = settings
        this.content = content
        compose.waitForIdle()
    }

    private val dimens get() = if (size == SecondSize.Roomy) SecondScreenDimens.Roomy else SecondScreenDimens.Compact

    private fun density(): Float = RuntimeEnvironment.getApplication().resources.displayMetrics.density

    @Test
    fun theBlockRestsOnTheBottomMarginAndTheLeftMarginAtEverySizeAndTextSize() {
        run {
            val margin = dimens.margin.value
            for (textSize in listOf(TextSize.Medium, TextSize.Largest)) {
                // A game, an Android app and the entry cards have their own cards and tests (SecondScreenGameCardTest, SecondScreenCatchUpTest, SecondScreenEntryCardsTest).
                for ((name, content) in ALL_SAMPLES.filter { it.second != SecondScreenContent.Calm && it.second !is EntryContent && it.second !is SecondScreenContent.Game && it.second !is SecondScreenContent.App }) {
                    show(PlumThemeSettings.Default.copy(textSize = textSize), content)
                    val card = compose.onNodeWithTag(SecondScreenTags.CARD).fetchSemanticsNode().boundsInRoot
                    // The App and Apps cards are fit-checked in SecondScreenCatchUpTest.
                    // The App cards with an icon fill the margins' box instead of resting a block on it.
                    val isBlock = compose.onAllNodesWithTag(SecondScreenTags.BLOCK).fetchSemanticsNodes().isNotEmpty()
                    val block = compose.onNodeWithTag(if (isBlock) SecondScreenTags.BLOCK else SecondScreenTags.DETAILS).fetchSemanticsNode().boundsInRoot
                    val label = "$name $size $textSize"
                    assertEquals("$label bottom", card.bottom - margin * density(), block.bottom, 0.5f)
                    assertEquals("$label left", card.left + margin * density(), block.left, 0.5f)
                    assertTrue("$label stays inside the right margin", block.right <= card.right - margin * density() + 0.5f)
                    assertTrue("$label stays below the top margin", block.top >= card.top + margin * density() - 0.5f)
                }
            }
        }
    }

    @Test
    fun theCardFillsItsSizeAndIsRoomyOrCompactByTheShorterSide() {
        show(PlumThemeSettings.Default, GAME_WITH_STAR)
        val card = compose.onNodeWithTag(SecondScreenTags.CARD).fetchSemanticsNode().boundsInRoot
        assertEquals(size.widthDp * density(), card.width, 0.5f)
        assertEquals(size.heightDp * density(), card.height, 0.5f)
        assertEquals(dimens, SecondScreenDimens.forShorterSide(minOf(size.widthDp, size.heightDp).dp))
        assertEquals(SecondScreenDimens.Roomy, SecondScreenDimens.forShorterSide(400.dp))
        assertEquals(SecondScreenDimens.Compact, SecondScreenDimens.forShorterSide(399.dp))
    }

    @Test
    fun theStarIsNeverCutAndTheTitleStaysOnItsLineBesideTheSystemAtEveryTextSizeAndFont() {
        for (font in listOf(PlumFont.Jost)) {
            for (textSize in TextSize.entries) {
                show(PlumThemeSettings.Default.copy(textSize = textSize), GAME_LONG_TITLE)
                val label = "$size $font $textSize"
                val node = compose.onNodeWithTag(GameCardTags.TITLE).fetchSemanticsNode()
                val text = node.config[SemanticsProperties.Text].joinToString("")
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                val layout = layouts.single()
                assertTrue("$label: the star is the last character: '$text'", text.endsWith(STAR))
                assertTrue("$label: a cut title ends in an ellipsis before the star: '$text'", text.dropLast(2).endsWith("\u2026"))
                assertEquals("$label: one line", 1, layout.lineCount)
                assertFalse("$label: the text overflows its box", layout.hasVisualOverflow)
                val starRight = node.boundsInRoot.left + layout.getLineRight(0)
                val system = compose.onNodeWithTag(GameCardTags.SYSTEM).fetchSemanticsNode().boundsInRoot
                assertTrue("$label: the star's right edge $starRight is past the system's ${system.left}", starRight <= system.left + 0.5f)
            }
        }
    }

    @Test
    fun aShortFavouriteKeepsItsWholeTitleAtTheLargestTextSize() {
        show(PlumThemeSettings.Default.copy(textSize = TextSize.Largest), GAME_WITH_STAR)
        val node = compose.onNodeWithTag(GameCardTags.TITLE).fetchSemanticsNode()
        assertEquals("Super Metroid $STAR", node.config[SemanticsProperties.Text].joinToString(""))
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertEquals("one line", 1, layouts.single().lineCount)
    }

    /** The brightest red channel of the card (white text on Black) at the moment; 0 when nothing is drawn. */
    private fun brightest(): Float {
        val pixels = compose.onNodeWithTag(SecondScreenTags.CARD).captureToImage().toPixelMap()
        var best = 0f
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) best = maxOf(best, pixels[x, y].red)
        return best
    }

    /**
     * The first card after the empty page is whole at once; another card fades in like the top screen's and is whole
     * within a glide; with animations off every change is whole on its first frame.
     */
    @Test
    fun aFirstCardShowsAtOnceAndAChangeIsWholeWithinAGlide() {
        compose.mainClock.autoAdvance = false
        show(PlumThemeSettings.Default, GAME_NEVER_PLAYED)
        compose.mainClock.advanceTimeByFrame()
        val full = brightest()
        assertTrue("the first content is drawn at once ($full)", full > 0.5f)
        show(PlumThemeSettings.Default, GAME_WITH_STAR)
        compose.mainClock.advanceTimeBy(PlumMotion.GLIDE_MS + 32L)
        assertEquals("the new block whole within a glide", full, brightest(), 0.05f)
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS.toLong())
        assertEquals("and nothing changes afterwards", full, brightest(), 0.05f)
        show(PlumThemeSettings.Default.copy(animationsOn = false), GAME_NEVER_PLAYED)
        compose.mainClock.advanceTimeByFrame()
        assertEquals("with animations off, whole on its first frame", full, brightest(), 0.05f)
    }

    @Test
    fun aShortFavouriteKeepsItsWholeTitleAndAGameWithoutAStarHasNone() {
        show(PlumThemeSettings.Default, GAME_WITH_STAR)
        val withStar = compose.onNodeWithTag(GameCardTags.TITLE).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("")
        assertEquals("Super Metroid $STAR", withStar)
        show(PlumThemeSettings.Default, GAME_NEVER_PLAYED)
        val plain = compose.onNodeWithTag(GameCardTags.TITLE).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("")
        assertEquals("Chrono Trigger", plain)
    }
}

/** The Thor's bottom screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenCardsFitRoomyTest : SecondScreenCardsFitBase(SecondSize.Roomy)

/** A 4:3 second screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenCardsFitCompactTest : SecondScreenCardsFitBase(SecondSize.Compact)
