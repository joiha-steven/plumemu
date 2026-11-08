package com.plumemu.secondscreen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.plumemu.home.FakeAppIcons
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.LocalHomeAppIcons
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

/** What the system, Recent and Favorites cards show in each state, and that at both sizes and text sizes every part is whole or gone. */
abstract class SecondScreenEntryCardsBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { PlumTheme(settings) { CompositionLocalProvider(LocalHomeAppIcons provides FakeAppIcons(missing = setOf(APP_WITHOUT_ICON_KEY))) { SecondScreenCard(content) } } }
    }

    private fun show(
        showing: SecondScreenContent,
        with: PlumThemeSettings = PlumThemeSettings.Default,
    ) {
        settings = with
        content = showing
        compose.waitForIdle()
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun has(tag: String) = count(tag) > 0

    private fun textOf(tag: String): String {
        val parts = mutableListOf<String>()
        walk(compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()) { node -> node.config.getOrNull(SemanticsProperties.Text)?.let { parts += it.joinToString("") } }
        return parts.joinToString(" ")
    }

    private fun textsOf(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString("") }

    // System

    @Test
    fun aPlayedSystemShowsEveryPart() {
        show(SYSTEM_WITH_LAST)
        assertEquals("Super Nintendo", textOf(EntryTags.TITLE))
        assertTrue(has(EntryTags.TILE_PLAY))
        assertTrue(has(EntryTags.TILE_PLAYED))
        assertTrue(has(EntryTags.TILE_ACHIEVEMENTS))
        assertTrue(has(EntryTags.STATUS))
        assertTrue(has(EntryTags.FOOT))
        assertTrue("covers: ${count(EntryTags.COVER)}", count(EntryTags.COVER) in 1..6)
        assertEquals("a time under every cover", count(EntryTags.COVER), count(EntryTags.WHEN))
        assertEquals("2 h ago", textsOf(EntryTags.WHEN).first())
        assertEquals("yesterday", textsOf(EntryTags.WHEN)[1])
        val status = textOf(EntryTags.STATUS)
        assertTrue(status, "3 new this week" in status && "Opens in RetroArch" in status && "Snes9x" in status && "4 files not added" in status)
    }

    @Test
    fun aSystemWithRaButNoPointsSaysGamesWithProgress() {
        show(SYSTEM_RA_NO_POINTS)
        assertTrue(textOf(EntryTags.TILE_ACHIEVEMENTS), "5 games with progress" in textOf(EntryTags.TILE_ACHIEVEMENTS))
    }

    @Test
    fun aSystemWithRaPointsSaysMasteredAndPoints() {
        show(SYSTEM_WITH_LAST)
        val tile = textOf(EntryTags.TILE_ACHIEVEMENTS)
        assertTrue(tile, "mastered" in tile && "2,480 points" in tile)
    }

    @Test
    fun aSystemNeverPlayedHasNoCoversAndZeroTiles() {
        show(SYSTEM_NEVER_PLAYED)
        assertFalse(has(EntryTags.COVER))
        assertTrue(has(EntryTags.TILE_PLAY))
        assertTrue(textOf(EntryTags.TILE_PLAY), Regex("0.min").containsMatchIn(textOf(EntryTags.TILE_PLAY)))
        assertFalse(has(EntryTags.TILE_ACHIEVEMENTS))
    }

    @Test
    fun withoutRaTheAchievementsTileAndWithNothingToSayTheStatusLineAreLeftOut() {
        show(SYSTEM_NO_RA)
        assertFalse(has(EntryTags.TILE_ACHIEVEMENTS))
        assertFalse(has(EntryTags.STATUS))
        assertTrue(has(EntryTags.TILE_PLAY) && has(EntryTags.TILE_PLAYED))
    }

    @Test
    fun anEmptySystemShowsItsNameAndZero() {
        show(SYSTEM_EMPTY)
        assertEquals("PlayStation 2", textOf(EntryTags.TITLE))
        assertEquals("0 games", textOf(EntryTags.COUNT))
        assertFalse(has(EntryTags.COVER))
        assertFalse(has(EntryTags.FOOT))
    }

    @Test
    fun aSystemWithNoInfoHasNoFoot() {
        show(SYSTEM_NO_INFO)
        assertFalse(has(EntryTags.FOOT))
        assertTrue(has(EntryTags.TILE_PLAY))
    }

    @Test
    fun aSystemBeforeItsFiguresAreReadIsItsHeader() {
        show(SystemEntry("snes", "Super Nintendo", 248))
        assertEquals("Super Nintendo", textOf(EntryTags.TITLE))
        assertFalse(has(EntryTags.TILE_PLAY))
        assertFalse(has(EntryTags.COVER))
    }

    // Recent

    @Test
    fun recentShowsFiveCoversWithWhenAndSystemAndTheWeek() {
        show(RECENT_FULL)
        assertEquals("Recent", textOf(EntryTags.TITLE))
        assertTrue("covers: ${count(EntryTags.COVER)}", count(EntryTags.COVER) in 1..5)
        assertEquals(count(EntryTags.COVER), count(EntryTags.CAPTION_SYSTEM))
        assertEquals("the short name under a cover", "SNES", textsOf(EntryTags.CAPTION_SYSTEM).first())
        val fallback = RECENT_FULL.copy(systems = RECENT_FULL.systems.mapValues { (_, look) -> look.copy(caption = "") })
        show(fallback)
        assertEquals("no short name: the full one", "Super Nintendo", textsOf(EntryTags.CAPTION_SYSTEM).first())
        show(RECENT_FULL)
        assertTrue(has(EntryTags.WEEK))
        assertEquals(7, (0..6).count { has(EntryTags.weekBar(it)) })
        assertTrue(textOf(EntryTags.WEEK_LAST), "Last week" in textOf(EntryTags.WEEK_LAST))
    }

    @Test
    fun recentWithOneGameShowsOneCover() {
        show(RECENT_ONE)
        assertEquals(1, count(EntryTags.COVER))
        assertFalse("no last week to show", has(EntryTags.WEEK_LAST))
    }

    @Test
    fun recentWithNothingThisWeekShowsEmptyBarsAndLastWeek() {
        show(RECENT_NOTHING_THIS_WEEK)
        assertTrue(has(EntryTags.WEEK))
        assertFalse("no games this week", has(EntryTags.WEEK_GAMES))
        assertTrue(has(EntryTags.WEEK_LAST))
    }

    @Test
    fun anEmptyRecentIsItsHeader() {
        show(RECENT_EMPTY)
        assertEquals("0 games", textOf(EntryTags.COUNT))
        assertFalse(has(EntryTags.COVER))
        assertFalse(has(EntryTags.WEEK))
        assertEquals("Games you play show up here", textOf(EntryTags.EMPTY))
    }

    @Test
    fun aRecentNotReadYetShowsOnlyItsHeader() {
        show(RECENT_EMPTY.copy(figures = null))
        assertFalse(has(EntryTags.EMPTY))
    }

    // Favorites

    @Test
    fun favoritesWithMoreThanFitSayNShownOfM() {
        show(FAVORITES_MANY)
        val rows = (0..2).count { has(EntryTags.favoritesRow(it)) }
        assertTrue("rows: $rows", rows in 1..3)
        val shown = count(EntryTags.COVER)
        assertTrue("covers: $shown", shown in 1 until 30)
        val status = textOf(EntryTags.STATUS)
        assertTrue(status, status.startsWith("$shown shown of 30") && "on 6 systems" in status)
    }

    @Test
    fun favoritesFewerThanARowShowThemAllAndTheTotal() {
        show(FAVORITES_FEW)
        assertEquals(3, count(EntryTags.COVER))
        assertTrue("one or two rows", (0..2).count { has(EntryTags.favoritesRow(it)) } in 1..2)
        assertTrue(textOf(EntryTags.STATUS), "shown of" !in textOf(EntryTags.STATUS) && "on 2 systems" in textOf(EntryTags.STATUS))
    }

    @Test
    fun anEmptyFavoritesIsItsHeader() {
        show(FAVORITES_EMPTY)
        assertEquals("0 games", textOf(EntryTags.COUNT))
        assertFalse(has(EntryTags.COVER))
        assertFalse(has(EntryTags.STATUS))
        assertEquals("Press Y on a game to add it to Favorites", textOf(EntryTags.EMPTY))
    }

    @Test
    fun aFavoritesWithGamesHasNoEmptyLine() {
        show(FAVORITES_FEW)
        assertFalse(has(EntryTags.EMPTY))
    }

    // Apps still renders

    @Test
    fun theAppsCardStillRenders() {
        show(APPS_WITH_ICONS)
        compose.onNodeWithTag(SecondScreenTags.CARD).assertExists()
    }

    // Fit

    private val entries =
        ALL_SAMPLES.filter { it.second is EntryContent } +
            listOf("system_played_vi" to SYSTEM_STACKED_MARKS)

    private fun walk(
        node: SemanticsNode,
        visit: (SemanticsNode) -> Unit,
    ) {
        visit(node)
        node.children.forEach { walk(it, visit) }
    }

    @Test
    fun noPartIsCutAndPartsGoWholeAtEverySizeAndTextSize() {
        for (textSize in listOf(TextSize.Medium, TextSize.Largest)) {
            for ((name, sample) in entries) {
                show(sample, PlumThemeSettings.Default.copy(textSize = textSize))
                val label = "$name $size $textSize"
                val card = compose.onNodeWithTag(SecondScreenTags.CARD).fetchSemanticsNode().boundsInRoot
                val inner = compose.onNodeWithTag(EntryTags.CARD).fetchSemanticsNode().boundsInRoot
                assertTrue("$label: the card is inside the screen", inner.left >= card.left - 0.5f && inner.right <= card.right + 0.5f && inner.bottom <= card.bottom + 0.5f)
                walk(compose.onNodeWithTag(EntryTags.CARD).fetchSemanticsNode()) { node ->
                    if (!node.layoutInfo.isPlaced) return@walk
                    val at: Rect = node.boundsInRoot
                    assertTrue("$label: a part sticks out of the card: ${node.config.getOrNull(SemanticsProperties.Text)} $at in $inner", at.left >= inner.left - 1f && at.right <= inner.right + 1f && at.bottom <= inner.bottom + 1f && at.top >= inner.top - 1f)
                    val text = node.config.getOrNull(SemanticsProperties.Text)
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                    if (text != null && tag != EntryTags.TITLE) {
                        val layouts = mutableListOf<TextLayoutResult>()
                        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                        layouts.forEach {
                            val shown = text.joinToString("")
                            assertFalse("$label: '$shown' ends in an ellipsis", shown.endsWith("…"))
                            // A one-line text is as wide as it wants to be, or it is cut (a wrapped one is judged by its line count).
                            if (it.lineCount == 1) assertTrue("$label: '$shown' is cut", it.multiParagraph.intrinsics.maxIntrinsicWidth <= node.boundsInRoot.width + 1f)
                        }
                    }
                }
            }
        }
    }
}

/** The Thor's bottom screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenEntryCardsRoomyTest : SecondScreenEntryCardsBase(SecondSize.Roomy)

/** A 4:3 second screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenEntryCardsCompactTest : SecondScreenEntryCardsBase(SecondSize.Compact)
