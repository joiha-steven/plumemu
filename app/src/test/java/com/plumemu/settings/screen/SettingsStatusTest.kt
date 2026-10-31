package com.plumemu.settings.screen

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.graph.LibraryResetState
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What Settings says about work it started, the hints of read-only rows, and scrolling with Animations off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsStatusTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val status = mutableStateOf(SettingsStatus())
    private lateinit var model: SettingsModel

    private fun show(
        focus: SettingsFocus,
        theme: PlumThemeSettings = PlumThemeSettings.Graphite,
    ) {
        model = SettingsModel(NoActions, focus)
        compose.setContent {
            PlumTheme(theme) {
                SettingsLayout(settingsForShot(), model, NoTouch, TEST_VERSION, { BOARD_CLOCK }, { BOARD_BATTERY }, status = status.value)
            }
        }
    }

    private fun press(action: Action) {
        compose.runOnIdle { model.onAction(action, PlumSettings(), SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Medium)) }
    }

    /** One frame, then whatever that frame started (effects run between frames, not inside them). */
    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun range(tag: String) =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

    private fun textScroll(): Float = range(SettingsTags.LICENSE_TEXT).value()

    @Test
    fun resetLibrarySaysForgettingThenDoneOrFailed() {
        show(panelAt(SettingsOption.ResetLibrary, PanelPage.Confirm, SettingsModel.CONFIRM_RESET))
        press(Action.Enter)
        compose.onNodeWithText(context.getString(R.string.settings_reset_library_forgetting)).assertIsDisplayed()
        status.value = SettingsStatus(libraryReset = LibraryResetState.Forgetting)
        compose.onNodeWithText(context.getString(R.string.settings_reset_library_forgetting)).assertIsDisplayed()
        status.value = SettingsStatus(libraryReset = LibraryResetState.Forgotten)
        compose.onNodeWithText(context.getString(R.string.settings_reset_library_done)).assertIsDisplayed()
        status.value = SettingsStatus(libraryReset = LibraryResetState.Failed)
        compose.onNodeWithText(context.getString(R.string.settings_reset_library_failed)).assertIsDisplayed()
    }

    @Test
    fun aFailedWriteIsSaidUnderTheOptionsAndResetAllDoesNotClaimDone() {
        status.value = SettingsStatus(saveFailed = true)
        show(panelAt(SettingsOption.ResetAll, PanelPage.Confirm, SettingsModel.CONFIRM_RESET))
        press(Action.Enter)
        compose.onNodeWithTag(SettingsTags.SAVE_FAILED).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_reset_all_done)).assertDoesNotExist()
    }

    @Test
    fun versionAndPrivacyShowNoChangeHintOnWideScreens() {
        show(optionsAt(SettingsOption.Version))
        compose.onNodeWithTag(SettingsTags.HINT_CONFIRM).assertDoesNotExist()
        compose.onNodeWithTag(SettingsTags.HINT_BACK).assertIsDisplayed()
        press(Action.Move(Direction.Down)) // Check for updates: A checks now
        compose.onNodeWithTag(SettingsTags.HINT_CONFIRM).assertIsDisplayed()
        press(Action.Move(Direction.Down)) // Check for updates daily: A flips it
        compose.onNodeWithTag(SettingsTags.HINT_CONFIRM).assertIsDisplayed()
        press(Action.Move(Direction.Down)) // Open-source licenses: A opens the list
        compose.onNodeWithTag(SettingsTags.HINT_CONFIRM).assertIsDisplayed()
        press(Action.Move(Direction.Down)) // Privacy
        compose.onNodeWithTag(SettingsTags.HINT_CONFIRM).assertDoesNotExist()
        compose.onNodeWithTag(SettingsTags.HINT_BACK).assertIsDisplayed()
    }

    @Test
    fun withAnimationsOffTheLicenceListJumpsToTheFocusedRow() {
        show(panelAt(SettingsOption.Licenses, PanelPage.Licenses, 0), PlumThemeSettings.Graphite.copy(animationsOn = false))
        assertTrue("the list is longer than the panel", range(SettingsTags.LICENSE_LIST).maxValue() > 0f)
        compose.mainClock.autoAdvance = false
        repeat(LicenseNotices.all.size - 1) { press(Action.Move(Direction.Down)) }
        repeat(4) { frame() }
        val jumped = range(SettingsTags.LICENSE_LIST).value()
        assertTrue("the list moved to the last row ($jumped)", jumped > 0f)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("nothing left to animate", jumped, range(SettingsTags.LICENSE_LIST).value(), 0f)
    }

    @Test
    fun withAnimationsOffALicenceTextJumpsByThreeRows() {
        show(panelAt(SettingsOption.Licenses, PanelPage.Licenses, 0), PlumThemeSettings.Graphite.copy(animationsOn = false))
        press(Action.Enter)
        compose.waitForIdle()
        compose.waitUntil(5_000) { range(SettingsTags.LICENSE_TEXT).maxValue() > 0f } // the text is read off the main thread
        assertEquals(0f, textScroll(), 0f)
        compose.mainClock.autoAdvance = false
        press(Action.Move(Direction.Down))
        repeat(4) { frame() }
        assertTrue("the text moved within four frames (${textScroll()})", textScroll() > 0f)
        val oneStep = textScroll()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("nothing left to animate", oneStep, textScroll(), 0f)
    }
}
