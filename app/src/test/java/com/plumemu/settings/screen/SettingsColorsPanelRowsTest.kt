package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.settings.colorSet
import com.plumemu.settings.withColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Colors' panel: the Background and Text color rows under the tiles, by key and by touch. A page
 * opens from its row and comes back to it; every colour rule of the pages still holds there.
 */
internal class SettingsColorsPanelRowsTest : SettingsModelHarness() {
    private fun start(appearance: AppearanceSettings = PlumSettings.Defaults.appearance) {
        actions.settings = PlumSettings(appearance = appearance, flags = SetupFlags(setupCompleted = true))
        openSection(SettingsSection.Appearance)
    }

    private fun assertOnRow(item: Int) {
        assertEquals(PanelPage.ColorSets, model.focus.page)
        assertEquals(item, model.focus.panelIndex)
        assertEquals("the list stays on Colors", SettingsOption.Colors, model.focusedOption)
    }

    private fun assertBackOnTheColorsRow() {
        press(Action.Back)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull(model.focus.page)
        assertEquals(SettingsOption.Colors, model.focusedOption)
    }

    @Test
    fun appearanceListsNeitherRow() {
        val rows = SettingsSection.Appearance.options
        assertFalse(SettingsOption.Background in rows)
        assertFalse(SettingsOption.TextColor in rows)
        assertTrue(SettingsOption.Accent in rows)
    }

    @Test
    fun leftAndRightOnTheColorsRowStillStepTheSets() {
        start()
        focusOption(SettingsOption.Colors)
        move(Direction.Right)
        assertEquals(ColorSet.entries[1], appearance.colorSet())
        assertEquals(SettingsColumn.Options, model.focus.column)
        move(Direction.Left)
        assertEquals(ColorSet.entries[0], appearance.colorSet())
    }

    @Test
    fun theDPadWalksFromTheTilesToTheRowsAndBack() {
        start()
        focusOption(SettingsOption.Colors)
        press(Action.Enter)
        val tiles = colorSetsPanelEntries(actions.settings).size
        repeat(tiles) { if (model.focus.panelIndex < tiles) move(Direction.Down) }
        assertOnRow(COLORS_BACKGROUND_ITEM)
        move(Direction.Down)
        assertOnRow(COLORS_TEXT_ITEM)
        move(Direction.Down)
        assertOnRow(COLORS_TEXT_ITEM)
        move(Direction.Up)
        assertOnRow(COLORS_BACKGROUND_ITEM)
        move(Direction.Up)
        assertEquals("up to the last tile", tiles - 1, model.focus.panelIndex)
    }

    @Test
    fun byKeyAColourChangesAndBackLandsOnTheSameRow() {
        for ((option, item) in listOf(SettingsOption.Background to COLORS_BACKGROUND_ITEM, SettingsOption.TextColor to COLORS_TEXT_ITEM)) {
            start()
            focusOption(option)
            val before = appearance
            press(Action.Enter)
            assertEquals(option, model.panelOption)
            assertEquals(ColorPages.kindOf(option)?.let(ColorPages::presetsOf), model.focus.page)
            move(Direction.Right)
            assertTrue("$option: the colour under the cursor applies", appearance != before)
            press(Action.Back)
            assertOnRow(item)
            assertEquals(SettingsOption.Colors, model.panelOption)
            assertBackOnTheColorsRow()
            press(Action.Back) // to the sections, for the next option
        }
    }

    @Test
    fun rightOpensARowAndLeftLeavesThePanel() {
        start()
        focusOption(SettingsOption.Background)
        move(Direction.Right)
        assertEquals(PanelPage.BackgroundPresets, model.focus.page)
        press(Action.Back)
        move(Direction.Left)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(SettingsOption.Colors, model.focusedOption)
    }

    @Test
    fun aOnASwatchReturnsToItsRowNotTheList() {
        start()
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        move(Direction.Right)
        press(Action.Enter)
        assertOnRow(COLORS_BACKGROUND_ITEM)
    }

