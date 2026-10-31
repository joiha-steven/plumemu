package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.FakeCoverImages
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.HomeScreen
import com.plumemu.home.rememberHomeStripState
import com.plumemu.home.search.rememberSearchState
import com.plumemu.game.rememberGameMenuController
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * The second screen while the top screen shows a system's grid: the grid
 * already shows the cover, so the bottom card leaves it out and gives the description the room.
 * In a list the card keeps its cover.
 */

/** The game under the cursor is marked "cover on the top screen" in a grid only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SecondScreenBesideGridFollowTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private lateinit var feed: SecondScreenFeed
    private val content = FakeHomeContent.withArtwork()

    private fun send(vararg actions: Action) {
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(SecondScreenFeed.SETTLE_MS)
        compose.waitForIdle()
    }

    private fun shownGame(): SecondScreenContent.Game = compose.runOnIdle { feed.content.value as SecondScreenContent.Game }

    private fun showHome() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            feed = remember { SecondScreenFeed(scope).also { it.setFollowing(true) } }
            PlumTheme { HomeScreen(content, router, HomeCallbacks(), secondScreen = feed) }
        }
        compose.waitUntil(WAIT_MS) { compose.runOnIdle { feed.content.value } is SecondScreenContent.System }
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray())
        send(Action.Enter, Action.Move(Direction.Down))
    }

    @Test
    fun aGameInAGridIsMarkedAndTheSameGameInTheListIsNot() {
        showHome()
        val inList = shownGame()
        assertFalse("in the list the card keeps its cover", inList.isCoverOnTopScreen)

        send(Action.ToggleView)
        val inGrid = shownGame()
        assertEquals("the same game", inList.title, inGrid.title)
        assertTrue("in the grid the top screen shows the cover", inGrid.isCoverOnTopScreen)

        send(Action.Menu)
        assertTrue("the game menu over the grid: still on the top screen", shownGame().isCoverOnTopScreen)
    }

    @Test
    fun aListSystemAndSearchOverAGridKeepTheCardsCover() {
        showHome()
        send(Action.ToggleView)
        assertTrue(shownGame().isCoverOnTopScreen)

        send(Action.Search)
        val searching = compose.runOnIdle { feed.content.value }
        assertFalse("search over the grid: no game marked ($searching)", searching is SecondScreenContent.Game && searching.isCoverOnTopScreen)
        send(Action.Back, Action.Back)

        // The next system down is a list (only the Super Nintendo was switched to its grid).
        send(Action.Back, Action.Move(Direction.Down), Action.Enter)
        val inAList = shownGame()
        assertFalse("${inAList.systemName} is a list: the card keeps its cover", inAList.isCoverOnTopScreen)
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theNarrowDetailsStopBesideAGridKeepsTheCardWithoutItsCover() {
        showHome()
        send(Action.ToggleView)
        val title = shownGame().title
        // Right past the grid's last column reaches the details stop, whose column shows the cover too.
        repeat(DETAILS_STOP_MOVES) {
            send(Action.Move(Direction.Right))
            assertTrue("after ${it + 1} moves right: still no cover on the card", shownGame().isCoverOnTopScreen)
        }
        send(Action.Move(Direction.Left))
        assertTrue("and back left: unchanged", shownGame().isCoverOnTopScreen)
        assertTrue("a game of the grid all along", shownGame().title.isNotEmpty() && title.isNotEmpty())
    }

    /** SELECT switches list and grid without rebuilding the game index (one entry per game). */
    @Test
    fun selectNeverRebuildsTheGameIndex() {
        // Counted from Dispatchers.Default, which waitForIdle does not wait for.
        val builds = AtomicInteger()
        var gridIds by mutableStateOf(emptySet<String>())
        compose.setContent {
            val scope = rememberCoroutineScope()
            feed = remember { SecondScreenFeed(scope).also { it.setFollowing(true) } }
            PlumTheme {
                val state = rememberHomeStripState(content, gridIds, isMotionOn = false)
                val search = rememberSearchState()
                val menu = rememberGameMenuController(HomeCallbacks().games)
                FollowHomeOnSecondScreen(feed, content, state, search, menu, gridIds, buildSystems = { builds.incrementAndGet(); GameSystems.of(it) })
            }
        }
        compose.waitUntil(WAIT_MS) { builds.get() == 1 }
        repeat(SWITCHES) {
            compose.runOnIdle { gridIds = if (gridIds.isEmpty()) setOf("snes") else emptySet() }
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(SecondScreenFeed.SETTLE_MS)
        compose.waitForIdle()
        val rebuilt = runCatching { compose.waitUntil(LATE_BUILD_MS) { builds.get() > 1 } }.isSuccess
        assertEquals("one game index for $SWITCHES switches", 1, if (rebuilt) builds.get() else 1)
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val LATE_BUILD_MS = 1_000L
        const val DETAILS_STOP_MOVES = 5
        const val SWITCHES = 4
    }
}

/**
 * The card beside a grid, measured: no cover and nothing decoded for it, the description in up to
 * [SecondScreenDimens.descriptionMaxLinesBesideGrid] lines, every part inside the margins and none
 * over the next, at every text size, on the Thor's bottom screen and on a 4:3 one.
 */
