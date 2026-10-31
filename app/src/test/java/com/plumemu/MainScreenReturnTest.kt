package com.plumemu

import android.view.KeyEvent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.home.pressButton
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.FIRST_SYSTEM_INDEX
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.GamesPagesInfo
import com.plumemu.home.HomeTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.SettingsHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** START opens Settings and B leaves it: the main screen comes back as it was (same column, system, game and scroll). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class MainScreenReturnTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private val fifthSystem = FIRST_SYSTEM_INDEX + 4 // Nintendo DS

    private fun show() {
        val content = FakeHomeContent.large()
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            val host = remember { SettingsHost("test", update = {}, resetAllSettings = {}, resetLibrary = {}) }
            MainScreen(PlumSettings.Defaults, HomeState.Ready(content), router, HomeCallbacks(), onRetry = {}, settingsHost = host)
        }
    }

    private fun press(vararg actions: Action) {
        actions.forEach {
            compose.runOnIdle { router.send(it) }
            compose.waitForIdle()
        }
    }

    /** The index of the system whose games page is shown, which follows the strip's focus. */
    private fun shownSystem(): Int =
        compose.onNode(SemanticsMatcher.keyIsDefined(GamesPagesInfo), useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(GamesPagesInfo)!!.pages.firstVisibleItemIndex

    private fun leaveAndReturnThrough(open: Action) {
        press(open)
        compose.onNodeWithTag(HomeTags.SYSTEMS).assertDoesNotExist()
        press(Action.Back)
        compose.onNodeWithTag(HomeTags.SYSTEMS).assertIsDisplayed()
    }

    @Test
    fun theFocusedSystemStaysFocusedAfterSettings() {
        show()
        press(Action.TapItem(Column.Systems, fifthSystem))
        assertEquals(fifthSystem, shownSystem())

        leaveAndReturnThrough(Action.OpenSettings)

        assertEquals("the same system, not Recent", fifthSystem, shownSystem())

        leaveAndReturnThrough(Action.OpenSettings)

        assertEquals("and after a second round", fifthSystem, shownSystem())
    }

    @Test
    fun theFocusedGameAndItsScrollStayAfterSettings() {
        show()
        press(Action.TapItem(Column.Systems, fifthSystem), Action.Enter)
        repeat(40) { press(Action.Move(Direction.Down)) }
        val title = "Nintendo DS title 41"
        compose.onNode(rowTitled(title)).assertIsDisplayed()
        val before = compose.onNode(rowTitled(title)).getUnclippedBoundsInRoot()

        leaveAndReturnThrough(Action.OpenSettings)

        compose.onNode(rowTitled(title)).assertIsDisplayed()
        assertEquals("same scroll position", before, compose.onNode(rowTitled(title)).getUnclippedBoundsInRoot())
    }

    @Test
    fun theFocusedSystemStaysFocusedAfterSearch() {
        show()
        press(Action.TapItem(Column.Systems, fifthSystem), Action.Search)
        // B on an empty query closes Search (with Plumemu's keyboard up, B erases first).
        compose.runOnIdle { router.pressButton(KeyEvent.KEYCODE_BUTTON_B) }
        compose.waitForIdle()

        assertEquals(fifthSystem, shownSystem())
    }

    @Test
    fun theFocusedGameStaysAfterTheGameMenu() {
        show()
        press(Action.TapItem(Column.Systems, fifthSystem), Action.Enter)
        repeat(12) { press(Action.Move(Direction.Down)) }
        press(Action.Menu, Action.Back)

        assertEquals(fifthSystem, shownSystem())
        compose.onNode(rowTitled("Nintendo DS title 13")).assertIsDisplayed()
    }

    /** [title] in the games list, not in the text-only details column that names the focused game at 768. */
    private fun rowTitled(title: String) = hasText(title) and !hasAnyAncestor(hasTestTag(HomeTags.DETAILS))
}
