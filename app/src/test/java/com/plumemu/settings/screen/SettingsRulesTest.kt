package com.plumemu.settings.screen

import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Settings' pure rules: what Reset all keeps, hex codes, moves inside a panel. */
class SettingsRulesTest {
    @Test
    fun resetAllKeepsBothFlags() {
        val before = PlumSettings(appearance = AppearanceSettings(textColor = TextColorSetting.Dark), flags = SetupFlags(setupCompleted = true, homeRoleAsked = true))
        val after = before.resetKeepingFlags()
        assertEquals(PlumSettings(appearance = AppearanceSettings(buttonSet = AppearanceSettings.BUTTONS_NONE), flags = before.flags), after)
    }

    @Test
    fun resetAllPutsEverySystemBackToAList() {
        val before = PlumSettings(appearance = AppearanceSettings(gridSystems = setOf("snes", "psp")), flags = SetupFlags(setupCompleted = true))
        assertEquals(emptySet<String>(), before.resetKeepingFlags().appearance.gridSystems)
    }

    @Test
    fun hexCodesAreReadWithOrWithoutTheHash() {
        assertEquals(0x1A2B3C, parseHexColor(" 1a2b3c ")?.value)
        assertEquals(0x1A2B3C, parseHexColor("#1A2B3C")?.value)
        assertNull(parseHexColor("1A2B3"))
        assertNull(parseHexColor("GGGGGG"))
        assertNull(parseHexColor(""))
    }

    @Test
    fun panelGridMovesStayInsideAndReachTheHexRow() {
        val presets = PanelGrid(count = 9, columns = 5)
        assertEquals(8, presets.move(4, Direction.Down))
        assertEquals(3, presets.move(8, Direction.Up))
        assertEquals(4, presets.move(4, Direction.Right))
        assertEquals(5, presets.move(5, Direction.Left))
        val custom = PanelGrid(count = 49, columns = 8)
        assertEquals(48, custom.move(43, Direction.Down))
        assertEquals(40, custom.move(48, Direction.Up))
        assertEquals(48, custom.move(48, Direction.Right))
        assertEquals(0, custom.move(0, Direction.Up))
    }
}
