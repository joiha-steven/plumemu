package com.plumemu.settings.screen

import androidx.compose.runtime.remember
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Files not added on the first line a cursor can stand on: the heading over it is not a stop, so the list rests at its very top with no fade over that heading. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class FilesNotAddedRestTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val settings = PlumSettings(flags = SetupFlags(setupCompleted = true))
    private lateinit var model: SettingsModel
    private lateinit var geometry: SettingsGeometry

    private val folders = List(FOLDERS) { SettingsFolderRow(FolderKey(it.toLong(), "Misc ROMs $it"), it + 1, FolderAnswer.Unanswered) }
    private val content = SettingsContent(unknownFolders = folders, notAdded = notAddedItems(emptyList(), folders), gameCount = 1)

    private val section = SettingsSection.shownIn(content).first { SettingsOption.FilesNotAdded in it.rowsIn(content) }
    private val rowFocus = SettingsFocus(column = SettingsColumn.Options, section = section, rememberedOption = mapOf(section to section.rowsIn(content).indexOf(SettingsOption.FilesNotAdded)))

    private fun show(focus: SettingsFocus) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Large)) {
                geometry = settingsGeometry(PlumTheme.layout)
                model = remember { SettingsModel(NoActions, focus).also { it.content = content } }
                SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
            }
        }
        compose.waitForIdle()
    }

    private fun press(action: Action) {
        compose.runOnIdle { model.onAction(action, settings, geometry) }
        compose.waitForIdle()
    }

    private fun assertRestsAtTheTop() {
        val heading = compose.onNodeWithTag(SettingsTags.panelItem(0), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val list = compose.onNodeWithTag(SettingsTags.PANEL_LIST, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("the heading stands at the list's top", list.top.value, heading.top.value, 1f)
    }

    @Test
    fun enteredFromItsRowTheListRestsAtTheTop() {
        show(rowFocus)
        press(Action.Move(Direction.Right))
        assertEquals(1, model.focus.panelIndex)
        assertRestsAtTheTop()
    }

    @Test
    fun openedOnTheFirstLineTheListRestsAtTheTop() {
        show(rowFocus.copy(column = SettingsColumn.Panel, page = PanelPage.FilesNotAdded, panelIndex = 1))
        assertRestsAtTheTop()
    }

    @Test
    fun backOnTheFirstLineTheHeadingShowsAgain() {
        show(rowFocus)
        press(Action.Move(Direction.Right))
        repeat(FOLDERS - 1) { press(Action.Move(Direction.Down)) }
        repeat(FOLDERS - 1) { press(Action.Move(Direction.Up)) }
        assertEquals(1, model.focus.panelIndex)
        assertRestsAtTheTop()
    }

    private companion object {
        const val FOLDERS = 8
    }
}
