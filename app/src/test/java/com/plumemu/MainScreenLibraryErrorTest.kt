package com.plumemu

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.CachedAppearance
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsHost
import com.plumemu.settings.screen.SettingsTags
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two cases. A1: "Plumemu could not open its library" offered only A (Try again), so with a
 * database that never opens and Plumemu as the home app the device was stuck there. START (or a tap on its hint)
 * now opens Settings, whose library sections say they cannot read it; Back and the Home button come back.
 * A3: while the library's first answer (or the settings) has not come, the screen was blank; after half a second
 * it says "Opening your library…".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class MainScreenLibraryErrorTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var router: InputRouter
    private val homePresses = HomePresses()
    private var retries = 0

    // What the real graph's Settings content gives when the database cannot open (GraphSettingsLibrary.content).
    private fun failedLibraryHost() = SettingsHost("test", update = {}, resetAllSettings = {}, resetLibrary = {}, content = flowOf(SettingsContent(loadFailed = true)))

    private fun show(
        state: MutableState<HomeState?> = mutableStateOf(HomeState.Failed),
        withSettings: Boolean = true,
        onRetry: () -> Unit = { retries++ },
    ) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            val host = remember { if (withSettings) failedLibraryHost() else null }
            MainScreen(PlumSettings.Defaults, state.value, router, HomeCallbacks(), onRetry = onRetry, settingsHost = host, homePresses = homePresses)
        }
        compose.waitForIdle()
    }

    private fun press(vararg actions: Action) {
        actions.forEach {
            compose.runOnIdle { router.send(it) }
            compose.waitForIdle()
        }
    }

    private fun assertErrorShown() {
        compose.onNodeWithTag(MainScreenTags.LIBRARY_ERROR).assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.SCREEN).assertDoesNotExist()
    }

    private fun assertSettingsShown() {
        compose.onNodeWithTag(SettingsTags.SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(MainScreenTags.LIBRARY_ERROR).assertDoesNotExist()
    }

    @Test
    fun theHintBarOffersTryAgainAndSettings() {
        show()
        compose.onNodeWithTag(MainScreenTags.TRY_AGAIN).assertIsDisplayed()
        compose.onNodeWithTag(MainScreenTags.SETTINGS).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.home_hint_settings)).assertIsDisplayed()
    }

    @Test
    fun startOpensSettingsAndBackComesBack() {
        show()
        press(Action.Menu) // START on the gamepad
        assertSettingsShown()
        press(Action.Back)
        assertErrorShown()
        assertEquals("coming back from Settings tries again once", 1, retries)
        press(Action.Enter)
        assertEquals("A still tries again once back", 2, retries)
    }

    @Test
    fun aTapOnTheSettingsHintOpensSettings() {
        show()
        compose.onNodeWithTag(MainScreenTags.SETTINGS).performClick()
        compose.waitForIdle()
        assertSettingsShown()
        assertEquals("opening Settings is not a retry", 0, retries)
    }

    @Test
    fun settingsWalksItsSectionsWithAnUnreadableLibrary() {
        show()
        press(Action.Menu)
        // Every section, Library and Emulators included, with the library unreadable: none crashes.
        repeat(12) { press(Action.Move(Direction.Down), Action.Enter, Action.Back) }
        assertSettingsShown()
    }

    @Test
    fun theHomeButtonClosesSettingsBackToTheError() {
        show()
        press(Action.OpenSettings)
        assertSettingsShown()
        compose.runOnIdle { homePresses.press() }
        compose.waitForIdle()
        assertErrorShown()
        assertEquals("the Home button back from Settings tries again once", 1, retries)
        compose.runOnIdle { homePresses.press() }
        compose.waitForIdle()
        assertEquals("a Home press on the error itself is not a retry", 1, retries)
    }

    @Test
    fun aLibraryFixedInSettingsShowsTheHomeOnTheWayBack() {
        val state = mutableStateOf<HomeState?>(HomeState.Failed)
        var isFixed = false
        // The graph's retry reads the library again; after Reset library or a restore it opens.
        show(state, onRetry = { if (isFixed) state.value = HomeState.Ready(FakeHomeContent.board()) })
        press(Action.Menu)
        assertSettingsShown()
        isFixed = true // what Reset library did while Settings was open
        press(Action.Back)
        compose.onNodeWithTag(MainScreenTags.LIBRARY_ERROR).assertDoesNotExist()
        compose.onNodeWithText("Super Nintendo").assertIsDisplayed()
    }

    @Test
    fun withoutSettingsOnlyTryAgainIsOffered() {
        show(withSettings = false)
        compose.onNodeWithTag(MainScreenTags.SETTINGS).assertDoesNotExist()
        press(Action.Menu)
        assertErrorShown()
    }

    @Test
    fun aLibraryThatTakesItsTimeSaysItIsOpeningAfterHalfASecond() {
        val state = mutableStateOf<HomeState?>(null)
        compose.mainClock.autoAdvance = false
        show(state)
        compose.mainClock.advanceTimeBy(OPENING_TEXT_DELAY_MS - 100)
        compose.onNodeWithTag(MainScreenTags.OPENING_LIBRARY).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag(MainScreenTags.OPENING_LIBRARY).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.home_opening_library)).assertIsDisplayed()

        compose.mainClock.autoAdvance = true
        state.value = HomeState.Ready(FakeHomeContent.board())
        compose.waitForIdle()
        compose.onNodeWithTag(MainScreenTags.OPENING_LIBRARY).assertDoesNotExist()
        compose.onNodeWithText("Super Nintendo").assertIsDisplayed()
    }

    @Test
    fun theWaitForSettingsAndForTheLibraryIsOneWait() {
        val settings = mutableStateOf<PlumSettings?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            MainScreen(settings.value, null, remember { InputRouter(scope, onDebug = {}) }, HomeCallbacks(), onRetry = {}, waitingAppearance = CachedAppearance(0xFF000000.toInt(), isTextDark = false))
        }
        compose.mainClock.advanceTimeBy(OPENING_TEXT_DELAY_MS - 100)
        settings.value = PlumSettings.Defaults // the settings arrive, the library is still opening
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag(MainScreenTags.OPENING_LIBRARY).assertIsDisplayed()
    }

    @Test
    fun settingsThatTakeTheirTimeShowTheSameLineInTheCachedColours() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            MainScreen(null, null, remember { InputRouter(scope, onDebug = {}) }, HomeCallbacks(), onRetry = {}, waitingAppearance = CachedAppearance(0xFFE8E4DA.toInt(), isTextDark = true))
        }
        compose.mainClock.advanceTimeBy(OPENING_TEXT_DELAY_MS - 100)
        compose.onNodeWithTag(MainScreenTags.OPENING_LIBRARY).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag(MainScreenTags.OPENING_LIBRARY).assertIsDisplayed()
    }
}
