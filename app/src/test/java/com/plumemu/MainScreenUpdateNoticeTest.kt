package com.plumemu

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.components.TopBarTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.FakeGameActions
import com.plumemu.game.GamePanelTags
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.WhatsNewFixtures
import com.plumemu.home.WhatsNewTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsHost
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.NoteSection
import com.plumemu.update.SectionKind
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The main screen's update notice: START (or a tap on the pill) opens Settings on About › Update available while an update
 * waits and on its start page otherwise; the "What's new" card comes once, over an idle main screen only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class MainScreenUpdateNoticeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val updateFound = MutableStateFlow<String?>(null)
    private val whatsNew = MutableStateFlow<UpdateManifest?>(null)
    private val shownCodes = mutableListOf<Long>()
    private val games = FakeGameActions()
    private lateinit var router: InputRouter

    private val waiting = WhatsNewFixtures.grouped.copy(versionCode = 13, versionName = "0.1.2")

    private fun show() = show { compose.setContent(it) }

    private fun show(setContent: (@androidx.compose.runtime.Composable () -> Unit) -> Unit) {
        setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            val host =
                remember {
                    SettingsHost(
                        "test",
                        update = {},
                        resetAllSettings = {},
                        resetLibrary = {},
                        content = flowOf(SettingsContent(update = UpdateView(lastCheck = LastCheck(1, CheckOutcome.Found), found = waiting))),
                    )
                }
            MainScreen(
                PlumSettings.Defaults,
                HomeState.Ready(FakeHomeContent.board()),
                router,
                HomeCallbacks(games = games, updateFound = updateFound, whatsNew = whatsNew, onWhatsNewShown = { shownCodes += it }),
                onRetry = {},
                settingsHost = host,
            )
        }
    }

    private fun press(vararg actions: Action) {
        actions.forEach {
            compose.runOnIdle { router.send(it) }
            compose.waitForIdle()
        }
    }

    @Test
    fun startOpensSettingsOnTheUpdateWhileOneWaits() {
        updateFound.value = "0.1.2"
        show()
        press(Action.OpenSettings)
        compose.onNodeWithTag(SettingsTags.UPDATE_PANEL).assertIsDisplayed()
    }

    @Test
    fun aTapOnThePillDoesTheSame() {
        updateFound.value = "0.1.2"
        show()
        compose.onNodeWithTag(TopBarTags.PILL, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.UPDATE_PANEL).assertIsDisplayed()
    }

    @Test
    fun startOpensSettingsOnItsStartPageWhenNothingWaits() {
        show()
        press(Action.OpenSettings)
        compose.onNodeWithTag(SettingsTags.SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.UPDATE_PANEL).assertDoesNotExist()
    }

    @Test
    fun backFromTheUpdatePanelGoesBackThroughSettingsToTheMainScreen() {
        updateFound.value = "0.1.2"
        show()
        press(Action.OpenSettings)
        compose.onNodeWithTag(SettingsTags.UPDATE_PANEL).assertIsDisplayed()
        press(Action.Back)
        compose.onNodeWithTag(SettingsTags.SCREEN).assertIsDisplayed()
        press(Action.Back, Action.Back)
        compose.onNodeWithTag(SettingsTags.SCREEN).assertDoesNotExist()
    }

    @Test
    fun theCardShowsOnceOverTheIdleMainScreenAndBClosesIt() {
        whatsNew.value = waiting
        show()
        compose.onNodeWithTag(WhatsNewTags.CARD).assertIsDisplayed()
        // Counted when it closes, not when it opens.
        assertEquals(emptyList<Long>(), shownCodes)
        // The main screen under it hears nothing: START does not open Settings.
        press(Action.OpenSettings)
        compose.onNodeWithTag(SettingsTags.SCREEN).assertDoesNotExist()
        press(Action.Back)
        compose.onNodeWithTag(WhatsNewTags.CARD).assertDoesNotExist()
        assertEquals(listOf(13L), shownCodes)
    }

    @Test
    fun aAndATapOnOkCloseItToo() {
        whatsNew.value = waiting
        show()
        press(Action.Enter)
        compose.onNodeWithTag(WhatsNewTags.CARD).assertDoesNotExist()

        whatsNew.value = null
        whatsNew.value = waiting.copy(versionCode = 14)
        compose.waitForIdle()
        compose.onNodeWithTag(WhatsNewTags.OK).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(WhatsNewTags.CARD).assertDoesNotExist()
    }

    @Test
    fun nothingIsShownWithoutNotes() {
        show()
        compose.onNodeWithTag(WhatsNewTags.CARD).assertDoesNotExist()
        assertEquals(emptyList<Long>(), shownCodes)
    }

    @Test
    fun theCardWaitsWhileAGamePanelIsOpenAndComesWhenItCloses() {
        show()
        press(Action.Move(Direction.Right), Action.Enter, Action.Move(Direction.Down), Action.Menu)
        compose.onNodeWithTag(GamePanelTags.PANEL).assertIsDisplayed()
        whatsNew.value = waiting
        compose.waitForIdle()
        compose.onNodeWithTag(WhatsNewTags.CARD).assertDoesNotExist()
        assertEquals(emptyList<Long>(), shownCodes)
        press(Action.Back)
        compose.onNodeWithTag(GamePanelTags.PANEL).assertDoesNotExist()
        compose.onNodeWithTag(WhatsNewTags.CARD).assertIsDisplayed()
    }

    private val longNotes =
        waiting.copy(
            sections = listOf(NoteSection(SectionKind.New, List(14) { "Note number ${it + 1} of a release with a lot to say, long enough to wrap on a narrow card." })),
        )

    private fun notesScroll() = compose.onNodeWithTag(WhatsNewTags.NOTES).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun upAndDownScrollTheNotesAndOnlyOkClosesIt() {
        whatsNew.value = longNotes
        show()
        assertEquals(0f, notesScroll(), 0.5f)
        press(Action.Move(Direction.Down))
        val down = notesScroll()
        assertTrue("Down scrolls the notes ($down)", down > 0f)
        press(Action.Move(Direction.Up))
        assertTrue("Up scrolls back", notesScroll() < down)
        press(Action.Move(Direction.Left))
        compose.onNodeWithTag(WhatsNewTags.CARD).assertIsDisplayed()
    }

    @Test
    fun afterAOrBClosesTheCardStartReachesTheMainScreenAgain() {
        updateFound.value = "0.1.2"
        whatsNew.value = waiting
        show()
        press(Action.Enter)
        compose.onNodeWithTag(WhatsNewTags.CARD).assertDoesNotExist()
        press(Action.OpenSettings)
        compose.onNodeWithTag(SettingsTags.UPDATE_PANEL).assertIsDisplayed()
        press(Action.Back, Action.Back, Action.Back)
        compose.onNodeWithTag(SettingsTags.SCREEN).assertDoesNotExist()

        whatsNew.value = waiting.copy(versionCode = 14)
        compose.waitForIdle()
        compose.onNodeWithTag(WhatsNewTags.CARD).assertIsDisplayed()
        press(Action.Back)
        compose.onNodeWithTag(WhatsNewTags.CARD).assertDoesNotExist()
        press(Action.OpenSettings)
        compose.onNodeWithTag(SettingsTags.UPDATE_PANEL).assertIsDisplayed()
    }

    @Test
    fun anActivityRecreatedWithTheCardOpenShowsItAgainAndOneClosedStaysClosed() {
        val tester = StateRestorationTester(compose)
        whatsNew.value = waiting
        show { tester.setContent(it) }
        compose.onNodeWithTag(WhatsNewTags.CARD).assertIsDisplayed()
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithTag(WhatsNewTags.CARD).assertIsDisplayed()
        assertEquals(emptyList<Long>(), shownCodes)
        press(Action.Back)
        assertEquals(listOf(13L), shownCodes)
        // The record is still being saved when the activity is re-created: the closed card must not come back.
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithTag(WhatsNewTags.CARD).assertDoesNotExist()
    }
}
