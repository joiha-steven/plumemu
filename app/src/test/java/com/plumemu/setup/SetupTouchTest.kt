package com.plumemu.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.ButtonLetters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Touch on Setup: a first tap on a folder card only focuses it (its line then says "Remove this folder") and a
 * tap on the focused card removes it, as A does; the scroll's frame reaches [SlabDimens.listEndRoom] past the
 * window, and a tap in that empty room (above, below, beside the list) takes and does nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SetupTouchTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val source = FakeSetupSource()
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val model = SetupModel(modelScope, source) { }

    @After
    fun tearDown() = modelScope.cancel()

    private fun text(id: Int) = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    @Test
    fun aFirstTapOnAFolderCardFocusesItAndASecondRemovesIt() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val router = remember { InputRouter(scope, onDebug = {}) }
            PlumTheme { SetupScreen(model, router, ButtonLetters.Default, onOpenFolderPicker = {}) }
        }
        compose.runOnIdle { model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE)) }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.setup_folder_remove)).assertDoesNotExist()
        compose.onNodeWithTag(SetupTags.FOLDER_CARD).performClick()
        compose.waitForIdle()
        assertTrue("nothing removed by the first tap", source.removed.isEmpty())
        assertEquals(SetupControl.FolderCard0, model.state.value.focus)
        compose.onNodeWithText(text(R.string.setup_folder_remove)).assertIsDisplayed()
        compose.onNodeWithTag(SetupTags.FOLDER_CARD).performClick()
        compose.waitForIdle()
        assertEquals(listOf(1L), source.removed.toList())
    }

    @Test
    fun aTapInTheEmptyRoomAroundTheListTakesNothing() {
        val actions = mutableListOf<Action>()
        var density = Density(1f)
        compose.setContent {
            density = LocalDensity.current
            PlumTheme {
                Box(Modifier) {
                    SetupLayout(SetupFakes.folderChosen, ButtonLetters.Default, onAction = { actions += it }, clock = { "21:40" }, battery = { "82%" })
                }
            }
        }
        compose.waitForIdle()
        val scroll = compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val first = compose.onNodeWithTag(SetupTags.FOLDER_CARD, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val last = compose.onNodeWithTag(SetupTags.control(SetupControl.Continue), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val room = SlabDimens.listEndRoom
        assertTrue("the frame reaches $room past the first control", scroll.top.value <= first.top.value - room.value + 1f)
        val midX = (first.left + first.right) / 2
        val midY = (first.top + last.bottom) / 2
        // Touch input counts from what is laid out inside the window, not from the frame past it.
        val origin = compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).getBoundsInRoot()
        val spots =
            listOf(
                "above the list" to Offset(with(density) { (midX - origin.left).toPx() }, with(density) { (first.top - origin.top - room / 2).toPx() }),
                "below the list" to Offset(with(density) { (midX - origin.left).toPx() }, with(density) { (last.bottom - origin.top + room / 2).toPx() }),
                "left of the list" to Offset(with(density) { (first.left - origin.left - room / 2).toPx() }, with(density) { (midY - origin.top).toPx() }),
            )
        for ((where, at) in spots) {
            compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).performTouchInput { click(at) }
            compose.waitForIdle()
            assertTrue("a tap $where took ${actions.toList()}", actions.isEmpty())
        }
    }
}
