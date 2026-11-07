package com.plumemu.settings.screen

import androidx.compose.runtime.remember
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Systems' list leaves no blank under its header: at every rest a row stands at the list's top, System order or a system. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsSystemsNoBlankTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val settings = PlumSettings(flags = SetupFlags(setupCompleted = true))
    private lateinit var model: SettingsModel
    private lateinit var geometry: SettingsGeometry

    private fun open(systems: Int) {
        val content = SettingsContent(librarySystems = SYSTEMS.take(systems))
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                geometry = settingsGeometry(PlumTheme.layout)
                model = remember { SettingsModel(NoActions, optionsAt(SettingsOption.RomFolders).copy(rememberedOption = mapOf(SettingsSection.Library to SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.Systems)))).also { it.content = content } }
                SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { model.onAction(Action.Enter, settings, geometry) }
        compose.waitForIdle()
    }

    private fun press(direction: Direction) {
        compose.runOnIdle { model.onAction(Action.Move(direction), settings, geometry) }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    private fun listTop(): Dp = compose.onNodeWithTag(SettingsTags.SYSTEMS_LIST).getUnclippedBoundsInRoot().top

    private fun assertNoBlank(where: String) {
        val rows = listOf(SettingsTags.option(SettingsOption.SystemOrder)) + (0 until compose.onAllNodesWithTag(SettingsTags.SYSTEM_NAME, useUnmergedTree = true).fetchSemanticsNodes().size).map { SettingsTags.panelItem(it) }
        val spans = rows.map { compose.onNodeWithTag(it, useUnmergedTree = true).getUnclippedBoundsInRoot() }
        val top = listTop()
        val covered = spans.any { it.bottom >= top && it.top <= top + GLIDE_ROOM }
        assertTrue("$where: no row at the list's top ${top}: ${spans.map { it.top }}", covered)
    }

    @Test
    fun walkingDownAndUpALongListLeavesNoBlank() {
        open(SYSTEMS.size)
        assertNoBlank("opened")
        repeat(SYSTEMS.size) {
            press(Direction.Down)
            assertNoBlank("down $it")
        }
        repeat(SYSTEMS.size + 1) {
            press(Direction.Up)
            assertNoBlank("up $it")
        }
    }

    @Test
    fun aListJustTooLongForTheWindowLeavesNoBlank() {
        open(6)
        repeat(6) {
            press(Direction.Down)
            assertNoBlank("down $it")
        }
    }

    @Test
    fun aFingerThatLiftsLeavesNoBlank() {
        open(SYSTEMS.size)
        compose.onAllNodesWithTag(SettingsTags.SYSTEMS_LIST)[0].performTouchInput { swipeUp(startY = centerY, endY = centerY - 40f, durationMillis = 400) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertNoBlank("after the finger")
    }

    private companion object {
        val SYSTEMS = List(16) { LibrarySystem("system$it", "System $it", gameCount = 10 + it) }

        /** The list's own room above its first row. */
        val GLIDE_ROOM = com.plumemu.coreui.theme.SlabDimens.listEndRoom
    }
}
