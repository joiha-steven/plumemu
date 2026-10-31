package com.plumemu.home

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.AppMenuEntryId
import com.plumemu.game.FakeAppActions
import com.plumemu.game.FakeGameActions
import com.plumemu.game.GamePanelTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Apps entry's menu on the whole screen with a real router: START on an app and a long
 * press open it, App info and Uninstall start Android's screens, Uninstall is missing for an app that cannot be
 * removed, and the hint bar says START Menu on an app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class AppsMenuScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val apps = FakeAppActions()
    private lateinit var router: InputRouter

    private fun show() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(appsGridLibrary(), router, HomeCallbacks(games = FakeGameActions(), apps = apps)) }
        }
        compose.waitForIdle()
    }

    private fun send(vararg actions: Action) =
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }

    private fun openApps() = send(Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter)

    private fun isPanelOpen() = compose.onAllNodesWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hasText(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hasRow(entry: AppMenuEntryId) = compose.onAllNodesWithTag(GamePanelTags.row(entry.name), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun startOpensTheMenuOfTheFocusedAppAndTheHintBarSaysSo() {
        show()
        openApps()
        compose.onNodeWithTag(HomeTags.hint(Action.Menu), useUnmergedTree = true).assertExists()
        assertTrue("the hint bar says START Menu on an app", hasText("Menu"))
        send(Action.Move(Direction.Down))
        send(Action.Menu)
        assertTrue("START opens the app menu", isPanelOpen())
        assertTrue("titled with the app", hasText(AppsGridApps[1].title))
        assertTrue("App info is there", hasText("App info"))
        assertFalse("an app Android cannot remove has no Uninstall", hasRow(AppMenuEntryId.Uninstall))
        send(Action.Back)
        assertFalse("B closes it", isPanelOpen())
    }

    @Test
    fun aLongPressOpensTheMenuAndAppInfoStartsAndroidsPage() {
        show()
        openApps()
        compose.onNodeWithTag(HomeTags.game(2), useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        assertTrue("a long press opens the menu", isPanelOpen())
        compose.onNodeWithTag(GamePanelTags.row(AppMenuEntryId.Info.name), useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(listOf(AppsGridApps[2].id), apps.infoOpened)
        assertFalse("started: the menu closes", isPanelOpen())
        assertTrue("and no app was opened", apps.opened.isEmpty())
    }

    @Test
    fun uninstallIsOfferedForARemovableAppAndStartsAndroidsConfirmation() {
        apps.uninstallable = setOf(AppsGridApps[0].id)
        show()
        openApps()
        send(Action.Menu)
        assertTrue(hasRow(AppMenuEntryId.Uninstall))
        send(Action.Move(Direction.Down), Action.Enter)
        assertEquals(listOf(AppsGridApps[0].id), apps.uninstalled)
        assertFalse(isPanelOpen())
    }
}