    @Test
    fun aRowKeepsItsPlaceWhenAColourMakesCustomAppear() {
        start()
        val tiles = colorSetsPanelEntries(actions.settings).size
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        val paper = ColorPages.backgroundPresets.indexOfFirst { it.name == "Paper" }
        repeat(paper) { move(Direction.Right) }
        move(Direction.Right)
        press(Action.Back)
        assertTrue("Custom's tile came", colorSetsPanelEntries(actions.settings).size >= tiles)
        assertOnRow(COLORS_BACKGROUND_ITEM)
    }

    @Test
    fun byTouchARowOpensItsPageAndBackLandsOnIt() {
        for ((option, item) in listOf(SettingsOption.Background to COLORS_BACKGROUND_ITEM, SettingsOption.TextColor to COLORS_TEXT_ITEM)) {
            start()
            tapIntoColorPage(option)
            assertEquals(option, model.panelOption)
            val other = (0..2).first { it != model.focus.panelIndex }
            model.onTapPanelItem(other, actions.settings, wide)
            model.onTapPanelItem(other, actions.settings, wide)
            assertOnRow(item)
            assertBackOnTheColorsRow()
            press(Action.Back)
        }
    }

    @Test
    fun aFirstTapBesideTheColorsRowOnlyFocusesTheRow() {
        start()
        focusOption(SettingsOption.Colors)
        model.onTapPanelItem(COLORS_TEXT_ITEM, actions.settings, wide)
        assertOnRow(COLORS_TEXT_ITEM)
    }

    @Test
    fun theOwnColorsPickedThroughARowComeBackFromCustom() {
        val own = ColorSetting.Custom(0x2C1111)
        start(AppearanceSettings(background = own, textColor = TextColorSetting.Light, accent = ColorSetting.Custom(0x123456)))
        focusOption(SettingsOption.Colors)
        pickInColorsPanel(ColorSet.Paper)
        assertEquals(ColorSet.Paper, appearance.colorSet())
        pickInColorsPanel(null)
        assertEquals("Custom restores the own background", own, appearance.background)
        assertEquals(ColorSetting.Custom(0x123456), appearance.accent)
        assertNull(appearance.colorSet())
    }

    @Test
    fun aRowsColourNeverMovesTheButtonSet() {
        start(PlumSettings.Defaults.appearance.withColors(ColorSet.Super))
        val buttons = appearance.buttonSet
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        move(Direction.Right)
        assertNull("the look is no set any more", appearance.colorSet())
        assertEquals("a colour never moves the button set", buttons, appearance.buttonSet)
    }

    @Test
    fun aBlockedTextColorSaysWhyAndOffersNothing() {
        val blocked = requireNotNull(generateSequence(0x000000) { it + 0x010101 }.take(256).map { com.plumemu.coreui.theme.Rgb(it) }.firstOrNull { ColorPages.textBlockedOn(it) })
        start(AppearanceSettings(background = ColorSetting.Custom(blocked.value)))
        focusOption(SettingsOption.TextColor)
        press(Action.Enter)
        assertEquals(PanelPage.TextPresets, model.focus.page)
        assertTrue(model.isTextBlocked)
        assertNull("A has nothing to choose there", confirmHint(model))
        val before = appearance
        press(Action.Enter)
        assertEquals("A stays on the page", PanelPage.TextPresets, model.focus.page)
        assertEquals(before, appearance)
        press(Action.Back)
        assertOnRow(COLORS_TEXT_ITEM)
    }

    @Test
    fun hintsSayWhatAAndBDoOnEachPage() {
        start()
        focusOption(SettingsOption.Colors)
        assertEquals(R.string.settings_hint_change, confirmHint(model))
        press(Action.Enter)
        assertEquals("a tile is chosen", R.string.settings_hint_choose, confirmHint(model))
        press(Action.Back)
        focusOption(SettingsOption.Background)
        assertEquals("a row changes a colour", R.string.settings_hint_change, confirmHint(model))
        press(Action.Enter)
        assertEquals("a swatch is chosen", R.string.settings_hint_choose, confirmHint(model))
    }
}
