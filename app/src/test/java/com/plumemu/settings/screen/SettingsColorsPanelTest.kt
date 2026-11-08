package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.OwnColors
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.settings.colorSet
import com.plumemu.settings.withColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Settings › Appearance › Colors' preview panel: A opens it, the D-pad walks the tiles, A uses one. */
internal class SettingsColorsPanelTest : SettingsModelHarness() {
    private val own = AppearanceSettings(background = ColorSetting.Custom(0x2C1111), textColor = TextColorSetting.Light, accent = ColorSetting.Custom(0x123456))

    private fun onColors(start: AppearanceSettings) {
        actions.settings = PlumSettings(appearance = start, flags = SetupFlags(setupCompleted = true))
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Colors)
    }

    private fun assertOnTheRow() {
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull(model.focus.page)
        assertEquals(SettingsOption.Colors, model.focusedOption)
    }

    @Test
    fun aOpensThePanelAtTheCurrentSetsTile() {
        for ((start, at) in listOf(PlumSettings.Defaults.appearance to 0, ColorSet.Ivory.appearance() to ColorSet.Ivory.ordinal, own to ColorSet.entries.size)) {
            onColors(start)
            press(Action.Enter)
            assertEquals(SettingsColumn.Panel, model.focus.column)
            assertEquals(PanelPage.ColorSets, model.focus.page)
            assertEquals("$start", at, model.focus.panelIndex)
            press(Action.Back)
            press(Action.Back) // to the sections, for the next start
        }
    }

    @Test
    fun theDPadMovesOverTheGridAndAppliesNothing() {
        onColors(PlumSettings.Defaults.appearance)
        val before = actions.settings
        val writes = actions.writes
        press(Action.Enter)
        move(Direction.Right)
        assertEquals(1, model.focus.panelIndex)
        move(Direction.Down)
        assertEquals("three tiles per row in the wide panel", 1 + COLOR_SET_COLUMNS_WIDE, model.focus.panelIndex)
        move(Direction.Left)
        assertEquals("left goes to the row's first tile", COLOR_SET_COLUMNS_WIDE, model.focus.panelIndex)
        val lastRow = (ColorSet.entries.size - 1) / COLOR_SET_COLUMNS_WIDE
        repeat(10) { if (model.focus.panelIndex / COLOR_SET_COLUMNS_WIDE < lastRow) move(Direction.Down) }
        assertEquals("down reaches the last row of tiles (no Custom here)", lastRow, model.focus.panelIndex / COLOR_SET_COLUMNS_WIDE)
        move(Direction.Down)
        assertEquals("down from the last row goes to the Background row", COLORS_BACKGROUND_ITEM, model.focus.panelIndex)
        assertEquals("moving only previews: nothing is written", writes, actions.writes)
        assertEquals(before, actions.settings)
    }

    @Test
    fun aUsesTheSetAndGoesBackToTheRow() {
        onColors(PlumSettings.Defaults.appearance)
        pickInColorsPanel(ColorSet.Ivory)
        assertEquals(ColorSet.Ivory, appearance.colorSet())
        assertOnTheRow()
        // Left / right on the row still step from there, like before.
        move(Direction.Right)
        assertEquals(ColorSet.ClearGrape, appearance.colorSet())
    }

    /** The same write as the row: own colours are kept exactly as stepping away from Custom keeps them. */
    @Test
    fun aKeepsTheOwnColoursExactlyLikeTheRow() {
        onColors(own)
        pickInColorsPanel(ColorSet.Ivory)
        assertEquals(own.withColors(ColorSet.Ivory), appearance)
        assertEquals(OwnColors(own.background, own.textColor, own.accent, buttonSet = "None"), appearance.ownColors)
        // Custom's tile is still there (own colours kept) and brings all three back.
        pickInColorsPanel(null)
        assertEquals(own, appearance.copy(ownColors = null, buttonSet = null))
    }

    @Test
    fun customHasATileOnlyWhileThereIsACustomLook() {
        val onPaper = PlumSettings(appearance = ColorSet.Daylight.appearance())
        assertEquals("no own colours: the ten sets", ColorSet.entries.toList(), colorSetsPanelEntries(onPaper))
        assertEquals("current colours match no set", ColorSet.entries + null, colorSetsPanelEntries(PlumSettings(appearance = own)))
        val kept = ColorSet.Daylight.appearance().copy(ownColors = OwnColors(own.background, own.textColor, own.accent))
        assertEquals("own colours kept", ColorSet.entries + null, colorSetsPanelEntries(PlumSettings(appearance = kept)))
        // Custom's tile shows the colours Custom stands for: the kept ones here, not Paper's.
        val look = tileLookOf(null, PlumSettings(appearance = kept))
        assertEquals(0x2C1111, look.tokens.bg.value)
        assertNull("Custom keeps the usual glyphs", look.buttons)
        assertEquals("a set's tile has its glyph colours", ColorSet.Ivory.buttons, tileLookOf(ColorSet.Ivory, onPaper).buttons)
    }

    @Test
    fun bGoesBackToTheRowWithoutChangingAnything() {
        onColors(PlumSettings.Defaults.appearance)
        val before = actions.settings
        press(Action.Enter)
        move(Direction.Right)
        move(Direction.Down)
        press(Action.Back)
        assertOnTheRow()
        assertEquals(before, actions.settings)
    }

    @Test
    fun touchATapOnTheValueOpensThePanelATapFocusesASecondUses() {
        onColors(PlumSettings.Defaults.appearance)
        model.onTapValue(SettingsSection.Appearance.options.indexOf(SettingsOption.Colors), actions.shown, wide)
        assertEquals(PanelPage.ColorSets, model.focus.page)
        press(Action.Back)
        assertOnTheRow()
        // Wide: the panel shows beside the row; a tap on a tile enters it and focuses that tile.
        model.onTapPanelItem(ColorSet.PeaGreen.ordinal, actions.shown, wide)
        assertEquals(PanelPage.ColorSets, model.focus.page)
        assertEquals(ColorSet.PeaGreen.ordinal, model.focus.panelIndex)
        assertEquals("the first tap only focuses", ColorSet.Plumemu, appearance.colorSet())
        model.onTapPanelItem(ColorSet.PeaGreen.ordinal, actions.shown, wide)
        assertEquals(ColorSet.PeaGreen, appearance.colorSet())
        assertOnTheRow()
    }

    /** The panel opens on the current set's tile: a first tap there only enters the panel, like any tile. */
    @Test
    fun touchAFirstTapOnTheCurrentTileOnlyOpensThePanel() {
        onColors(ColorSet.Ivory.appearance())
        val writes = actions.writes
        model.onTapPanelItem(ColorSet.Ivory.ordinal, actions.shown, wide)
        assertEquals(SettingsColumn.Panel, model.focus.column)
        assertEquals(PanelPage.ColorSets, model.focus.page)
        assertEquals(ColorSet.Ivory.ordinal, model.focus.panelIndex)
        assertEquals("nothing used yet", writes, actions.writes)
        model.onTapPanelItem(ColorSet.Ivory.ordinal, actions.shown, wide)
        assertOnTheRow()
        assertEquals(ColorSet.Ivory, appearance.colorSet())
    }

    @Test
    fun narrowThePanelIsAStopWithFiveTilesARow() {
        val narrowTiles = narrow.copy(colorSetColumns = 5)
        onColors(PlumSettings.Defaults.appearance)
        press(Action.Enter, narrowTiles)
        assertEquals(PanelPage.ColorSets, model.focus.page)
        move(Direction.Down, narrowTiles)
        assertEquals(5, model.focus.panelIndex)
        press(Action.Back, narrowTiles)
        assertOnTheRow()
        pickInColorsPanel(ColorSet.RedAndGold, narrowTiles)
        assertEquals(ColorSet.RedAndGold, appearance.colorSet())
    }

    @Test
    fun theHintSaysChooseInThePanel() {
        onColors(PlumSettings.Defaults.appearance)
        press(Action.Enter)
        assertEquals(R.string.settings_hint_choose, confirmHint(model))
    }

    private fun ColorSet.appearance(): AppearanceSettings = PlumSettings.Defaults.appearance.withColors(this)
}
