package com.plumemu

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import com.plumemu.content.HomeState
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.game.FakeGameActions
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.LibrarySystem
import com.plumemu.settings.screen.SettingsActivityActions
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsHost
import com.plumemu.settings.screen.SettingsOpening
import com.plumemu.telemetry.ScreenId
import com.plumemu.update.UpdateManifest
import com.plumemu.usage.LocalScreenTracker
import com.plumemu.usage.SETTLE_MS
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.graph.LibraryNotice
import com.plumemu.home.HomeNotices
import com.plumemu.home.WhatsNewFixtures
import com.plumemu.home.WhatsNewTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "What's new" card is made and counted as shown only once the main screen is in front again: notes that arrive while
 * Settings is open (the main screen hidden under it) must not be closed by Settings' own A and B presses and lost.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class MainScreenSettingsWhatsNewTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private lateinit var host: SettingsHost
    private var settings by mutableStateOf(PlumSettings.Defaults)
    private val games = FakeGameActions()
    private val whatsNew = MutableStateFlow<UpdateManifest?>(null)
    private val shownCodes = mutableListOf<Long>()
    private val notice = MutableStateFlow<LibraryNotice?>(null)
    private val noticesShown = mutableListOf<Long>()
    private val waiting = WhatsNewFixtures.grouped.copy(versionCode = 13, versionName = "0.1.2")
    private val tracked = mutableListOf<ScreenId>()
    private val real = SettingsContent(librarySystems = listOf(LibrarySystem("gba", "Game Boy Advance", 196)))

    private val activity =
        object : SettingsActivityActions {
            override fun openFolderPicker(): Boolean = false

            override fun openHomeSettings(onNotHome: () -> Unit): Boolean = false

            override fun isHomeApp(): Boolean = true

            override fun isUsageAccessGranted(): Boolean = false

            override fun openBackupPicker(kind: BackupKind): Boolean = false

            override fun openRestorePicker(): Boolean = false
        }

    private fun show() {
        HomeTreeProbe.live = 0
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            host = remember { SettingsHost("test", update = {}, resetAllSettings = {}, resetLibrary = {}, content = MutableSharedFlow(replay = 1), remembered = { real }, activity = activity) }
            // Kept across recompositions, as MainActivity keeps them: a new object each time would be a changed input.
            val state = remember { HomeState.Ready(FakeHomeContent.board()) }
            val callbacks = remember { HomeCallbacks(games = games, whatsNew = whatsNew, onWhatsNewShown = { shownCodes += it }, notices = HomeNotices(notice = notice, onNoticeShown = { noticesShown += it.serial })) }
            CompositionLocalProvider(LocalScreenTracker provides { tracked += it }) {
                MainScreen(settings, state, router, callbacks, onRetry = {}, settingsHost = host)
            }
        }
        compose.waitUntil(5_000) { host.deviceFacts.value != null }
        compose.waitForIdle()
    }

    private fun openSettingsWhole() {
        compose.runOnIdle { router.send(Action.OpenSettings) }
        repeat(SettingsOpening.WHOLE + 3) { compose.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun notesArrivingUnderSettingsAreNotClosedBySettingsPressesAndShowOnceBackOnTheMainScreen() {
        show()
        compose.mainClock.autoAdvance = false
        openSettingsWhole()
        whatsNew.value = waiting
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        // Settings' own presses: A opens a panel, B steps back; none may reach a card of the hidden main screen.
        compose.runOnIdle {
            router.send(Action.Enter)
            router.send(Action.Back)
            router.send(Action.Enter)
        }
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        assertEquals("the notes are not counted as shown while Settings is open", emptyList<Long>(), shownCodes)
        compose.runOnIdle { router.send(Action.Back) }
        compose.runOnIdle { router.send(Action.Back) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS + 600L)
        compose.onNodeWithTag(WhatsNewTags.CARD).assertIsDisplayed()
        assertEquals("still not counted before the person closes it", emptyList<Long>(), shownCodes)
    }

    @Test
    fun aTimedNoticeArrivingUnderSettingsWaitsForTheMainScreenAndIsNotUsedUpUnseen() {
        show()
        compose.mainClock.autoAdvance = false
        openSettingsWhole()
        notice.value = LibraryNotice(serial = 7, added = 24, removed = 0, addedSystems = emptyList())
        compose.mainClock.advanceTimeBy(NOTICE_SPAN_MS)
        assertEquals("not told as shown while hidden under Settings", emptyList<Long>(), noticesShown)
        compose.runOnIdle { router.send(Action.Back) }
        compose.runOnIdle { router.send(Action.Back) }
        compose.mainClock.advanceTimeBy(NOTICE_SPAN_MS)
        assertEquals("shown for its time once the main screen is back", listOf(7L), noticesShown)
    }

    @Test
    fun aNoticeShowingWhenSettingsOpensHoldsItsTimeUntilTheMainScreenIsBack() {
        show()
        compose.mainClock.autoAdvance = false
        notice.value = LibraryNotice(serial = 8, added = 24, removed = 0, addedSystems = emptyList())
        compose.mainClock.advanceTimeBy(1_000L)
        openSettingsWhole()
        compose.mainClock.advanceTimeBy(NOTICE_SPAN_MS)
        assertEquals("its seconds do not run out under Settings", emptyList<Long>(), noticesShown)
        compose.runOnIdle { router.send(Action.Back) }
        compose.runOnIdle { router.send(Action.Back) }
        compose.mainClock.advanceTimeBy(2_000L)
        assertEquals("back in front it shows again for its whole time", emptyList<Long>(), noticesShown)
        compose.mainClock.advanceTimeBy(NOTICE_SPAN_MS)
        assertEquals("then it is told as shown once", listOf(8L), noticesShown)
    }

    private companion object {
        const val NOTICE_SPAN_MS = 6_000L
    }
}
