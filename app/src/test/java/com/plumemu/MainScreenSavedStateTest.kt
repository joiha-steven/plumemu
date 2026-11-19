package com.plumemu

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.FIRST_SYSTEM_INDEX
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.GamesPagesInfo
import com.plumemu.home.HomeCallbacks
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

/**
 * The process may end with the screen off (compiled-code restart) and Android recreates the main
 * screen from its saved state: the focused system, game and scroll, and an open Settings, come back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class MainScreenSavedStateTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private val fifthSystem = FIRST_SYSTEM_INDEX + 4 // Nintendo DS
    private val restoration by lazy { StateRestorationTester(compose) }

    private fun show() {
        val content = FakeHomeContent.large()
        restoration.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            val host = remember { SettingsHost("test", update = {}, resetAllSettings = {}, resetLibrary = {}) }
            MainScreen(PlumSettings.Defaults, HomeState.Ready(content), router, HomeCallbacks(), onRetry = {}, settingsHost = host)
        }
        compose.waitForIdle()
    }

    private fun press(vararg actions: Action) {
        actions.forEach {
            compose.runOnIdle { router.send(it) }
            compose.waitForIdle()
        }
    }

    private fun recreate() {
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
    }

    private fun shownSystem(): Int =
        compose.onNode(SemanticsMatcher.keyIsDefined(GamesPagesInfo), useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(GamesPagesInfo)!!.pages.firstVisibleItemIndex

    private fun rowTitled(title: String) = hasText(title) and !hasAnyAncestor(hasTestTag(HomeTags.DETAILS))

    @Test
    fun theFocusedSystemGameAndScrollComeBack() {
        show()
        // A cold start lands on Favorites' first game: Back first, so the tap and Enter below open the system's games as before.
        press(Action.Back, Action.TapItem(Column.Systems, fifthSystem), Action.Enter)
        repeat(30) { press(Action.Move(Direction.Down)) }
        val title = "Nintendo DS title 31"
        compose.onNode(rowTitled(title)).assertIsDisplayed()
        val before = compose.onNode(rowTitled(title)).getUnclippedBoundsInRoot()

        recreate()

        assertEquals("the same system", fifthSystem, shownSystem())
        compose.onNode(rowTitled(title)).assertIsDisplayed()
        assertEquals("the same scroll", before, compose.onNode(rowTitled(title)).getUnclippedBoundsInRoot())
        press(Action.Move(Direction.Down))
        compose.onNode(rowTitled("Nintendo DS title 32")).assertIsDisplayed()
    }

    @Test
    fun anOpenSettingsComesBackOpen() {
        show()
        press(Action.TapItem(Column.Systems, fifthSystem), Action.OpenSettings)
        compose.onNodeWithTag(HomeTags.SYSTEMS).assertDoesNotExist()

        recreate()

        compose.onNodeWithTag(HomeTags.SYSTEMS).assertDoesNotExist()
        press(Action.Back)
        compose.onNodeWithTag(HomeTags.SYSTEMS).assertIsDisplayed()
        assertEquals("the system left before Settings", fifthSystem, shownSystem())
    }
}
