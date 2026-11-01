package com.plumemu.secondscreen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Dp
import com.plumemu.battery.BatteryPace
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.graph.AchievementCount
import com.plumemu.home.FakeAppIcons
import com.plumemu.home.FakeCoverImages
import com.plumemu.home.HomeTags
import com.plumemu.home.LocalHomeAppIcons
import com.plumemu.home.appComposeRule
import com.plumemu.home.assertNoTextCut
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
 * The second screen's catch-up with the top screen, measured: the App card's icon, the Apps card's icon grid
 * and the details card's RetroAchievements line, at every text size on both second-screen sizes.
 */
abstract class SecondScreenCatchUpBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)
    private val icons = FakeAppIcons(missing = setOf(NO_ICON_KEY))

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent {
            PlumTheme(settings) { CompositionLocalProvider(LocalHomeAppIcons provides icons) { SecondScreenCard(content, images = FakeCoverImages()) } }
        }
    }

    private val dimens get() = if (size == SecondSize.Roomy) SecondScreenDimens.Roomy else SecondScreenDimens.Compact

    private fun px(dp: Dp) = dp.value * RuntimeEnvironment.getApplication().resources.displayMetrics.density

    private fun show(
        showing: SecondScreenContent,
        textSize: TextSize = TextSize.Medium,
    ) {
        settings = PlumThemeSettings.Default.copy(textSize = textSize)
        content = showing
        compose.waitForIdle()
    }

    private fun all(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun assertInside(
        label: String,
        tags: List<String>,
    ) {
        val card = bounds(SecondScreenTags.CARD)
        val margin = px(dimens.margin)
        for (tag in tags) {
            for (node in all(tag)) {
                val b = node.boundsInRoot
                assertTrue("$label: $tag left ${b.left}", b.left >= card.left + margin - 0.5f)
                assertTrue("$label: $tag top ${b.top}", b.top >= card.top + margin - 0.5f)
                assertTrue("$label: $tag right ${b.right}", b.right <= card.right - margin + 0.5f)
                assertTrue("$label: $tag bottom ${b.bottom}", b.bottom <= card.bottom - margin + 0.5f)
            }
        }
    }

    // ---- the RetroAchievements line

    private val ra = AchievementCount(earned = 12, total = 45)
    private val pace = BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0)

    private val raCases =
        listOf(
            "art" to DETAILS_GAME.copy(art = LANDSCAPE_FRONT, achievements = ra),
            "no art" to DETAILS_GAME.copy(achievements = ra),
            "poster" to POSTER_GAME.copy(achievements = ra),
            "long file and pace" to DETAILS_GAME.copy(art = LANDSCAPE_FRONT, fileName = LONG_FILE_NAME, batteryPace = pace, achievements = ra),
        )

    @Test
    fun theAchievementsLineUsesTheTopScreensWordsAndRestsOnTheBottomMargin() {
        for ((name, game) in raCases) {
            show(game)
            val line = compose.onNodeWithTag(SecondScreenTags.ACHIEVEMENTS, useUnmergedTree = true).fetchSemanticsNode()
            assertEquals("$size $name", "Achievements 12 of 45 · 27%", line.config[SemanticsProperties.Text].joinToString(""))
            val card = bounds(SecondScreenTags.CARD)
            val b = line.boundsInRoot
            assertEquals("$size $name: on the bottom margin", card.bottom - px(dimens.margin), b.bottom, 0.5f)
            assertTrue("$size $name: under the played line", b.top >= bounds(SecondScreenTags.FACTS).bottom)
        }
    }

    @Test
    fun noProgressMeansNoLineAndTheCardIsAsBefore() {
        for ((name, game) in raCases) {
            show(game.copy(achievements = null))
            assertTrue("$size $name: no line", all(SecondScreenTags.ACHIEVEMENTS).isEmpty())
            if (game.batteryPace == null) {
                assertEquals("$size $name: played line on the margin", bounds(SecondScreenTags.CARD).bottom - px(dimens.margin), bounds(SecondScreenTags.FACTS).bottom, 0.5f)
            }
        }
    }

    @Test
    fun noLineUntilThePlayFactsAreRead() {
        show(DETAILS_GAME.copy(achievements = ra, isPlayKnown = false))
        assertTrue(all(SecondScreenTags.ACHIEVEMENTS).isEmpty())
    }

    @Test
    fun everyLineFitsAndNothingOverlapsAtEveryTextSize() {
        for (textSize in TextSize.entries) {
            for ((name, game) in raCases) {
                show(game, textSize)
                val label = "$size $textSize $name"
                assertInside(label, listOf(SecondScreenTags.TOP, SecondScreenTags.TITLE, SecondScreenTags.FACT_LINE, SecondScreenTags.FACTS, SecondScreenTags.ACHIEVEMENTS))
                val line = compose.onNodeWithTag(SecondScreenTags.ACHIEVEMENTS, useUnmergedTree = true).fetchSemanticsNode()
                assertEquals("$label: the line is not cut", 1, assertNoTextCut(line, label))
                // Text parts stack top to bottom without touching (the cover beside the head is not one of them).
                val stack = listOf(SecondScreenTags.TOP, SecondScreenTags.TITLE, SecondScreenTags.FACT_LINE, SecondScreenTags.DESCRIPTION, SecondScreenTags.FILE_NAME, SecondScreenTags.FACTS, SecondScreenTags.ACHIEVEMENTS)
                    .flatMap { tag -> all(tag).map { tag to it.boundsInRoot } }
                    .sortedBy { it.second.top }
                stack.zipWithNext().forEach { (a, b) -> assertTrue("$label: ${a.first} over ${b.first}", a.second.bottom <= b.second.top + 0.5f) }
                assertTrue("$label: the title and the played line are never given up", all(SecondScreenTags.TITLE).isNotEmpty() && all(SecondScreenTags.FACTS).isNotEmpty())
            }
        }
    }

    // ---- the App card

    @Test
    fun theAppCardShowsItsIconInTheArtSlotBesideTheName() {
        for (textSize in TextSize.entries) {
            show(SecondScreenContent.App("RetroArch", "com.retroarch/.Main"), textSize)
            val label = "$size $textSize"
            assertEquals("$label: one icon", 1, all(HomeTags.APP_GAME_ICON).size)
            val slot = bounds(SecondScreenTags.COVER)
            assertEquals("$label: the slot is the art slot", px(dimens.artSlot), slot.width, 0.5f)
            assertTrue("$label: the icon is the app game's size", bounds(HomeTags.APP_GAME_ICON).width <= px(PlumDimens.appGameDetailsIcon) + 0.5f)
            assertInside(label, listOf(SecondScreenTags.TOP, SecondScreenTags.TITLE, SecondScreenTags.COVER))
            assertTrue("$label: the title is beside the icon", bounds(SecondScreenTags.TITLE).left >= slot.right)
        }
    }

    @Test
    fun anAppWithoutAnIconShowsTheLetterStandIn() {
        show(SecondScreenContent.App("No Icon App", NO_ICON_KEY))
        assertEquals(0, all(HomeTags.APP_GAME_ICON).size)
        assertEquals(1, all(HomeTags.APP_ICON_STAND_IN).size)
    }

    @Test
    fun anAppWithNoIconKeyKeepsThePickingBlock() {
        show(SecondScreenContent.App("RetroArch"))
        assertEquals(1, all(SecondScreenTags.BLOCK).size)
        assertEquals(0, all(SecondScreenTags.COVER).size)
    }

    // ---- the Apps card

    private fun apps(count: Int) = SecondScreenContent.Apps(count, (0 until count).map { AppIconRef(if (it == 3) NO_ICON_KEY else "app.$it", "App $it") })

    @Test
    fun theAppsCardFillsTheHeightLeftWithIconsAndNoMore() {
        for (textSize in TextSize.entries) {
            show(apps(MAX_APP_ICONS), textSize)
            val label = "$size $textSize"
            val grid = bounds(SecondScreenTags.APPS_GRID)
            val title = bounds(SecondScreenTags.TITLE)
            val shown = all(SecondScreenTags.APPS_ICON)
            assertInside(label, listOf(SecondScreenTags.TOP, SecondScreenTags.TITLE, SecondScreenTags.APPS_ICON))
            assertTrue("$label: the grid is under the title", grid.top >= title.bottom)
            val icon = px(PlumDimens.appsGridIcon)
            val gap = px(PlumDimens.gridGapPortrait)
            val columns = ((grid.width + gap) / (icon + gap)).toInt()
            val rows = ((grid.height + gap) / (icon + gap)).toInt()
            assertEquals("$label: as many as fit", minOf(MAX_APP_ICONS, columns * rows), shown.size)
            shown.forEach { assertEquals("$label: icon size", icon, it.boundsInRoot.width, 0.5f) }
            if (shown.isNotEmpty()) assertEquals("$label: left on the margin", bounds(SecondScreenTags.CARD).left + px(dimens.margin), shown.minOf { it.boundsInRoot.left }, 0.5f)
        }
    }

    @Test
    fun fewAppsDrawOnlyThemAndAnIconlessOneShowsTheStandIn() {
        show(apps(5))
        assertEquals(5, all(SecondScreenTags.APPS_ICON).size)
        assertEquals(1, all(HomeTags.APP_ICON_STAND_IN).size)
    }

    @Test
    fun noIconsKeepsThePickingBlockWithTheCount() {
        show(SecondScreenContent.Apps(128))
        assertEquals(1, all(SecondScreenTags.BLOCK).size)
        assertEquals(0, all(SecondScreenTags.APPS_GRID).size)
    }
}

private const val NO_ICON_KEY = "app.noicon"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenCatchUpRoomyTest : SecondScreenCatchUpBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenCatchUpCompactTest : SecondScreenCatchUpBase(SecondSize.Compact)
