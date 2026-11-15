package com.plumemu.home

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coredata.backup.SettingsJson
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the 48 dp peek shows while a games list is focused: each system's code, in every sort, where the counts sit
 * (so the column lines up with the names when it opens), whole at Largest. The open column keeps its counts.
 * The old Left edge setting is stored but no longer read.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomePeekCodesBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(
        content: HomeContent,
        size: TextSize = TextSize.Small,
        actions: List<Action> = gamesFocused(FakeHomeContent.BOARD_SNES_INDEX),
    ) = compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { HomeAt(content, actions) } }

    private fun gamesFocused(index: Int) = listOf(Action.TapItem(Column.Systems, index), Action.Enter, Action.TapItem(Column.Games, 0))

    private fun edgeOf(index: Int) = compose.onNode(hasTestTag(SystemRowTags.COUNT) and hasAnyAncestor(hasTestTag(HomeTags.system(index))), useUnmergedTree = true)

    private fun rowBounds(index: Int): DpRect = compose.onNodeWithTag(HomeTags.system(index), useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** The widest codes the platform data has, so a clip would show here first. */
    private fun widestCodes(): HomeContent =
        HomeContent.of(
            emptyList(), emptyList(), emptyList(),
            listOf("WWR", "MXR", "W3X", "G&W", "NGC", "X68", "WSC", "SGX").map { code -> SystemGames(code, "System $code", listOf(GameRowModel("$code-1", "Game", "USA")), code = code) },
        )

    private val peekCodes = listOf("REC", "FAV", "APP", "SNS", "GBA", "PS1", "PSP", "NDS")

    @Test
    fun theDefaultPeekShowsCodesWhereTheCountsStand() {
        show(FakeHomeContent.board())
        compose.waitForIdle()
        peekCodes.forEachIndexed { index, code -> edgeOf(index).assertTextEquals(code) }
        for (index in peekCodes.indices) {
            val row = rowBounds(index)
            val edge = edgeOf(index).getUnclippedBoundsInRoot()
            assertEquals("row $index: the code ends where a count ends, the row padding from the row's end", row.right.value - PlumDimens.systemRowPadding.value, edge.right.value, 0.5f)
        }
    }

    @Test
    fun aListSortedByLastPlayedStillShowsCodes() {
        val games = List(12) { i -> GameRowModel("g$i", "${'A' + i}ar game", "USA", systemAbbr = "SNES", systemId = "snes") }
        val content = HomeContent.of(games, emptyList(), emptyList(), listOf(SystemGames("snes", "Super Nintendo", games, code = "SNS")), sortedByName = false)
        val snes = content.entries.indexOfFirst { it.id == "snes" }
        assertFalse("no letters in this sort", content.entries[snes].hasLetterRail)
        show(content, actions = gamesFocused(snes))
        compose.waitForIdle()
        edgeOf(0).assertTextEquals("REC")
        edgeOf(snes).assertTextEquals("SNS")
    }

    @Test
    fun recentShowsItsCodeWithGamesFocusedThere() {
        show(FakeHomeContent.board(), actions = listOf(Action.TapItem(Column.Systems, 0), Action.Enter, Action.TapItem(Column.Games, 0)))
        compose.waitForIdle()
        edgeOf(0).assertTextEquals("REC")
        edgeOf(FakeHomeContent.BOARD_SNES_INDEX).assertTextEquals("SNS")
    }

    @Test
    fun withTheSystemColumnOpenTheCodesGiveWayToCounts() {
        show(FakeHomeContent.board(), actions = listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)))
        compose.waitForIdle()
        edgeOf(FakeHomeContent.BOARD_SNES_INDEX).assertTextEquals("248")
    }

    @Test
    fun aStoredCountsOrLettersChoiceAndABackupHoldingItStillGiveCodes() {
        for (stored in listOf(LeftEdgeSetting.Counts, LeftEdgeSetting.Letters)) {
            val backup = SettingsJson.encode(PlumSettings.Defaults.copy(appearance = AppearanceSettings(leftEdge = stored))).toString()
            val restored = SettingsJson.decode(backup).settings
            assertEquals("the backup restores the stored value", stored, restored.appearance.leftEdge)
        }
        // Hand-written backup JSON of the old build, as a person's file would hold it.
        val json = SettingsJson.encode(PlumSettings.Defaults)
        json.getJSONObject("appearance").put("leftEdge", "Letters")
        assertEquals(LeftEdgeSetting.Letters, SettingsJson.decode(json.toString()).settings.appearance.leftEdge)
        // The home screen reads none of it: nothing in its content or its strip takes the setting.
        show(FakeHomeContent.board())
        compose.waitForIdle()
        peekCodes.forEachIndexed { index, code -> edgeOf(index).assertTextEquals(code) }
    }

    private fun assertWidestCodesFitThePeek(size: TextSize) {
        show(widestCodes(), size, gamesFocused(FIRST_SYSTEM_INDEX))
        compose.waitForIdle()
        val shown = (FIRST_SYSTEM_INDEX until FIRST_SYSTEM_INDEX + 8).filter { compose.onAllNodes(hasTestTag(HomeTags.system(it)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("some rows are on screen: $shown", shown.size >= 4)
        for (index in shown) {
            val row = rowBounds(index)
            val edge = edgeOf(index).getUnclippedBoundsInRoot()
            // The peek (48 dp, growing with the text) is the row's last stretch: the whole code lies inside it, with the row padding right of it.
            val peekLeft = row.right.value - PlumDimens.peekWidth.value * size.heightScale
            assertTrue("$size row $index: code ${edge.left.value}..${edge.right.value}, row ${row.left.value}..${row.right.value}, peek from $peekLeft", edge.left.value >= peekLeft + 0.5f)
            assertTrue("$size row $index: code ends at ${edge.right.value} within the row", edge.right.value <= row.right.value)
        }
    }

    @Test
    fun theWidestCodesFitThePeekAtMedium() = assertWidestCodesFitThePeek(TextSize.Small)

    @Test
    fun theWidestCodesFitThePeekAtLargest() = assertWidestCodesFitThePeek(TextSize.Largest)
}