abstract class SecondScreenBesideGridCardBase(
    private val widthDp: Int,
    private val heightDp: Int,
    private val dimens: SecondScreenDimens,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)
    private val images = FakeCoverImages()

    private fun px(value: Float) = value * RuntimeEnvironment.getApplication().resources.displayMetrics.density

    private fun all(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    @Test
    fun noCoverAndALongerDescriptionThatFitsAtEveryTextSize() {
        RuntimeEnvironment.setQualifiers("w${widthDp}dp-h${heightDp}dp-land-xhdpi")
        compose.setContent { PlumTheme(settings) { SecondScreenCard(content, images = images) } }
        val besideGrid = BESIDE_GRID_GAME
        val longest = mutableListOf<Int>()
        for (textSize in TextSize.entries) {
            settings = PlumThemeSettings.Default.copy(textSize = textSize)
            content = besideGrid
            compose.waitForIdle()
            val label = "${widthDp}x$heightDp $textSize"
            assertTrue("$label: no cover", all(SecondScreenTags.COVER).isEmpty())
            val card = compose.onNodeWithTag(SecondScreenTags.CARD).fetchSemanticsNode().boundsInRoot
            val margin = px(dimens.margin.value)
            val order = listOf(SecondScreenTags.TOP, SecondScreenTags.TITLE, SecondScreenTags.FACT_LINE, SecondScreenTags.DESCRIPTION, SecondScreenTags.FILE_NAME, SecondScreenTags.FACTS)
            var above = card.top + margin - 0.5f
            for (tag in order) {
                for (node in all(tag)) {
                    val b = node.boundsInRoot
                    assertTrue("$label: $tag inside the side margins ($b)", b.left >= card.left + margin - 0.5f && b.right <= card.right - margin + 0.5f)
                    assertTrue("$label: $tag starts under what is above it ($b, above ends at $above)", b.top >= above)
                    assertTrue("$label: $tag above the bottom margin ($b)", b.bottom <= card.bottom - margin + 0.5f)
                    above = b.bottom - 0.5f
                    if (tag == SecondScreenTags.DESCRIPTION || tag == SecondScreenTags.TITLE) {
                        val layout = layoutOf(node)
                        assertTrue("$label: $tag lines end inside its box", layout.getLineBottom(layout.lineCount - 1) <= b.height + 1f)
                    }
                }
            }
            val description = all(SecondScreenTags.DESCRIPTION).single()
            val lines = layoutOf(description).lineCount
            assertTrue("$label: at most ${dimens.descriptionMaxLinesBesideGrid} lines ($lines)", lines <= dimens.descriptionMaxLinesBesideGrid)
            longest += lines
        }
        assertTrue("nothing decoded for the cover: ${images.decoded}", images.decoded.isEmpty())
        assertTrue("at Medium the description takes more than the card with a cover gives (${longest})", longest[TextSize.entries.indexOf(TextSize.Medium)] > dimens.descriptionMaxLines)

        // The same game in a list keeps its cover.
        content = besideGrid.copy(isCoverOnTopScreen = false)
        compose.waitForIdle()
        assertEquals("in a list the cover shows", 1, all(SecondScreenTags.COVER).size)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenBesideGridCardThorTest : SecondScreenBesideGridCardBase(538, 468, SecondScreenDimens.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenBesideGridCardCompactTest : SecondScreenBesideGridCardBase(480, 360, SecondScreenDimens.Compact)

/** The Thor's bottom screen (1240x1080) beside a grid, long text, Medium and Largest, Black and Paper. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w538dp-h468dp-land-369dpi")
class SecondScreenBesideGridScreenshotTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)

    @Test
    fun theCardBesideAGrid() {
        compose.setContent { PlumTheme(settings) { SecondScreenCard(content, images = FakeCoverImages()) } }
        val mismatches = mutableListOf<String>()
        for ((look, base) in listOf("black" to PlumThemeSettings.Default, "paper" to PlumThemeSettings.Paper)) {
            for ((size, textSize) in listOf("medium" to TextSize.Medium, "largest" to TextSize.Largest)) {
                // Calm first, so the shot does not depend on the card before it.
                content = SecondScreenContent.Calm
                compose.waitForIdle()
                settings = base.copy(textSize = textSize)
                content = BESIDE_GRID_GAME
                compose.waitForIdle()
                val name = "second_screen_beside_grid_thor_${size}_$look"
                try {
                    compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$name.png")
                } catch (e: AssertionError) {
                    mismatches += "$name: ${e.message}"
                }
            }
        }
        assertEquals("shots match their references", emptyList<String>(), mismatches)
    }
}

/** A long synopsis: more than the card with a cover has room for. */
private val GRID_DESCRIPTION =
    LONG_DESCRIPTION + " Along the way she meets the creatures of Zebes, fights its bosses in their lairs and " +
        "slowly uncovers what became of the Chozo who once lived there, until the last escape before the planet explodes."

/** Long title, every fact, a long file name and its cover: as the feed fills a game in a grid. */
private val BESIDE_GRID_GAME =
    GAME_LONG_TITLE.withDetails(FULL_DETAILS.copy(description = GRID_DESCRIPTION), LANDSCAPE_FRONT)
        .copy(fileName = LONG_FILE_NAME, isCoverOnTopScreen = true)
