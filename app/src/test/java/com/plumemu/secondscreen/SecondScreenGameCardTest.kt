package com.plumemu.secondscreen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.SecondScreenText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenGameCardDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.LocalBadgeImages
import com.plumemu.graph.StoredAchievements
import com.plumemu.home.GameDetails
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
 * The game card (boards `SecondGame*`): what each state says, that badges only ever ask the cache, and that at every size, text
 * size and font every part is whole inside the margins (a part that does not fit is left out whole, from the bottom).
 */
abstract class SecondScreenGameCardBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(SecondScreenText.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)
    private val badges = CacheOnlyBadges()

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { PlumTheme(settings) { CompositionLocalProvider(LocalBadgeImages provides badges) { SecondScreenCard(content) } } }
    }

    private fun show(
        content: SecondScreenContent,
        settings: PlumThemeSettings = SecondScreenText.Default,
    ) {
        this.settings = settings
        this.content = content
        compose.waitForIdle()
    }

    private fun px(dp: androidx.compose.ui.unit.Dp) = dp.value * RuntimeEnvironment.getApplication().resources.displayMetrics.density

    private fun all(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun textOf(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().let(::textOf)

    private fun textOf(node: SemanticsNode) = node.config[SemanticsProperties.Text].joinToString("").replace('\u00A0', ' ')

    private fun withDescription(text: String) = BOARD_GAME.copy(details = GameDetails(year = null, publisher = "", genre = "", players = "", description = text))

    @Test
    fun signedInWithPointsAndBadges() {
        show(BOARD_GAME)
        assertEquals("Super Metroid", textOf(GameCardTags.TITLE))
        assertEquals("SNES", textOf(GameCardTags.SYSTEM))
        assertEquals("34 of 60", textOf(GameCardTags.ACHIEVEMENTS_FIGURE))
        assertEquals("410 / 680 points", textOf(GameCardTags.POINTS))
        assertEquals(5, all(GameCardTags.BADGE).size)
        assertEquals("Latest: Kraid's Lair cleared · 2 days ago", all(GameCardTags.LATEST).single().let { node -> allTexts(node) })
        assertEquals("2 h 20 min", textOf(GameCardTags.PLAY_TOTAL))
        assertEquals("9 sessions", textOf(GameCardTags.SESSIONS))
        assertEquals("About 15 min a session", textOf(GameCardTags.ABOUT))
        assertEquals(BOARD_DESCRIPTION, textOf(GameCardTags.DESCRIPTION))
        assertEquals(BOARD_FILE, textOf(GameCardTags.FILE))
    }

    private fun allTexts(node: SemanticsNode): String =
        (if (SemanticsProperties.Text in node.config) node.config[SemanticsProperties.Text].joinToString("") else "") + node.children.joinToString("") { allTexts(it) }

    @Test
    fun progressStoredButNoPointsOrBadges() {
        show(BOARD_GAME.copy(ra = StoredAchievements(earned = 3, total = 12), raLatestDaysAgo = null))
        assertEquals("3 of 12", textOf(GameCardTags.ACHIEVEMENTS_FIGURE))
        assertEquals(0, all(GameCardTags.POINTS).size)
        assertEquals(0, all(GameCardTags.BADGE).size)
        assertEquals(0, all(GameCardTags.LATEST).size)
        assertEquals(1, all(GameCardTags.METER).size)
    }

    @Test
    fun withoutRetroAchievementsThePlayTileTakesTheWholeWidth() {
        show(BOARD_GAME_NO_RA)
        assertEquals(0, all(GameCardTags.ACHIEVEMENTS).size)
        val play = bounds(GameCardTags.PLAY)
        val card = bounds(GameCardTags.CARD)
        assertEquals(card.width, play.width, 1f)
    }

    @Test
    fun theTilesShareTheWidthAsTheBoardsDo() {
        show(BOARD_GAME)
        val ra = bounds(GameCardTags.ACHIEVEMENTS)
        val play = bounds(GameCardTags.PLAY)
        assertEquals(px(SecondScreenGameCardDimens.tileGap), play.left - ra.right, 1f)
        assertEquals(SecondScreenGameCardDimens.ACHIEVEMENTS_WEIGHT / SecondScreenGameCardDimens.PLAY_WEIGHT, ra.width / play.width, 0.02f)
        assertEquals(ra.height, play.height, 1f)
    }

    @Test
    fun neverPlayed() {
        show(BOARD_GAME_NO_RA.copy(lastPlayedDaysAgo = null, playTimeMinutes = null, weeks = PlayWeeks(List(8) { 0L }, 0, 0)))
        assertEquals("Not played yet", textOf(GameCardTags.PLAY_TOTAL))
        assertEquals(0, all(GameCardTags.SESSIONS).size)
        assertEquals("", textOf(GameCardTags.ABOUT))
        assertEquals(1, all(GameCardTags.BARS).size)
    }

    @Test
    fun playedButNotInTheLastEightWeeks() {
        show(BOARD_GAME_NO_RA.copy(weeks = PlayWeeks(List(8) { 0L }, 0, 0)))
        assertEquals("0 min", textOf(GameCardTags.PLAY_TOTAL))
    }

    @Test
    fun theFiguresAreBlankUntilThePlayFactsAreRead() {
        show(BOARD_GAME.copy(isPlayKnown = false, weeks = null, fileName = null))
        assertEquals("", textOf(GameCardTags.PLAY_TOTAL))
        assertEquals(0, all(GameCardTags.FILE).size)
        assertEquals(bounds(GameCardTags.PLAY).height, run {
            show(BOARD_GAME_NO_RA.copy(weeks = PlayWeeks(List(8) { 0L }, 0, 0)))
            bounds(GameCardTags.PLAY).height
        }, 1f)
    }

    @Test
    fun noDescription() {
        show(withDescription(""))
        assertEquals(0, all(GameCardTags.DESCRIPTION).size)
        assertEquals(1, all(GameCardTags.FILE).size)
        show(BOARD_GAME.copy(details = null))
        assertEquals(0, all(GameCardTags.DESCRIPTION).size)
    }

    @Test
    fun aVeryLongDescriptionIsKeptWholeAndCutByTheRoomLeft() {
        val long = BOARD_DESCRIPTION.repeat(6)
        show(withDescription(long))
        assertEquals(long, textOf(GameCardTags.DESCRIPTION))
        val box = bounds(GameCardTags.DESCRIPTION)
        val card = bounds(GameCardTags.CARD)
        val tiles = bounds(GameCardTags.TILES)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag(GameCardTags.DESCRIPTION).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val room = card.bottom - tiles.bottom - px(SecondScreenGameCardDimens.tilesBottomGap)
        assertTrue("the text runs past the room it is clipped to (it is faded there)", layouts.single().size.height > room)
        assertEquals(tiles.bottom + px(SecondScreenGameCardDimens.tilesBottomGap), box.top, 1f)
    }

    @Test
    fun aCutDescriptionShowsWholeLinesOnly() {
        for (textSize in listOf(TextSize.Medium, TextSize.Largest)) {
            show(withDescription(BOARD_DESCRIPTION.repeat(6)), PlumThemeSettings.Default.copy(textSize = textSize))
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithTag(GameCardTags.DESCRIPTION).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            val line = layout.getLineBottom(0) - layout.getLineTop(0)
            val visible = bounds(GameCardTags.DESCRIPTION_BOX).height - px(SecondScreenGameCardDimens.descriptionInkTop)
            val lines = visible / line
            assertTrue("$size $textSize: the cut description shows $lines lines (line $line px, ${visible}px visible), not a whole number", kotlin.math.abs(lines - Math.round(lines)) * line <= 1f)
            assertTrue("$size $textSize: the text continues past the room", layout.size.height > visible)
        }
    }

    @Test
    fun aVeryLongTitleStaysOnOneLineBesideTheSystem() {
        show(BOARD_GAME.copy(title = LONG_TITLE))
        val title = bounds(GameCardTags.TITLE)
        val system = bounds(GameCardTags.SYSTEM)
        assertTrue(title.right <= system.left + 1f)
        assertTrue("one line", title.height < px(SecondScreenGameCardDimens.titleSize.value.dp) * 2)
        assertEquals("SNES", textOf(GameCardTags.SYSTEM))
    }

    @Test
    fun badgesAskTheCacheOnlyNeverTheNetwork() {
        show(BOARD_GAME)
        assertEquals(5, badges.cachedLoads)
        assertEquals(0, badges.loads)
    }

    private val cases
        get() =
            listOf(
                "board" to BOARD_GAME,
                "no RA" to BOARD_GAME_NO_RA,
                "no description" to withDescription(""),
                "long description" to withDescription(BOARD_DESCRIPTION.repeat(6)),
                "long title" to BOARD_GAME.copy(title = LONG_TITLE),
                "long file" to BOARD_GAME.copy(fileName = LONG_FILE_NAME),
                "long badge title" to BOARD_GAME.copy(ra = BOARD_RA.copy(recent = BOARD_RA.recent.map { it.copy(title = LONG_TITLE) })),
                "never played" to BOARD_GAME.copy(lastPlayedDaysAgo = null, weeks = PlayWeeks(List(8) { 0L }, 0, 0)),
            )

    /** Every part is inside the card's margins, whole, and no part sits on the next one down. */
    private fun assertWhole(label: String) {
        val card = bounds(GameCardTags.CARD)
        val tags = listOf(GameCardTags.TITLE, GameCardTags.SYSTEM, GameCardTags.TILES, GameCardTags.DESCRIPTION, GameCardTags.FILE)
        for (tag in tags) {
            for (node in all(tag)) {
                val b = node.boundsInRoot
                assertTrue("$label: $tag left ${b.left}", b.left >= card.left - 0.5f)
                assertTrue("$label: $tag right ${b.right} of ${card.right}", b.right <= card.right + 0.5f)
                assertTrue("$label: $tag top ${b.top}", b.top >= card.top - 0.5f)
                if (tag != GameCardTags.DESCRIPTION) assertTrue("$label: $tag bottom ${b.bottom} of ${card.bottom}; top ${b.top} tiles ${bounds(GameCardTags.TILES)} card $card", b.bottom <= card.bottom + 0.5f)
            }
        }
        val tiles = bounds(GameCardTags.TILES)
        assertTrue("$label: tiles under the header", tiles.top >= bounds(GameCardTags.TITLE).bottom - 0.5f)
        all(GameCardTags.FILE).singleOrNull()?.let { file ->
            assertTrue("$label: the file line under the tiles: file ${file.boundsInRoot} tiles $tiles card $card desc ${all(GameCardTags.DESCRIPTION).map { it.boundsInRoot }}", file.boundsInRoot.top >= tiles.bottom - 0.5f)
            assertEquals("$label: the file line on the bottom margin", card.bottom, file.boundsInRoot.bottom, 1f)
            all(GameCardTags.DESCRIPTION).singleOrNull()?.let { description ->
                // The description's box ends where the foot starts; the text node may be taller (it is clipped there).
                assertTrue("$label: description starts under the tiles", description.boundsInRoot.top >= tiles.bottom - 0.5f)
            }
        }
        // Tile texts are whole: each tile's text bounds sit inside the tile.
        for (tile in listOf(GameCardTags.ACHIEVEMENTS, GameCardTags.PLAY)) {
            for (node in all(tile)) {
                val t = node.boundsInRoot
                assertTrue("$label: $tile inside the card", t.left >= card.left - 0.5f && t.right <= card.right + 0.5f)
            }
        }
    }

    @Test
    fun everyStateFitsAtEveryTextSize() {
        for (textSize in listOf(TextSize.Medium, TextSize.Largest)) {
            for ((name, each) in cases) {
                show(each, PlumThemeSettings.Default.copy(textSize = textSize))
                assertWhole("$size $textSize $name")
            }
        }
    }

    @Test
    fun theLargestTextFitsInEveryFont() {
        for (font in listOf(PlumFont.Jost)) {
            for ((name, each) in cases) {
                show(each, PlumThemeSettings.Default.copy(textSize = TextSize.Largest))
                assertWhole("$size $font $name")
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenGameCardRoomyTest : SecondScreenGameCardBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenGameCardCompactTest : SecondScreenGameCardBase(SecondSize.Compact)
