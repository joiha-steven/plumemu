package com.plumemu

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.CollectionsShotsBase
import com.plumemu.home.HomeTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.SettingsHost
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Collections folder opens as a grid unless its entry id is in `appearance.listCollections`, through
 * `MainScreen` (which hands that set to `HomeScreen`) with the real Activity's `homeCallbacks`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class MainScreenCollectionsViewTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    private val activity = controller.get()
    private lateinit var router: InputRouter

    @After
    fun tearDown() {
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.pause().stop()
        controller.destroy()
    }

    /** The own collection "RPG" has entry id `collection:7` (`CollectionsShotsBase`). */
    private fun show(settings: PlumSettings) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            val host = remember { SettingsHost("test", update = {}, resetAllSettings = {}, resetLibrary = {}) }
            MainScreen(settings, HomeState.Ready(CollectionsShotsBase.content()), router, activity.homeCallbacks, onRetry = {}, settingsHost = host)
        }
        compose.waitForIdle()
    }

    private fun withLists(vararg ids: String) =
        PlumSettings.Defaults.copy(appearance = PlumSettings.Defaults.appearance.copy(listCollections = ids.toSet()))

    private fun press(code: Int) {
        compose.runOnIdle {
            router.onKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
            router.onKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_UP, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
        }
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun gridShown() = compose.onAllNodesWithTag(HomeTags.GRID_PAGE, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** R2 from the cold start's Favorites reaches the folders page; RPG is its fourth row. */
    private fun openRpgByKeys() {
        press(KeyEvent.KEYCODE_BUTTON_R2)
        repeat(3) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        press(KeyEvent.KEYCODE_BUTTON_A)
    }

    @Test
    fun aFolderNotInTheSavedListsOpensAsAGridByKeys() {
        show(PlumSettings.Defaults)
        openRpgByKeys()
        assertTrue("RPG opens as a grid", gridShown())
    }

    @Test
    fun aFolderInTheSavedListsOpensAsAListByKeys() {
        show(withLists("collection:7"))
        openRpgByKeys()
        assertFalse("RPG was chosen as a list", gridShown())
        assertTrue("its rows are listed", compose.onAllNodesWithTag(HomeTags.game(0), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aTapOpensTheFolderAsTheSavedListsSay() {
        show(withLists("collection:7"))
        tap(HomeTags.system(1)) // Collections
        tap(HomeTags.system(1)) // into its folders
        tap(HomeTags.game(3)) // RPG focused
        tap(HomeTags.game(3)) // opened
        assertFalse("RPG was chosen as a list", gridShown())
    }
}
