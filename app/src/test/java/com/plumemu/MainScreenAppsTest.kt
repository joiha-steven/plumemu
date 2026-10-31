package com.plumemu

import android.content.ComponentName
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.GameRowModel
import com.plumemu.home.HomeContent
import com.plumemu.home.HomeTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.SettingsHost
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A (gamepad) and a tap on a row of the Apps entry, through the screens the Activity shows: the
 * real `MainActivity`'s own `homeCallbacks` handed to `MainScreen`, which copies them for Settings
 * (a host is given, as in the app) and passes them through `HomeWithRolePrompt` to `HomeScreen`,
 * whose game panel controller opens the row with the Activity's `GraphAppActions` on the real graph.
 *
 * `MainScreen` is hosted here directly rather than read from the Activity's own window: in
 * Robolectric the Activity's content (settings, Room) does not reliably reach its composition
 *, and its first-run setup would take the same keys. The keys go through
 * `InputRouter.onKeyEvent`, the call `MainActivity.dispatchKeyEvent` makes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class MainScreenAppsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    private val activity = controller.get()
    private val app = activity.application as PlumApplication
    private lateinit var router: InputRouter

    private val alpha = ComponentName("com.example.alpha", "com.example.alpha.Main")
    private val beta = ComponentName("com.example.beta", "com.example.beta.Main")

    @Before
    fun listApps() {
        installLauncherApp(app, alpha.packageName, alpha.className, "Alpha")
        installLauncherApp(app, beta.packageName, beta.className, "Beta")
        onMainLoop { app.graph.refreshApps() }
        assertEquals(listOf("Alpha", "Beta"), app.graph.listedApps.value.map { it.label })
    }

    @After
    fun tearDown() {
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.pause().stop()
        controller.destroy()
    }

    /** What the Activity's content is once the graph listed the two apps: Recent, Favorites, Apps (index 2), no games. */
    private fun show() {
        val rows = listOf(alpha to "Alpha", beta to "Beta").map { (component, label) -> GameRowModel("${component.packageName}/${component.className}", label, region = "") }
        val content = HomeContent.of(emptyList(), emptyList(), rows, emptyList())
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            val host = remember { SettingsHost("test", update = {}, resetAllSettings = {}, resetLibrary = {}) }
            MainScreen(PlumSettings.Defaults, HomeState.Ready(content), router, activity.homeCallbacks, onRetry = {}, settingsHost = host)
        }
        compose.waitForIdle()
        shadowOf(app).clearNextStartedActivities()
    }

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

    @Test
    fun aOnAnAppsRowStartsThatApp() {
        show()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_DOWN) // Recent, Favorites, Apps
        press(KeyEvent.KEYCODE_BUTTON_A) // into Apps' list
        assertEquals("entering the list starts nothing", null, shadowOf(app).peekNextStartedActivity())
        press(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals("A on the row started the app", alpha, shadowOf(app).nextStartedActivity?.component)
    }

    @Test
    fun aTapFocusesAnAppsRowAndASecondTapStartsThatApp() {
        show()
        tap(HomeTags.system(2))
        tap(HomeTags.system(2)) // into Apps' list, Alpha focused
        tap(HomeTags.game(1))
        assertEquals("the first tap on Beta only focuses it", null, shadowOf(app).peekNextStartedActivity())
        tap(HomeTags.game(1))
        assertEquals("the second tap started Beta", beta, shadowOf(app).nextStartedActivity?.component)
    }
}
