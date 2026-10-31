package com.plumemu.secondscreen

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.FAVORITE_STAR
import com.plumemu.coreui.components.SYSTEM_BADGE_TAG
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.assertNoTextCut
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

/** Measured, not looked at: where the block sits and what the title does at every text size. */
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
                for ((name, content) in ALL_SAMPLES.filter { it.second != SecondScreenContent.Calm }) {
                    show(PlumThemeSettings.Default.copy(textSize = textSize), content)
                    val card = compose.onNodeWithTag(SecondScreenTags.CARD).fetchSemanticsNode().boundsInRoot
                    val block = compose.onNodeWithTag(SecondScreenTags.BLOCK).fetchSemanticsNode().boundsInRoot
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
    fun theStarIsNeverCutAndTheTitleKeepsToItsLinesAtEveryTextSizeAndFont() {
        run {
            val maxLines = dimens.titleMaxLines
            for (font in PlumFont.entries) {
                for (textSize in TextSize.entries) {
                    show(PlumThemeSettings.Default.copy(textSize = textSize, font = font), GAME_LONG_TITLE)
                    val label = "$size $font $textSize"
                    val node = compose.onNodeWithTag(SecondScreenTags.TITLE).fetchSemanticsNode()
                    val text = node.config[SemanticsProperties.Text].joinToString("")
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                    val layout = layouts.single()
                    assertTrue("$label: the star is the last character: '$text'", text.endsWith(STAR))
                    assertTrue("$label: a cut title ends in an ellipsis before the star: '$text'", text.dropLast(2).endsWith("…"))
                    assertTrue("$label: ${layout.lineCount} lines, at most $maxLines", layout.lineCount <= maxLines)
                    assertFalse("$label: the text overflows its box", layout.hasVisualOverflow)
                    val lastLine = layout.lineCount - 1
                    val card = compose.onNodeWithTag(SecondScreenTags.CARD).fetchSemanticsNode().boundsInRoot
                    val starRight = node.boundsInRoot.left + layout.getLineRight(lastLine)
                    assertTrue("$label: the star's right edge $starRight is past the margin", starRight <= card.right - dimens.margin.value * density() + 0.5f)
                }
            }
        }
    }

    private fun factsLayout(): Pair<String, TextLayoutResult> {
        val node = compose.onNodeWithTag(SecondScreenTags.FACTS).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return node.config[SemanticsProperties.Text].joinToString("") to layouts.single()
    }

    /** The space between the end of the last character before the separator and the separator, in px. */
    private fun gapBeforeSeparator(): Float {
        val (text, layout) = factsLayout()
        val dot = text.indexOf('\u00B7')
        assertTrue("a separator in '$text'", dot > 0)
        val lastLetter = text.substring(0, dot).trimEnd().length - 1
        return layout.getBoundingBox(dot).left - layout.getBoundingBox(lastLetter).right
    }

    @Test
    fun aLongLastGameNameIsCutAndTheGapBeforeTheSeparatorIsTheNormalOne() {
        show(PlumThemeSettings.Default, SYSTEM_WITH_LAST)
        val normal = gapBeforeSeparator()
        show(PlumThemeSettings.Default, SYSTEM_LONG_LAST_GAME)
        val (text, layout) = factsLayout()
        assertTrue("the name is cut: '$text'", text.contains("\u2026"))
        assertTrue("what follows the name stays: '$text'", text.endsWith("yesterday"))
        assertEquals("one line", 1, layout.lineCount)
        assertTrue("a real gap, not zero: $normal", normal > 1f)
        assertEquals("gap before the separator after a cut name, in px", normal, gapBeforeSeparator(), 1f)
    }

    @Test
    fun aShortFavouriteKeepsItsWholeTitleAtTheLargestTextSize() {
        show(PlumThemeSettings.Default.copy(textSize = TextSize.Largest), GAME_WITH_STAR)
        val node = compose.onNodeWithTag(SecondScreenTags.TITLE).fetchSemanticsNode()
        assertEquals("Super Metroid $STAR", node.config[SemanticsProperties.Text].joinToString(""))
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertTrue("at most ${dimens.titleMaxLines} lines", layouts.single().lineCount <= dimens.titleMaxLines)
    }

    /** The brightest red channel of the card (white text on Black) at the moment; 0 when nothing is drawn. */
    private fun brightest(): Float {
        val pixels = compose.onNodeWithTag(SecondScreenTags.CARD).captureToImage().toPixelMap()
        var best = 0f
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) best = maxOf(best, pixels[x, y].red)
        return best
    }

    /** No fade on the second screen: a change costs one frame, with animations on or off. */
    @Test
    fun aChangeOfContentShowsTheNewBlockAtOnce() {
        compose.mainClock.autoAdvance = false
        show(PlumThemeSettings.Default, GAME_NEVER_PLAYED)
        compose.mainClock.advanceTimeByFrame()
        val full = brightest()
        assertTrue("the first content is drawn at once ($full)", full > 0.5f)
        show(PlumThemeSettings.Default, GAME_WITH_STAR)
        compose.mainClock.advanceTimeByFrame()
        assertEquals("the new block at full brightness on its first frame", full, brightest(), 0.05f)
        compose.mainClock.advanceTimeBy(PlumMotion.FADE_IN_MS * 2L)
        assertEquals("and nothing changes afterwards", full, brightest(), 0.05f)
        show(PlumThemeSettings.Default.copy(animationsOn = false), GAME_NEVER_PLAYED)
        compose.mainClock.advanceTimeByFrame()
        assertEquals("the same with animations off", full, brightest(), 0.05f)
    }

    @Test
    fun aGameWhosePlayIsNotReadYetHasNoFactsLine() {
        show(PlumThemeSettings.Default, GAME_NEVER_PLAYED.copy(isPlayKnown = false))
        compose.onNodeWithTag(SecondScreenTags.FACTS).assertDoesNotExist()
        show(PlumThemeSettings.Default, GAME_NEVER_PLAYED)
        compose.onNodeWithTag(SecondScreenTags.FACTS).assertExists()
    }

    private val systemSamples =
        listOf(SYSTEM_WITH_LAST, SYSTEM_WITHOUT_LAST, SYSTEM_IN_PRODUCTION, SYSTEM_NO_FACTS, SYSTEM_LONG_NAME, SYSTEM_LONG_LAST_GAME, SYSTEM_RECENT, SYSTEM_ABOUT_SNES, SYSTEM_ABOUT_ARCADE, SYSTEM_LONG_NAME_ABOUT)

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun textLines(tag: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().lineCount
    }

    @Test
    fun theSystemCardsBadgeAndFactsLineAreNeverCutOrWrappedAtEveryTextSizeAndFont() {
        val d = density()
        for (font in PlumFont.entries) {
            for (textSize in TextSize.entries) {
                for (content in systemSamples) {
                    show(PlumThemeSettings.Default.copy(textSize = textSize, font = font), content)
                    val label = "$size $font $textSize ${(content as SecondScreenContent.System).name}"
                    val card = bounds(SecondScreenTags.CARD)
                    val block = bounds(SecondScreenTags.BLOCK)
                    assertTrue("$label: the block stays below the top margin", block.top >= card.top + dimens.margin.value * d - 0.5f)
                    assertEquals("$label: rests on the bottom margin", card.bottom - dimens.margin.value * d, block.bottom, 0.5f)
                    val hasBadge = content.info != null
                    compose.onAllNodesWithTag(SYSTEM_BADGE_TAG).assertCountEquals(if (hasBadge) 1 else 0)
                    if (hasBadge) {
                        val badge = bounds(SYSTEM_BADGE_TAG)
                        assertTrue("$label: badge width ${badge.width}", badge.width >= dimens.badgeMinWidth.value * d - 0.5f)
                        assertTrue("$label: badge height ${badge.height}", badge.height >= dimens.badgeMinHeight.value * d - 0.5f)
                        assertEquals("$label: badge is flush with the left margin", block.left, badge.left, 0.5f)
                        assertEquals("$label: 18 dp from the badge to the top line", SecondScreenDimens.badgeToTopGap.value * d, bounds(SecondScreenTags.TOP).top - badge.bottom, 1.5f)
                        assertTrue("$label: badge is not cut", assertNoTextCut(compose.onNodeWithTag(SYSTEM_BADGE_TAG).fetchSemanticsNode(), label) > 0)
                    }
                    val hasFacts = content.info?.let { it.maker != null || it.year != null || it.manyMakers } == true
                    compose.onAllNodesWithTag(SecondScreenTags.SYSTEM_FACTS).assertCountEquals(if (hasFacts) 1 else 0)
                    if (hasFacts) {
                        val facts = bounds(SecondScreenTags.SYSTEM_FACTS)
                        assertEquals("$label: facts line is one line", 1, textLines(SecondScreenTags.SYSTEM_FACTS))
                        assertTrue("$label: facts line stays inside the right margin", facts.right <= card.right - dimens.margin.value * d + 0.5f)
                        assertNoTextCut(compose.onNodeWithTag(SecondScreenTags.SYSTEM_FACTS).fetchSemanticsNode(), label)
                        assertEquals("$label: 16 dp from the title to the facts line", SecondScreenDimens.titleToFactsGap.value * d, facts.top - bounds(SecondScreenTags.TITLE).bottom, 1.5f)
                        if (content.lastGameTitle != null) {
                            assertTrue("$label: the last-played line is under the facts", bounds(SecondScreenTags.FACTS).top >= facts.bottom - 0.5f)
                            if (content.info?.about == null) {
                                assertEquals("$label: 4 dp from the facts to the last-played line", SecondScreenDimens.systemFactsToLastGap.value * d, bounds(SecondScreenTags.FACTS).top - facts.bottom, 1.5f)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun aboutShown() = compose.onAllNodesWithTag(SecondScreenTags.SYSTEM_ABOUT).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theAboutTextIsUnderTheFactsWithinItsLinesAndNeverCostsTheTitleALine() {
        val d = density()
        var shown = 0
        var dropped = 0
        for (font in PlumFont.entries) {
            for (textSize in TextSize.entries) {
                for (content in listOf(SYSTEM_ABOUT_SNES, SYSTEM_ABOUT_ARCADE, SYSTEM_LONG_NAME_ABOUT, SYSTEM_LONG_NAME_ABOUT.copy(lastGameTitle = null, lastPlayedDaysAgo = null))) {
                    val settings = PlumThemeSettings.Default.copy(textSize = textSize, font = font)
                    val label = "$size $font $textSize ${content.name}"
                    show(settings, content.copy(info = content.info!!.copy(about = null)))
                    val titleLinesWithout = textLines(SecondScreenTags.TITLE)
                    val titleHeightWithout = bounds(SecondScreenTags.TITLE).height
                    show(settings, content)
                    val card = bounds(SecondScreenTags.CARD)
                    val block = bounds(SecondScreenTags.BLOCK)
                    assertEquals("$label: title lines are the same with the about text allowed", titleLinesWithout, textLines(SecondScreenTags.TITLE))
                    assertEquals("$label: title height is the same", titleHeightWithout, bounds(SecondScreenTags.TITLE).height, 0.5f)
                    assertEquals("$label: rests on the bottom margin", card.bottom - dimens.margin.value * d, block.bottom, 0.5f)
                    assertTrue("$label: below the top margin", block.top >= card.top + dimens.margin.value * d - 0.5f)
                    if (!aboutShown()) {
                        dropped++
                        // Dropped whole: the block is the one without the text.
                        continue
                    }
                    shown++
                    val about = bounds(SecondScreenTags.SYSTEM_ABOUT)
                    val facts = bounds(SecondScreenTags.SYSTEM_FACTS)
                    assertTrue("$label: at most ${dimens.aboutMaxLines} lines", textLines(SecondScreenTags.SYSTEM_ABOUT) <= dimens.aboutMaxLines)
                    assertEquals("$label: 4 dp from the facts to the about text", SecondScreenDimens.systemFactsToAboutGap.value * d, about.top - facts.bottom, 1.5f)
                    assertEquals("$label: left edge is the block's", block.left, about.left, 0.5f)
                    assertTrue("$label: inside the right margin", about.right <= card.right - dimens.margin.value * d + 0.5f)
                    assertTrue("$label: block inside the card", block.top >= card.top + dimens.margin.value * d - 0.5f)
                    if (content.lastGameTitle != null) {
                        assertEquals("$label: gap from the about text to the last-played line", SecondScreenDimens.systemAboutToLastGap.value * d, bounds(SecondScreenTags.FACTS).top - about.bottom, 1.5f)
                    }
                    val layouts = mutableListOf<TextLayoutResult>()
                    compose.onNodeWithTag(SecondScreenTags.SYSTEM_ABOUT).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                    // Whole or not shown: never an ellipsis, never more lines than allowed.
                    assertFalse("$label: the about text is cut", layouts.single().hasVisualOverflow)
                    assertFalse("$label: ends in an ellipsis", compose.onNodeWithTag(SecondScreenTags.SYSTEM_ABOUT).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("").endsWith("…"))
                }
            }
        }
        assertTrue("$size: the about text was shown at least once", shown > 0)
        println("about text at $size: shown $shown, dropped $dropped")
    }

    @Test
    fun theFactsLineDropsTheKindThenTheMakerWhenTheyDoNotFit() {
        show(PlumThemeSettings.Default, SYSTEM_WITH_LAST)
        assertEquals("Nintendo · Home console · 1990–2003", factsText())
        show(PlumThemeSettings.Default, SYSTEM_IN_PRODUCTION)
        assertEquals("Nintendo · Home console · 2017–present", factsText())
        show(PlumThemeSettings.Default.copy(textSize = TextSize.Largest), SYSTEM_LONG_NAME)
        assertEquals("1983–1995", factsText())
        show(PlumThemeSettings.Default, SYSTEM_RECENT)
        compose.onAllNodesWithTag(SYSTEM_BADGE_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SecondScreenTags.SYSTEM_FACTS).assertCountEquals(0)
    }

    private fun factsText() = compose.onNodeWithTag(SecondScreenTags.SYSTEM_FACTS).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("")

    @Test
    fun aShortFavouriteKeepsItsWholeTitleAndAGameWithoutAStarHasNone() {
        show(PlumThemeSettings.Default, GAME_WITH_STAR)
        val withStar = compose.onNodeWithTag(SecondScreenTags.TITLE).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("")
        assertEquals("Super Metroid $STAR", withStar)
        show(PlumThemeSettings.Default, GAME_NEVER_PLAYED)
        val plain = compose.onNodeWithTag(SecondScreenTags.TITLE).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("")
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
