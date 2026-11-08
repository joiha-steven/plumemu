package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.home.search.SearchIndex
import com.plumemu.home.search.SearchState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Which hints each home bar keeps as the width shrinks, with a 70-character title in focus (the grid bars cut it to PlumDimens.hintTitleMin before any key leaves): the table in
 * docs/design/rules.md (Hint bar drop order) is [EXPECTED]. Sans, Graphite; Small and Larger text.
 * A hint is named by its key cap; the list is left to right as drawn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class HintDropOrderBase(private val width: Int) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val content = FakeHomeContent.withArtwork()
    private val mediumTitle = "Legend of Heroes: Trails in the Sky"
    private val longTitle = "The Legend of Extremely Long Titles: A Very Long Subtitle That Goes On And On"

    private fun withLongTitle(
        content: HomeContent,
        entryId: String,
        row: Int,
        newTitle: String = longTitle,
    ): HomeContent =
        content.withEntries(
            content.entries.map { e ->
                if (e.id == entryId) e.copy(rows = e.rows.mapIndexed { i, r -> if (i == row) r.copy(title = newTitle) else r }) else e
            },
        )

    private fun shown(): List<String> =
        HINTS.mapNotNull { (name, action) ->
            val node = compose.onAllNodesWithTag(HomeTags.hint(action), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull { it.layoutInfo.isPlaced }
            node?.let { name to it.boundsInRoot.left }
        }.sortedBy { it.second }.map { it.first }

    private fun check(
        scene: String,
        board: @Composable () -> Unit,
    ) {
        var size by mutableStateOf(TextSize.Small)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { board() } }
        val actual =
            listOf(TextSize.Small, TextSize.Larger).associate { s ->
                size = s
                compose.waitForIdle()
                "$scene@$width/$s" to shown().joinToString(" ")
            }
        actual.forEach { (key, hints) -> assertEquals("$key: A never leaves", "A", hints.substringBefore(' ')) }
        val wanted = actual.keys.associateWith { EXPECTED[it] }
        assertEquals("hints shown (the table in docs/design/rules.md)", wanted.toString(), actual.toString())
    }

    @Test
    fun systemsColumn() = check("systems") { HomeAt(content, BoardSystemsFocused) }

    @Test
    fun gamesList() = check("list") { HomeAt(withLongTitle(content, "snes", FakeHomeContent.BOARD_REMEMBERED_ROW), BoardGamesFocused) }

    @Test
    fun coverGrid() = check("grid") { HomeAt(withLongTitle(content, "snes", 0), gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes")) }

    @Test
    fun appsGrid() =
        check("apps") {
            val apps = remember { withLongTitle(appsGridLibrary(), HomeContent.APPS_ID, APP_LONG_NAME) }
            CompositionLocalProvider(LocalHomeAppIcons provides remember { FakeAppIcons() }) {
                HomeAt(apps, gridActions(APPS_ENTRY, APP_LONG_NAME), gridIds = setOf(HomeContent.APPS_ID))
            }
        }

    @Test
    fun coverGridMediumTitle() = check("grid-medium") { HomeAt(withLongTitle(content, "snes", 0, mediumTitle), gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes")) }

    @Test
    fun appsGridMediumName() =
        check("apps-medium") {
            val apps = remember { withLongTitle(appsGridLibrary(), HomeContent.APPS_ID, APP_LONG_NAME, mediumTitle) }
            CompositionLocalProvider(LocalHomeAppIcons provides remember { FakeAppIcons() }) {
                HomeAt(apps, gridActions(APPS_ENTRY, APP_LONG_NAME), gridIds = setOf(HomeContent.APPS_ID))
            }
        }

    @Test
    fun coverGridShortTitle() = check("grid-short") { HomeAt(content, gridActions(FakeHomeContent.BOARD_SNES_INDEX, MEGA_MAN_X), gridIds = setOf("snes")) }

    @Test
    fun appsGridShortName() = check("apps-short") { AppsGridBoard(focused = 8) }

    /** Remove from Recent's Undo is offered: X reads "Undo" and is the last of the droppable hints to go (docs/design/rules.md). */
    private fun undoOffered(board: @Composable () -> Unit): @Composable () -> Unit = { CompositionLocalProvider(LocalRecentUndoOffered provides { true }) { board() } }

    @Test
    fun gamesListUndo() = check("list-undo", undoOffered { HomeAt(withLongTitle(content, "snes", FakeHomeContent.BOARD_REMEMBERED_ROW), BoardGamesFocused) })

    @Test
    fun coverGridUndo() = check("grid-undo", undoOffered { HomeAt(withLongTitle(content, "snes", 0), gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes")) })

    @Test
    fun appsGridUndo() =
        check("apps-undo", undoOffered {
            val apps = remember { withLongTitle(appsGridLibrary(), HomeContent.APPS_ID, APP_LONG_NAME) }
            CompositionLocalProvider(LocalHomeAppIcons provides remember { FakeAppIcons() }) {
                HomeAt(apps, gridActions(APPS_ENTRY, APP_LONG_NAME), gridIds = setOf(HomeContent.APPS_ID))
            }
        })

    @Test
    fun appsGridShortNameUndo() = check("apps-short-undo", undoOffered { AppsGridBoard(focused = 8) })

    @Test
    fun detailsStop() =
        check("details") {
            val narrow = PlumTheme.layout.widthClass == WidthClass.Narrow
            HomeAt(withLongTitle(content, "snes", FakeHomeContent.BOARD_REMEMBERED_ROW), if (narrow) BoardGamesFocused + Action.Move(Direction.Right) else BoardGamesFocused)
        }

    @Test
    fun searchResults() =
        check("search-results") {
            val state = remember { searchResultsState(withLongTitle(content, "snes", 0)) }
            HomeAt(content, BoardGamesFocused, search = state)
        }

    @Test
    fun searchTyping() = check("search-typing") { HomeAt(content, BoardGamesFocused, search = remember { typingFor("super", content) }) }

    private fun searchResultsState(c: HomeContent) =
        SearchState().apply {
            open()
            onQueryChange("super")
            showResults("super", SearchIndex.of(c).find("super"))
            toResults()
        }

    private companion object {
        val HINTS =
            listOf(
                "A" to Action.Play, "A" to Action.Enter, "B" to Action.Back, "B" to Action.Erase(), "X" to Action.Search, "Y" to Action.ToggleFavorite,
                "R3" to Action.Sort, "START" to Action.Menu, "START" to Action.OpenSettings,
                "L2R2" to Action.JumpLetterNext, "L1R1" to Action.NextSystem, "SELECT" to Action.ToggleView,
            )
        val EXPECTED: Map<String, String> =
        mapOf(
            "apps-short-undo@576/Larger" to "A B X SELECT",
            "apps-short-undo@576/Small" to "A B X START SELECT",
            "apps-short-undo@640/Larger" to "A B X START SELECT",
            "apps-short-undo@640/Small" to "A B X START SELECT",
            "apps-short-undo@768/Larger" to "A B X START L2R2 SELECT",
            "apps-short-undo@768/Small" to "A B X START L2R2 SELECT",
            "apps-undo@576/Larger" to "A B X SELECT",
            "apps-undo@576/Small" to "A B X SELECT",
            "apps-undo@640/Larger" to "A B X SELECT",
            "apps-undo@640/Small" to "A B X START SELECT",
            "apps-undo@768/Larger" to "A B X START SELECT",
            "apps-undo@768/Small" to "A B X START SELECT",
            "grid-undo@576/Larger" to "A B X SELECT",
            "grid-undo@576/Small" to "A B X SELECT",
            "grid-undo@640/Larger" to "A B X L1R1 SELECT",
            "grid-undo@640/Small" to "A B X L1R1 SELECT",
            "grid-undo@768/Larger" to "A B X Y L1R1 SELECT",
            "grid-undo@768/Small" to "A B X Y L1R1 SELECT",
            "list-undo@576/Larger" to "A Y X START SELECT",
            "list-undo@576/Small" to "A B Y X START SELECT",
            "list-undo@640/Larger" to "A B Y X START SELECT",
            "list-undo@640/Small" to "A B Y X R3 START SELECT",
            "list-undo@768/Larger" to "A B Y X R3 START SELECT",
            "list-undo@768/Small" to "A B Y X R3 START SELECT",
            "apps-short@576/Larger" to "A B START SELECT",
            "apps-short@576/Small" to "A B START SELECT",
            "apps-short@640/Larger" to "A B X START SELECT",
            "apps-short@640/Small" to "A B X START SELECT",
            "apps-short@768/Larger" to "A B X START SELECT",
            "apps-short@768/Small" to "A B X START L2R2 SELECT",
            "apps-medium@576/Larger" to "A B SELECT",
            "apps-medium@576/Small" to "A B START SELECT",
            "apps-medium@640/Larger" to "A B START SELECT",
            "apps-medium@640/Small" to "A B X START SELECT",
            "apps-medium@768/Larger" to "A B X START SELECT",
            "apps-medium@768/Small" to "A B X START SELECT",
            "grid-medium@576/Larger" to "A B L1R1 SELECT",
            "grid-medium@576/Small" to "A B L1R1 SELECT",
            "grid-medium@640/Larger" to "A B L1R1 SELECT",
            "grid-medium@640/Small" to "A B Y L1R1 SELECT",
            "grid-medium@768/Larger" to "A B Y L1R1 SELECT",
            "grid-medium@768/Small" to "A B Y L1R1 SELECT",
            "apps@576/Larger" to "A B SELECT",
            "apps@576/Small" to "A B START SELECT",
            "apps@640/Larger" to "A B START SELECT",
            "apps@640/Small" to "A B X START SELECT",
            "apps@768/Larger" to "A B X START SELECT",
            "apps@768/Small" to "A B X START SELECT",
            "details@576/Larger" to "A B Y START",
            "details@576/Small" to "A B Y START",
            "details@640/Larger" to "A B Y START",
            "details@640/Small" to "A B Y START",
            "details@768/Larger" to "A B Y X R3 START SELECT",
            "details@768/Small" to "A B Y X R3 START SELECT",
            "grid-short@576/Larger" to "A B L1R1 SELECT",
            "grid-short@576/Small" to "A B L1R1 SELECT",
            "grid-short@640/Larger" to "A B L1R1 SELECT",
            "grid-short@640/Small" to "A B Y L1R1 SELECT",
            "grid-short@768/Larger" to "A B Y L1R1 SELECT",
            "grid-short@768/Small" to "A B Y L1R1 SELECT",
            "grid@576/Larger" to "A B L1R1 SELECT",
            "grid@576/Small" to "A B L1R1 SELECT",
            "grid@640/Larger" to "A B L1R1 SELECT",
            "grid@640/Small" to "A B Y L1R1 SELECT",
            "grid@768/Larger" to "A B Y L1R1 SELECT",
            "grid@768/Small" to "A B Y L1R1 SELECT",
            "list@576/Larger" to "A Y X START SELECT",
            "list@576/Small" to "A B Y X START SELECT",
            "list@640/Larger" to "A B Y X START SELECT",
            "list@640/Small" to "A B Y X START SELECT",
            "list@768/Larger" to "A B Y X R3 START SELECT",
            "list@768/Small" to "A B Y X R3 START SELECT",
            "search-results@576/Larger" to "A B X Y START",
            "search-results@576/Small" to "A B X Y START",
            "search-results@640/Larger" to "A B X Y START",
            "search-results@640/Small" to "A B X Y START",
            "search-results@768/Larger" to "A B X Y START",
            "search-results@768/Small" to "A B X Y START",
            "search-typing@576/Larger" to "A B X START Y L1R1",
            "search-typing@576/Small" to "A B X START Y L1R1",
            "search-typing@640/Larger" to "A B X START Y L1R1",
            "search-typing@640/Small" to "A B X START Y L1R1",
            "search-typing@768/Larger" to "A B X START Y L1R1",
            "search-typing@768/Small" to "A B X START Y L1R1",
            "systems@576/Larger" to "A X START SELECT",
            "systems@576/Small" to "A X START SELECT",
            "systems@640/Larger" to "A X START SELECT",
            "systems@640/Small" to "A X START SELECT",
            "systems@768/Larger" to "A X START SELECT",
            "systems@768/Small" to "A X START SELECT",
        )
    }
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HintDropOrder576Test : HintDropOrderBase(576)

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class HintDropOrder640Test : HintDropOrderBase(640)

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HintDropOrder768Test : HintDropOrderBase(768)
