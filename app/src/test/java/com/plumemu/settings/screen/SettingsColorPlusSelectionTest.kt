package com.plumemu.settings.screen

import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "+" page's selected check marks the colour in effect when the page opened (then the one chosen),
 * never the one the focus ring previews; and the grid the cursor walks is the one fixed when the page
 * opened, also while the store answers late. Walking a grid whose last row is short is checked on the
 * pure move.
 */
internal class SettingsColorPlusSelectionTest : SettingsModelHarness() {
    private fun openBackgroundPlusOn(tile: Rgb) {
        actions.settings = actions.settings.copy(appearance = appearance.copy(background = ColorSetting.Custom(tile.value)))
        model.forgetColorWrites()
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        assertEquals(PanelPage.BackgroundCustom, model.focus.page)
    }

    @Test
    fun theCheckStaysOnTheOpeningTileWhileTheRingMovesAndMovesOnA() {
        val opening = backgroundGrid[5]
        openBackgroundPlusOn(opening)
        assertEquals("the page opens on its tile", 5, model.focus.panelIndex)
        assertEquals(opening, model.selectedColour)
        move(Direction.Right)
        move(Direction.Right)
        assertEquals(7, model.focus.panelIndex)
        assertEquals("the previewed colour is on screen", ColorSetting.Custom(backgroundGrid[7].value), appearance.background)
        assertEquals("the check did not follow the ring", opening, model.selectedColour)
        press(Action.Enter)
        assertEquals("A chose the tile under the ring: the check moves there", backgroundGrid[7], model.selectedColour)
    }

    @Test
    fun aTapOnATileMovesTheCheckToIt() {
        val opening = backgroundGrid[5]
        openBackgroundPlusOn(opening)
        model.onTapPanelItem(2, actions.shown, wide)
        assertEquals(backgroundGrid[2], model.selectedColour)
    }

    @Test
    fun aShortLastRowIsWalkedToItsEndAndBackToTheSliders() {
        val swatches = 19 // rows of 8, 8 and 3
        val hue = swatches
        fun next(
            from: Int,
            direction: Direction,
        ) = nextCustomIndex(swatches, from, direction)
        assertEquals("Down from the right of row 2 lands on the short row's last tile", 18, next(15, Direction.Down))
        assertEquals("Down from column 2 lands under it", 18, next(10, Direction.Down))
        assertEquals("Down from column 0 lands under it", 16, next(8, Direction.Down))
        assertEquals("Down from the short row goes to Hue", hue, next(17, Direction.Down))
        assertEquals("Up from Hue lands on the short row's first tile", 16, next(hue, Direction.Up))
        assertEquals("Right at the end of the short row stays", 18, next(18, Direction.Right))
        assertEquals("Left on column 0 stays (the model leaves the page there)", 16, next(16, Direction.Left))
    }

    @Test
    fun leftFromColumnZeroOfAShortLastRowLeavesThePage() {
        val paper = BackgroundPreset.Paper.color
        actions.settings = actions.settings.copy(appearance = appearance.copy(background = ColorSetting.Preset("Paper"), textColor = TextColorSetting.Dark))
        model.forgetColorWrites()
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.TextColor)
        press(Action.Enter)
        val plus = ColorPages.presetCount(PanelPage.TextPresets, paper) - 1
        repeat(2) { model.onTapPanelItem(plus, actions.shown, wide) }
        assertEquals(PanelPage.TextCustom, model.focus.page)
        val tiles = model.customPalette(PanelPage.TextCustom).size
        assertTrue("Text on Paper has a short last row for this test to mean something ($tiles tiles)", tiles % ColorPages.PALETTE_COLUMNS != 0)
        val lastRowStart = (tiles - 1) / ColorPages.PALETTE_COLUMNS * ColorPages.PALETTE_COLUMNS
        while (model.focus.panelIndex < lastRowStart) move(Direction.Down)
        assertEquals(lastRowStart, model.focus.panelIndex)
        move(Direction.Left)
        assertNotEquals("Left from column 0 left the page", PanelPage.TextCustom, model.focus.page)
    }

    @Test
    fun theTextPageWalksTheGridFixedAtOpeningWhileTheStoreStillAnswersWithTheOldBackground() {
        val paper = BackgroundPreset.Paper.color
        val graphite = BackgroundPreset.Graphite.color
        val onPaper = ColorPages.palette(PanelPage.TextCustom, paper)
        assertNotEquals("the two backgrounds give different grids, so the test means something", onPaper.size, ColorPages.palette(PanelPage.TextCustom, graphite).size)
        // A background write the store has not applied yet: the stored settings still say Graphite.
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        model.onTapPanelItem(BackgroundPreset.entries.indexOf(BackgroundPreset.Paper), actions.shown, wide) // queued: actions.shown is still Graphite
        assertEquals("the store has not answered yet", ColorSetting.Preset("Graphite"), actions.shown.appearance.background)
        press(Action.Back)
        focusOption(SettingsOption.TextColor)
        press(Action.Enter)
        assertEquals(PanelPage.TextPresets, model.focus.page)
        val plus = ColorPages.presetCount(PanelPage.TextPresets, paper) - 1
        repeat(2) { model.onTapPanelItem(plus, actions.shown, wide) }
        assertEquals(PanelPage.TextCustom, model.focus.page)
        assertEquals("the page draws and walks the opening background's grid", onPaper, model.customPalette(PanelPage.TextCustom))
        repeat(onPaper.size / ColorPages.PALETTE_COLUMNS + ColorSlider.entries.size + 2) { move(Direction.Down) }
        assertEquals("Down ends on the hex row of that grid", onPaper.size + ColorSlider.entries.size, model.focus.panelIndex)
    }
}
