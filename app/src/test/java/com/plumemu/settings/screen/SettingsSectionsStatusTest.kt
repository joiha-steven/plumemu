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

/** Library and Emulators on screen: plain lines for what was tried, progress, a long system list with Animations off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsSectionsStatusTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val status = mutableStateOf(SettingsStatus())
    private val actions = BusyActions()
    private lateinit var model: SettingsModel

    private class BusyActions : SettingsActions by NoActions {
        override fun rescanLibrary(): Boolean = false
    }

    private fun show(
        focus: SettingsFocus,
        content: SettingsContent,
        theme: PlumThemeSettings = PlumThemeSettings.Graphite,
    ) {
        model = SettingsModel(actions, focus).also { it.content = content }
        compose.setContent {
            PlumTheme(theme) {
                SettingsLayout(settingsForShot(), model, NoTouch, TEST_VERSION, { BOARD_CLOCK }, { BOARD_BATTERY }, status = status.value)
            }
        }
    }

    private fun press(action: Action) {
        compose.runOnIdle { model.onAction(action, PlumSettings(), SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Medium)) }
    }

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun scroll() = compose.onNodeWithTag(SettingsTags.OPTION_LIST).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

    @Test
    fun rescanWhileAScanRunsSaysSoAndShowsTheProgress() {
        show(optionsAt(SettingsOption.Rescan), ShotContent.library.copy(isScanning = true))
        compose.onNodeWithText(context.getString(R.string.settings_rescan_running)).assertIsDisplayed()
        compose.onNodeWithTag(SettingsPanelTags.SCAN_PULSE).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_rescan_starting)).assertIsDisplayed()
        press(Action.Enter)
        compose.onNodeWithText(context.getString(R.string.settings_rescan_busy)).assertIsDisplayed()
        compose.onNodeWithTag(SettingsPanelTags.SCAN_PULSE).assertExists() // the dot stays under the "already reading" notice
        press(Action.Move(Direction.Down))
        compose.onNodeWithTag(SettingsTags.NOTICE).assertDoesNotExist()
    }

    @Test
    fun thePickersAnswerIsSaidInTheFoldersPanel() {
        show(panelAt(SettingsOption.RomFolders, PanelPage.Folders, 2), ShotContent.library)
        status.value = SettingsStatus(folderAdd = FolderAddState.NotKept)
        compose.onNodeWithText(context.getString(R.string.setup_message_not_kept)).assertIsDisplayed()
        status.value = SettingsStatus(folderAdd = FolderAddState.Added)
        compose.onNodeWithText(context.getString(R.string.settings_folder_added)).assertIsDisplayed()
    }

    @Test
    fun aRemovalAskedInAnEarlierSettingsStillSaysRemoving() {
        // A fresh model (Settings opened again): only the process-level content knows.
        val removingId = ShotContent.library.folders[1].id
        show(panelAt(SettingsOption.RomFolders, PanelPage.Folders, 0), ShotContent.library.copy(removingFolderIds = setOf(removingId)))
        compose.onNodeWithText(context.getString(R.string.settings_folder_removing)).assertIsDisplayed()
    }

    @Test
    fun foldersThatCannotBeReadGiveAPlainLineInLibrary() {
        show(optionsAt(SettingsOption.RomFolders), SettingsContent(loadFailed = true))
        compose.onNodeWithTag(SettingsTags.LOAD_FAILED).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_library_unreadable)).assertIsDisplayed()
    }

    @Test
    fun emulatorsThatCannotBeReadGiveTheSameLine() {
        show(optionsAt(SettingsOption.DetectEmulators), SettingsContent(loadFailed = true))
        compose.onNodeWithTag(SettingsTags.LOAD_FAILED).assertIsDisplayed()
    }

    @Test
    fun otherSectionsDoNotCarryTheLine() {
        show(optionsAt(SettingsOption.ShowButtonHints), SettingsContent(loadFailed = true))
        compose.onNodeWithTag(SettingsTags.LOAD_FAILED).assertDoesNotExist()
    }

    @Test
    fun anUnreadableFolderSaysSo() {
        val folders = ShotContent.library.folders.mapIndexed { i, f -> if (i == 1) f.copy(isUnavailable = true) else f }
        show(panelAt(SettingsOption.RomFolders, PanelPage.Folders, 0), ShotContent.library.copy(folders = folders))
        compose.onNodeWithText(context.getString(R.string.settings_folder_unavailable)).assertIsDisplayed()
    }

    @Test
    fun withAnimationsOffALongSystemListJumpsToTheFocusedRow() {
        val many = (1..14).map { SystemEmulators("s$it", "System $it", automatic = null, chosen = null, installed = emptyList()) }
        val focus = SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Emulators)
        show(focus, SettingsContent(systems = many), PlumThemeSettings.Graphite.copy(animationsOn = false))
        assertTrue("the rows are longer than the column", scroll().maxValue() > 0f)
        compose.mainClock.autoAdvance = false
        repeat(many.size) { press(Action.Move(Direction.Down)) } // to Detect new emulators, the last row
        repeat(4) { frame() }
        val jumped = scroll().value()
        assertEquals("at the end of the list", scroll().maxValue(), jumped, 0f)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("nothing left to animate", jumped, scroll().value(), 0f)
        compose.onNodeWithTag(SettingsTags.option(SettingsOption.DetectEmulators)).assertIsDisplayed()
    }
}
