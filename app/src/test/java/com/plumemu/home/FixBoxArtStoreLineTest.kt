package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.BOX_ART_NONE_KEY
import com.plumemu.game.BoxArtHit
import com.plumemu.game.BoxArtSearchOutcome
import com.plumemu.game.BoxArtStoreOutcome
import com.plumemu.game.FixBoxArtState
import com.plumemu.game.FixBoxArtTags
import com.plumemu.game.GamePanelTags
import com.plumemu.game.boxArtHitKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.coroutines.CoroutineContext

/**
 * Fix box art… at 768x432, with the list long enough to scroll: what a store says is seen under
 * the picked row whatever its depth; the focused row is brought back into view when the text size
 * changes under the open panel; a row scrolled back draws its decoded picture at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class FixBoxArtStoreLineTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val many: List<BoxArtHit> = BoxArtPanelFixtures.hits + (6..12).map { BoxArtHit("$it", "Metroid Game $it", 2000 + it, "Nintendo", "Game Boy", null) }

    private fun picked(downs: Int, store: (FixBoxArtState) -> Unit): FixBoxArtState =
        BoxArtPanelFixtures.results(many).also { state ->
            repeat(downs) { state.onAction(Action.Move(Direction.Down)) }
            store(state)
        }

    private fun assertLineInsideTheList() {
        compose.onNodeWithTag(FixBoxArtTags.LINE, useUnmergedTree = true).assertIsDisplayed()
        val list = compose.onNodeWithTag(FixBoxArtTags.ROWS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val line = compose.onNodeWithTag(FixBoxArtTags.LINE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the line $line is inside the list $list", line.top >= list.top && line.bottom <= list.bottom)
    }

    private fun show(state: FixBoxArtState) = compose.setContent { PlumTheme { BoxArtPanelFixtures.Screen(BoxArtPanelFixtures.panel(state)) } }

    @Test
    fun noBoxFrontOnADeepGameIsSeen() {
        show(picked(9) { it.storing(); it.stored(BoxArtStoreOutcome.NoBoxFront, many[7]) })
        assertLineInsideTheList()
    }

    @Test
    fun storageFailedOnTheFourthGameIsSeen() {
        show(picked(5) { it.storing(); it.stored(BoxArtStoreOutcome.StorageFailed, many[3]) })
        assertLineInsideTheList()
    }

    @Test
    fun waitingOnADeepGameIsSeen() {
        show(picked(10) { it.storing(); it.waitingForPictures() })
        assertLineInsideTheList()
    }

    @Test
    fun theSearchLineStaysAtTheTopOfTheList() {
        val state = FixBoxArtState("zork", "PlayStation").also { it.onAction(Action.Move(Direction.Down)); it.searching("zork") }
        show(state)
        compose.onNodeWithTag(FixBoxArtTags.LINE, useUnmergedTree = true).assertIsDisplayed()
    }

    /** The store starts and fails while the panel is open: the line appears above the picked row, which must stay whole in the list. */
    private fun live(downs: Int, rowTag: String, store: (FixBoxArtState) -> Unit) {
        val state = BoxArtPanelFixtures.results(many)
        show(state)
        compose.waitForIdle()
        repeat(downs) { compose.runOnIdle { state.onAction(Action.Move(Direction.Down)) }; compose.waitForIdle() }
        compose.runOnIdle { store(state) }
        compose.waitForIdle()
        val list = compose.onNodeWithTag(FixBoxArtTags.ROWS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val row = compose.onNodeWithTag(rowTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val line = compose.onNodeWithTag(FixBoxArtTags.LINE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("line $line inside $list", line.top >= list.top && line.bottom <= list.bottom)
        assertTrue("picked row $row inside $list", row.top >= list.top && row.bottom <= list.bottom)
    }

    @Test
    fun waitingOnTheLastGameStartedWhileOpen() =
        live(many.size + 1, FixBoxArtTags.fill(boxArtHitKey(many.last()))) { it.storing(); it.waitingForPictures() }

    @Test
    fun storageFailedOnNoBoxArtStartedWhileOpen() =
        live(many.size + 2, GamePanelTags.row(BOX_ART_NONE_KEY)) { it.storing(); it.stored(BoxArtStoreOutcome.StorageFailed) }

    @Test
    fun noBoxFrontOnTheLastGameStartedWhileOpen() =
        live(many.size + 1, FixBoxArtTags.fill(boxArtHitKey(many.last()))) { it.storing(); it.stored(BoxArtStoreOutcome.NoBoxFront, many.last()) }

    /** After a failure the line stays with the game that was picked while the cursor moves on. */
    @Test
    fun theLineStaysOnThePickedGameWhenTheCursorMoves() {
        val state = BoxArtPanelFixtures.results(many)
        show(state)
        compose.waitForIdle()
        repeat(2) { compose.runOnIdle { state.onAction(Action.Move(Direction.Down)) }; compose.waitForIdle() }
        compose.runOnIdle { state.storing(); state.stored(BoxArtStoreOutcome.StorageFailed) }
        compose.waitForIdle()
        val pickedFill = FixBoxArtTags.fill(boxArtHitKey(many[0]))
        val gap = { compose.onNodeWithTag(pickedFill, useUnmergedTree = true).getUnclippedBoundsInRoot().top - compose.onNodeWithTag(FixBoxArtTags.LINE, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom }
        val before = gap()
        // The list may scroll a little to keep the cursor's neighbours in view; the rows keep their places relative to each other.
        val spacing = { compose.onNodeWithTag(FixBoxArtTags.fill(boxArtHitKey(many[2])), useUnmergedTree = true).getUnclippedBoundsInRoot().top - compose.onNodeWithTag(pickedFill, useUnmergedTree = true).getUnclippedBoundsInRoot().top }
        val spacingBefore = spacing()
        compose.runOnIdle { state.onAction(Action.Move(Direction.Down)) }
        compose.waitForIdle()
        assertTrue("the line is still right above the picked game", kotlin.math.abs((gap() - before).value) < 0.5f)
        assertTrue("the rows below the picked game do not move against it", kotlin.math.abs((spacing() - spacingBefore).value) < 0.5f)
    }

    /** The default text size follows the window width, so it can change under an open panel. */
    @Test
    fun aTextSizeChangeKeepsTheDeepFocusedRowWhole() {
        val state = picked(9) {}
        val key = GamePanelTags.row(boxArtHitKey(many[7]))
        var size by mutableStateOf(TextSize.Small)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { BoxArtPanelFixtures.Screen(BoxArtPanelFixtures.panel(state)) } }
        for (next in listOf(TextSize.Largest, TextSize.Medium, TextSize.Large)) {
            size = next
            compose.waitForIdle()
            val list = compose.onNodeWithTag(FixBoxArtTags.ROWS, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val row = compose.onNodeWithTag(key, useUnmergedTree = true).getUnclippedBoundsInRoot()
            compose.onNodeWithTag(key, useUnmergedTree = true).assertIsDisplayed()
            assertTrue("$next: row $row whole inside list $list", row.top >= list.top && row.bottom <= list.bottom)
        }
    }

    /** After the pictures are decoded once, a row scrolled out and back shows its picture with no wait. */
    @Test
    fun aRowScrolledBackDrawsItsCachedPictureAtOnce() {
        val state = BoxArtPanelFixtures.results(many)
        var reading: CoroutineDispatcher by mutableStateOf(Dispatchers.Unconfined)
        compose.setContent {
            PlumTheme {
                BoxArtPanelFixtures.WithCovers(reading) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = BoxArtPanelFixtures.panel(state)) }
            }
        }
        val first = hasTestTag(FixBoxArtTags.thumbnail(BoxArtPanelFixtures.portrait.id))
        compose.onNode(first, useUnmergedTree = true).assertExists()
        reading = Never
        compose.waitForIdle()
        val list = compose.onNodeWithTag(FixBoxArtTags.ROWS, useUnmergedTree = true)
        list.performScrollToNode(hasTestTag(GamePanelTags.row(BOX_ART_NONE_KEY)))
        compose.waitForIdle()
        list.performScrollToNode(first)
        compose.waitForIdle()
        compose.onNode(first and hasAnyDescendant(hasTestTag(FixBoxArtTags.PICTURE)), useUnmergedTree = true).assertExists()
    }

    /** Never runs what it is given: a read of a file's size started now would not finish. */
    private object Never : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
    }
}
