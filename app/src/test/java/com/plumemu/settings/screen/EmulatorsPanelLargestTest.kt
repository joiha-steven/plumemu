package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.account.AccountStatus
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Emulators, a system's panel: at Largest the title, body and line above the list must
 * not take all the room. The list keeps at least its first row whole (the rest scrolls), .
 */
abstract class EmulatorsPanelLargestBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theListKeepsAWholeFirstRowAtLargest() {
        val psx = SystemEmulators("psx", "PlayStation", automatic = null, chosen = null, installed = emptyList())
        val duck = EmulatorEntry("duckstation", "DuckStation", isLegalRisk = false)
        val system = psx.copy(installed = listOf(duck, EmulatorEntry("retroarch-swanstation", "SwanStation", isLegalRisk = false), EmulatorEntry("retroarch-mednafen-psx", "Beetle PSX", isLegalRisk = false)))
        val content = ShotContent.library.copy(systems = listOf(system))
        val focus = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.Emulators, rememberedOption = mapOf(SettingsSection.Emulators to 0), page = PanelPage.Emulators, panelIndex = 0, systemId = "psx")
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) { SettingsAt(focus, content = content) } }
        val failures = mutableListOf<String>()
        for (each in listOf(PlumFont.Jost)) {
            compose.waitForIdle()
            val list = compose.onNodeWithTag(SettingsTags.PANEL_LIST, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val row = compose.onNodeWithTag(SettingsTags.panelItem(0), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            if (list.height < 1f) failures += "$each: the list has no height ($list)"
            if (row.height < 1f || row.top < list.top - 1f || row.bottom > list.bottom + 1f) failures += "$each: the first row is not whole (row $row, list $list)"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The same for the other panels built as a header over a list: Language, Game folders and Box art source. */
    @Test
    fun otherListPanelsKeepAWholeFirstRowAtLargest() {
        val account = AccountShotContent.of(AccountStatus.SignedOut)
        val cases =
            listOf(
                Triple("Language", panelAt(SettingsOption.Language, PanelPage.Language, 0), ShotContent.library),
                Triple("Game folders", panelAt(SettingsOption.RomFolders, PanelPage.Folders, 0), ShotContent.library),
                Triple("Box art source", AccountShotContent.focusOn(account, SettingsOption.BoxArtSource, PanelPage.BoxArtSource), account),
            )
        var case by mutableStateOf(cases.first())
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) { SettingsAt(case.second, content = case.third) } }
        val failures = mutableListOf<String>()
        for (each in cases) {
            case = each
            for (eachFont in listOf(PlumFont.Jost)) {
                compose.waitForIdle()
                val list = compose.onNodeWithTag(SettingsTags.PANEL_LIST, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val row = compose.onNodeWithTag(SettingsTags.panelItem(0), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                if (list.height < 1f) failures += "${each.first} $eachFont: the list has no height ($list)"
                if (row.height < 1f || row.top < list.top - 1f || row.bottom > list.bottom + 1f) failures += "${each.first} $eachFont: the first row is not whole (row $row, list $list)"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class EmulatorsPanelLargestNovaTest : EmulatorsPanelLargestBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class EmulatorsPanelLargest640Test : EmulatorsPanelLargestBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class EmulatorsPanelLargestRp4ProTest : EmulatorsPanelLargestBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class EmulatorsPanelLargestRp5Test : EmulatorsPanelLargestBase()
