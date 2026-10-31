package com.plumemu.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.height
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The letter rail by touch: a tap on a letter focuses that letter's first row (the row L1 / R1
 * would land on), a vertical drag follows the finger letter by letter and clamps at the ends, and with the rail
 * hidden the peek behaves as before.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeLetterRailTouchTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var strip: StripState

    private val titles = listOf("1942", "Alpha", "Apple", "Bravo", "Charlie", "Delta", "Delta Two", "Echo", "Zelda")

    private fun systemContent() =
        HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("snes", "Super Nintendo", titles.mapIndexed { i, t -> GameRowModel("g$i", t, "USA") })))

    private fun twoSystems() =
        HomeContent.of(
            emptyList(), emptyList(), emptyList(),
            listOf(
                SystemGames("snes", "Super Nintendo", titles.mapIndexed { i, t -> GameRowModel("g$i", t, "USA") }),
                SystemGames("gb", "Game Boy", listOf("Tetris", "Zelda").mapIndexed { i, t -> GameRowModel("b$i", t, "USA") }),
            ),
        )

    private fun show(
        content: HomeContent,
        edge: LeftEdgeSetting = LeftEdgeSetting.Letters,
        index: Int = FIRST_SYSTEM_INDEX,
    ) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                HomeAt(content.withLeftEdge(edge), listOf(Action.TapItem(Column.Systems, index), Action.Enter, Action.TapItem(Column.Games, 0)), onAction = { strip.dispatch(it) }, onState = { strip = it })
            }
        }
        compose.waitForIdle()
    }

    private fun letterY(c: Char): Float {
        val rail = compose.onNodeWithTag(HomeTags.RAIL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val box = compose.onNodeWithTag(HomeTags.railLetter(c), useUnmergedTree = true).getUnclippedBoundsInRoot()
        return (box.top.value + box.height.value / 2 - rail.top.value) * 2f // xhdpi: 2 px per dp
    }

    private fun railX() = 24f * 2f

    private fun tapLetter(c: Char) {
        val y = letterY(c)
        compose.onNodeWithTag(HomeTags.RAIL, useUnmergedTree = true).performTouchInput { click(Offset(railX(), y)) }
        compose.waitForIdle()
    }

    private fun dragBetween(fromY: Float, toY: Float) {
        compose.onNodeWithTag(HomeTags.RAIL, useUnmergedTree = true).performTouchInput {
            down(Offset(railX(), fromY))
            moveTo(Offset(railX(), (fromY + toY) / 2))
            moveTo(Offset(railX(), toY))
            up()
        }
        compose.waitForIdle()
    }

    private fun focusedRow() = compose.runOnIdle { strip.focus.row }

    @Test
    fun tapOnALetterFocusesItsFirstRow() {
        show(systemContent())
        tapLetter('D')
        assertEquals(titles.indexOf("Delta"), focusedRow())
        tapLetter('A')
        assertEquals(titles.indexOf("Alpha"), focusedRow())
        compose.runOnIdle { assertEquals(Column.Games, strip.focus.column) }
    }

    @Test
    fun tapLandsWhereL1R1WouldLand() {
        val rows = systemContent().entries[FIRST_SYSTEM_INDEX].rows
        assertEquals(letterJumpTarget(rows, 0, forward = true), letterStartRow(rows, 'A'))
        assertEquals(letterJumpTarget(rows, titles.indexOf("Echo"), forward = true), letterStartRow(rows, 'Z'))
    }

    @Test
    fun dragDownEndsOnTheLetterUnderTheFinger() {
        show(systemContent())
        dragBetween(letterY('#'), letterY('D'))
        assertEquals(titles.indexOf("Delta"), focusedRow())
    }

    @Test
    fun dragBeyondTheEndsClampsToTheFirstAndLastLetter() {
        show(systemContent())
        val rail = compose.onNodeWithTag(HomeTags.RAIL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        dragBetween(letterY('#'), (rail.height.value - 1f) * 2f + 100f)
        assertEquals(titles.indexOf("Zelda"), focusedRow())
        dragBetween(letterY('Z'), -200f)
        assertEquals(0, focusedRow())
    }

    @Test
    fun aDragThroughSeveralMovesPerLetterEndsOnTheLastLetter() {
        show(systemContent())
        compose.onNodeWithTag(HomeTags.RAIL, useUnmergedTree = true).performTouchInput {
            down(Offset(railX(), letterY('#')))
            for (c in listOf('#', '#', 'B', 'B', 'C')) {
                moveTo(Offset(railX(), letterY(c)))
            }
            up()
        }
        compose.waitForIdle()
        assertEquals(titles.indexOf("Charlie"), focusedRow())
    }

    @Test
    fun aCutRailMapsTheFingerToTheWindowDrawnAtTouchDown() {
        // 27 letters in a rail of about 12 slots: the window re-centres on every jump, the finger must not chase it.
        val all = listOf("1942") + ('A'..'Z').map { "${it}ar" }
        val rows = all.mapIndexed { i, t -> GameRowModel("g$i", t, "USA") }
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                HomeAt(
                    HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("snes", "Super Nintendo", rows))).withLeftEdge(LeftEdgeSetting.Letters),
                    listOf(Action.TapItem(Column.Systems, FIRST_SYSTEM_INDEX), Action.Enter, Action.TapItem(Column.Games, 0)),
                    onAction = { strip.dispatch(it) },
                    onState = { strip = it },
                )
            }
        }
        compose.waitForIdle()
        fun drawnLetters() = (listOf('#') + ('A'..'Z')).filter { compose.onAllNodesWithTag(HomeTags.railLetter(it), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val shown = drawnLetters()
        assertTrue("the rail is cut (${shown.size} of 27 drawn)", shown.size < 27)
        val middle = shown[shown.size / 2 + 1]
        val top = shown[1]
        val middleY = letterY(middle)
        val rail = compose.onNodeWithTag(HomeTags.RAIL, useUnmergedTree = true)
        // One call per step with a recomposition between, as on a device: the drawn window re-centres after each jump.
        rail.performTouchInput { down(Offset(railX(), letterY(top))) }
        compose.waitForIdle()
        for (dy in listOf(0f, 1f, 0f, -1f, 0f, 1f)) {
            rail.performTouchInput { moveTo(Offset(railX(), middleY + dy)) }
            compose.waitForIdle()
        }
        rail.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("the letter drawn under the finger at touch-down, no drift", all.indexOfFirst { it.first().uppercaseChar() == middle }, focusedRow())
    }

    @Test
    fun aFadingRailDoesNotTakeTouchesFromTheSystemRows() {
        show(twoSystems())
        compose.runOnIdle { strip.dispatch(Action.Back) }
        compose.waitForIdle()
        compose.onNodeWithTag(HomeTags.railLetter('D'), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(HomeTags.system(FIRST_SYSTEM_INDEX + 1), useUnmergedTree = true).performTouchInput { click(Offset(width - 20f, height / 2f)) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("the tapped system is selected", FIRST_SYSTEM_INDEX + 1, strip.focus.systemIndex) }
    }

    @Test
    fun withCountsTheRailIsGoneAndTheFocusStays() {
        show(twoSystems(), LeftEdgeSetting.Counts)
        compose.onNodeWithTag(HomeTags.RAIL, useUnmergedTree = true).assertDoesNotExist()
        // The peek's touches reach the system rows as before: a tap there selects a system, no letter jump happens.
        compose.onNodeWithTag(HomeTags.system(FIRST_SYSTEM_INDEX + 1), useUnmergedTree = true).performTouchInput { click(Offset(width - 20f, height / 2f)) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("the tapped system is selected", FIRST_SYSTEM_INDEX + 1, strip.focus.systemIndex) }
    }

    @Test
    fun appsPinnedSettingsRowIsNotATarget() {
        val labels = listOf("Settings", "Browser", "Calculator", "Spotify", "Sync")
        val apps = labels.mapIndexed { i, t -> GameRowModel("$i", t, "", sortName = LetterKey.foldedLabel(t)) }
        show(HomeContent.of(emptyList(), emptyList(), apps, emptyList()), index = 2)
        tapLetter('S')
        assertEquals("Spotify, not the pinned Settings row", 3, focusedRow())
        val entry = systemContentApps(apps)
        assertEquals(null, letterStartRow(entry.rows, 'Q', entry.railStart))
    }

    private fun systemContentApps(apps: List<GameRowModel>) = HomeContent.of(emptyList(), emptyList(), apps, emptyList()).entries.first { it.kind == EntryKind.Apps }
}
