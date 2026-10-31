package com.plumemu.settings.screen

import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.CustomPalette
import com.plumemu.coreui.theme.TextMode
import com.plumemu.R
import org.junit.Assert.assertEquals
import com.plumemu.coreui.theme.AccentChoice
import com.plumemu.coreui.theme.AccentPreset
import com.plumemu.coreui.theme.ColorRole
import com.plumemu.coreui.theme.Readability
import com.plumemu.coreui.theme.Rgb
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The colour pages of Settings: presets, the custom palette's preview and choice, the hex code field. */
internal class SettingsColorPagesTest : SettingsModelHarness() {
    @Test
    fun backgroundPresetsApplyAsTheCursorReachesThem() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background) // under Colors
        press(Action.Enter)
        assertEquals(PanelPage.BackgroundPresets, model.focus.page)
        assertEquals("opens at the current colour (Graphite)", 0, model.focus.panelIndex)
        move(Direction.Right)
        assertEquals(ColorSetting.Preset("Black"), appearance.background)
        move(Direction.Down) // 5 per row: under Black, Sand
        assertEquals(ColorSetting.Preset("Sand"), appearance.background)
        press(Action.Enter)
        assertEquals("A chooses and goes back to the options", SettingsColumn.Options, model.focus.column)
        assertEquals(ColorSetting.Preset("Sand"), appearance.background)
        press(Action.Enter)
        assertEquals("opens again at the current one", 6, model.focus.panelIndex)
    }

    @Test
    fun plusOpensTheCustomPaletteOnTheNearestSwatchNotTheHexRowWithoutChangingTheColour() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background) // under Colors
        press(Action.Enter)
        openPlus()
        assertEquals(PanelPage.BackgroundCustom, model.focus.page)
        val nearest = ColorPages.indexOfNearest(backgroundGrid, BackgroundPreset.Graphite.color.value)
        assertEquals("Graphite is not in the grid: its nearest swatch, not the hex row", nearest, model.focus.panelIndex)
        assertTrue(model.focus.panelIndex < backgroundGrid.size)
        assertEquals("opening keeps the colour", ColorSetting.Preset("Graphite"), appearance.background)
    }

    @Test
    fun walkingThePaletteIsAPreviewThatBackPutsBack() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background) // under Colors
        press(Action.Enter)
        openPlus() // the palette, on a swatch
        toHexRow()
        upToGrid()
        assertEquals(ColorSetting.Custom(backgroundGrid[backgroundGrid.size - ColorPages.PALETTE_COLUMNS].value), appearance.background)
        press(Action.Back)
        assertEquals("back to the presets", PanelPage.BackgroundPresets, model.focus.page)
        assertEquals("the colour the page opened with comes back", ColorSetting.Preset("Graphite"), appearance.background)

        press(Action.Enter)
        move(Direction.Up)
        toHexRow()
        // The walk down passes the sliders, which keep the colour shown; only B puts the opening one back.
        press(Action.Back)
        press(Action.Back)
        assertEquals("B puts the opening colour back", ColorSetting.Preset("Graphite"), appearance.background)
    }

    @Test
    fun aChoosesASwatchOfThePalette() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background) // under Colors
        press(Action.Enter)
        openPlus()
        toHexRow()
        upToGrid()
        move(Direction.Up)
        move(Direction.Right)
        move(Direction.Right)
        press(Action.Enter)
        assertEquals(SettingsColumn.Options, model.focus.column)
        val chosen = backgroundGrid.size - 2 * ColorPages.PALETTE_COLUMNS + 2
        assertEquals(ColorSetting.Custom(backgroundGrid[chosen].value), appearance.background)
        press(Action.Enter)
        assertEquals("a custom colour opens on the palette, at it", PanelPage.BackgroundCustom, model.focus.page)
        assertEquals(chosen, model.focus.panelIndex)
    }

    @Test
    fun tappingASwatchWhileTypingLeavesTheField() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Accent)
        press(Action.Enter)
        repeat(4) { move(Direction.Right) }
        move(Direction.Down)
        press(Action.Enter) // "+": the accent palette, on a swatch
        toHexRow()
        press(Action.Enter)
        assertTrue(model.isEditingHex)
        model.onTapPanelItem(3, actions.shown, wide)
        assertFalse(model.isEditingHex)
        assertEquals(3, model.focus.panelIndex)
        val cell = PaletteCuration.curated(CustomPalette.accentSwatchesOn(BackgroundPreset.Graphite.color))[3]
        assertEquals("a tapped swatch is chosen", ColorSetting.Custom(cell.value), appearance.accent)
        press(Action.Back)
        assertEquals("and kept when the page is left", ColorSetting.Custom(cell.value), appearance.accent)
    }

    /** Typed accents are dark codes (#1A2B3C …): they read on Paper, not on the Graphite these tests start on (accents under 3:1 are refused). */
    private fun useDarkOnPaper() {
        actions.settings = actions.settings.copy(appearance = appearance.copy(background = ColorSetting.Preset("Paper"), textColor = TextColorSetting.Dark))
    }

    private fun openAccentHexRow() {
        useDarkOnPaper()
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Accent)
        press(Action.Enter)
        repeat(6) { move(Direction.Right) }
        move(Direction.Down)
        press(Action.Enter) // + → the accent palette, on a swatch
        toHexRow()
    }

    @Test
    fun withOnlyTheRouterADownAndAUsesTheTypedColourOnce() {
        // Android's keyboard (A on the field asks for it again, Down leaves the field); Plumemu's: SettingsHexOwnKeyboardTest.
        useAndroidKeyboard()
        useDarkOnPaper()
        openAccentHexRow()
        assertEquals("on the hex row A starts typing", R.string.account_hint_type, confirmHint(model))
        press(Action.Enter)
        assertTrue(model.isEditingHex)
        val beforeTyping = appearance.accent // the swatch the walk down previewed: the sliders keep it
        model.onHexTextChange("1A2B3C")
        press(Action.Enter)
        assertTrue("A on the field only brings the keyboard back", model.isEditingHex)
        assertEquals("nothing applied yet", beforeTyping, appearance.accent)
        assertEquals(R.string.account_hint_type, confirmHint(model))
        move(Direction.Down)
        assertTrue(model.hexOnUse)
        assertEquals(R.string.settings_hint_choose, confirmHint(model))
        press(Action.Enter)
        assertEquals(ColorSetting.Custom(0x1A2B3C), appearance.accent)
        assertFalse(model.isEditingHex)
        assertFalse(model.hexOnUse)
    }

    @Test
    fun upFromUseGoesBackToTheFieldAndAWrongCodeOnUseIsRefused() {
        // Android's keyboard (A on the field asks for it again, Down leaves the field); Plumemu's: SettingsHexOwnKeyboardTest.
        useAndroidKeyboard()
        useDarkOnPaper()
        openAccentHexRow()
        press(Action.Enter)
        model.onHexTextChange("zz")
        move(Direction.Down)
        press(Action.Enter)
        assertTrue("a wrong code is refused", model.hexError)
        assertTrue("and typing goes on", model.isEditingHex)
        val asks = model.hexKeyboardAsks
        move(Direction.Up)
        assertFalse(model.hexOnUse)
        assertEquals("Up opens nothing by itself", asks, model.hexKeyboardAsks)
    }

    @Test
    fun aTypedHexCodeIsUsedAndAWrongOneIsRefused() {
        // Android's keyboard (A on the field asks for it again, Down leaves the field); Plumemu's: SettingsHexOwnKeyboardTest.
        useAndroidKeyboard()
        useDarkOnPaper()
        val onPaper = PaletteCuration.curated(CustomPalette.accentSwatchesOn(BackgroundPreset.Paper.color)).size
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Accent)
        press(Action.Enter)
        assertEquals(PanelPage.AccentPresets, model.focus.page)
        assertEquals(0, model.focus.panelIndex)
        repeat(6) { move(Direction.Right) }
        move(Direction.Down)
        press(Action.Enter) // + → the accent palette, at the hex row (Green is a preset)
        assertEquals(PanelPage.AccentCustom, model.focus.page)
        assertTrue("opens on a swatch, not the hex row", model.focus.panelIndex < onPaper)
        toHexRow()
        assertEquals(onPaper + ColorSlider.entries.size, model.focus.panelIndex)
        press(Action.Enter)
        assertTrue(model.isEditingHex)

        model.onHexEntered("zz12")
        assertTrue(model.hexError)
        assertTrue("still editing", model.isEditingHex)
        model.onHexEntered("#1a2B3c")
        assertFalse(model.isEditingHex)
        assertEquals(ColorSetting.Custom(0x1A2B3C), appearance.accent)

        press(Action.Enter)
        assertTrue(model.isEditingHex)
        assertEquals("the field starts empty (the current code is its hint)", "", model.hexText)
        model.onHexTextChange("0a0b0c")
        val asks = model.hexKeyboardAsks
        press(Action.Enter)
        assertEquals("A brings the keyboard back, it does not use the code", asks + 1, model.hexKeyboardAsks)
        assertTrue(model.isEditingHex)
        model.onHexEntered(model.hexText)
        assertEquals("the keyboard's Done uses the typed code", ColorSetting.Custom(0x0A0B0C), appearance.accent)
        assertFalse(model.isEditingHex)

        press(Action.Enter)
        assertTrue(model.isEditingHex)
        press(Action.Move(Direction.Up))
        assertEquals("moves are ignored while typing", onPaper + ColorSlider.entries.size, model.focus.panelIndex)
        press(Action.Back)
        assertFalse("Back leaves the field first", model.isEditingHex)
        assertEquals(PanelPage.AccentCustom, model.focus.page)
    }

    @Test
    fun narrowColourPanelUsesTenPerRow() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background) // under Colors
        press(Action.Enter, narrow)
        move(Direction.Down, narrow)
        assertEquals("ten per row: down goes to the second row", 10, model.focus.panelIndex)
        move(Direction.Up, narrow)
        repeat(12) { move(Direction.Right, narrow) }
        assertEquals("right stops at the end of the row of ten", 9, model.focus.panelIndex)
    }

    @Test
    fun aTapOnASwatchBesideTheOptionsAppliesItAndASecondTapChooses() {
        model.onTapSection(SettingsSection.Appearance)
        model.onTapSection(SettingsSection.Appearance)
        model.onTapOption(SettingsSection.Appearance.options.indexOf(SettingsOption.Background), actions.settings, wide) // under Colors
        model.onTapPanelItem(5, actions.settings, wide)
        assertEquals(SettingsColumn.Panel, model.focus.column)
        assertEquals(ColorSetting.Preset("Paper"), appearance.background)
        model.onTapPanelItem(5, actions.settings, wide)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(ColorSetting.Preset("Paper"), appearance.background)
    }

    @Test
    fun tappingTheHexRowPutsBackTheOpeningColourLikeTheDPad() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background) // under Colors
        press(Action.Enter)
        openPlus() // the palette, on a swatch
        toHexRow()
        upToGrid()
        assertEquals(ColorSetting.Custom(backgroundGrid[backgroundGrid.size - ColorPages.PALETTE_COLUMNS].value), appearance.background)
        model.onTapPanelItem(backgroundGrid.size + ColorSlider.entries.size, actions.shown, wide)
        assertTrue(model.isEditingHex)
        assertEquals("the colour the page opened with", ColorSetting.Preset("Graphite"), appearance.background)
    }

    @Test
    fun leavingTheCompositionOnACustomPagePutsBackTheOpeningColour() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background) // under Colors
        press(Action.Enter)
        openPlus()
        toHexRow()
        move(Direction.Up)
        model.onDispose()
        assertEquals("no preview stays stored", ColorSetting.Preset("Graphite"), appearance.background)
        model.onDispose()
        assertEquals("a second dispose writes nothing more", ColorSetting.Preset("Graphite"), appearance.background)
    }

    /** Walks the palette cursor to [target] (rows of eight), previewing each cell on the way. */
    private fun moveToCell(target: Int) {
        repeat(60) {
            val at = model.focus.panelIndex
            if (at == target) return
            val swatches = ColorPages.palette(model.focus.page!!, ColorPages.backgroundOf(actions.settings)).size
            if (at >= swatches) {
                move(Direction.Up) // from a slider or the code row back to the grid
                return@repeat
            }
            val rowDiff = target / ColorPages.PALETTE_COLUMNS - at / ColorPages.PALETTE_COLUMNS
            move(
                when {
                    rowDiff < 0 -> Direction.Up
                    rowDiff > 0 -> Direction.Down
                    target < at -> Direction.Left
                    else -> Direction.Right
                },
            )
        }
    }

    /** A grey accent: it reads on Graphite and on the dark backgrounds, but not on the light ones. */
    private val grey = Rgb(0x909090)
    private val greyMoves = backgroundGrid.indices.filter { !Readability.passes(grey, backgroundGrid[it], ColorRole.Accent) }
    private val greyReads = backgroundGrid.indices.filter { it !in greyMoves }

    private fun openPaletteWithGreyAccent() {
        actions.settings = actions.settings.copy(appearance = appearance.copy(accent = ColorSetting.Custom(grey.value)))
        openPalette()
    }

    @Test
    fun browsingToACellThatMovesTheAccentAndCancellingPutsTheOpeningAccentBack() {
        openPaletteWithGreyAccent()
        val openingBackground = appearance.background // Plum: openPalette previews presets on the way
        assertTrue("precondition: some offered background moves the grey", greyMoves.isNotEmpty())
        moveToCell(greyMoves.first())
        assertNotEquals(ColorSetting.Custom(grey.value), appearance.accent)
        assertTrue("and the person is told", model.colorWrite?.accentAdjusted == true)
        press(Action.Back)
        assertEquals(openingBackground, appearance.background)
        assertEquals("the opening accent is back", ColorSetting.Custom(grey.value), appearance.accent)
    }

    @Test
    fun browsingOnToACellWhereTheAccentReadsGivesTheOpeningAccentAgain() {
        openPaletteWithGreyAccent()
        moveToCell(greyMoves.first())
        assertNotEquals(ColorSetting.Custom(grey.value), appearance.accent)
        moveToCell(greyReads.first())
        assertEquals(ColorSetting.Custom(grey.value), appearance.accent)
        assertNull("nothing to tell once it reads again", model.colorWrite?.takeIf { it.adjusted })
    }

    @Test
    fun theAccentPaletteOffersOnlyCellsThatReadSoNoCellIsRefused() {
        openAccentHexRow() // Paper, Dark text
        val opening = ColorSetting.Preset("Green") // what the accent was when the page opened (the walk to the hex row previewed a swatch)
        val cells = PaletteCuration.curated(CustomPalette.accentSwatchesOn(BackgroundPreset.Paper.color))
        assertTrue("two or three rows of 8, got ${cells.size}", cells.size in 2 * PaletteCuration.COLUMNS..PaletteCuration.COLUMNS * PaletteCuration.ROWS)
        for (cell in cells) assertTrue("${cell.toHex()} reads on Paper", Readability.passes(cell, BackgroundPreset.Paper.color, ColorRole.Accent))
        move(Direction.Up) // off the hex row, so the cursor is on a swatch
        moveToCell(cells.size - 1)
        assertEquals(ColorSetting.Custom(cells.last().value), appearance.accent)
        press(Action.Back)
        assertEquals("leaving puts the opening accent back", opening, appearance.accent)
    }

    private fun openBackgroundPanel() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background) // under Colors
        press(Action.Enter)
    }

    /** From the presets, "+" opens the palette (on a swatch), then down to the hex row. */
    private fun openPalette() {
        openBackgroundPanel()
        openPlus()
        toHexRow()
    }

    @Test
    fun previewingALightPresetSwitchesTheTextToDarkAtOnce() {
        openBackgroundPanel()
        move(Direction.Down) // Paper
        assertEquals(ColorSetting.Preset("Paper"), appearance.background)
        assertEquals(TextColorSetting.Dark, appearance.textColor)
        move(Direction.Up) // Graphite again
        assertEquals(TextColorSetting.Light, appearance.textColor)
    }

    @Test
    fun choosingALightPresetStoresDarkText() {
        openBackgroundPanel()
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(TextColorSetting.Dark, appearance.textColor)
    }

    @Test
    fun everyPresetGetsTheTextThatContrastsMore() {
        for ((name, expected) in listOf("Sand" to TextColorSetting.Dark, "Mist" to TextColorSetting.Dark, "Plum" to TextColorSetting.Light)) {
            val index = ColorPages.backgroundPresets.indexOfFirst { it.name == name }
            model.onTapSection(SettingsSection.Appearance)
            model.onTapSection(SettingsSection.Appearance)
            model.onTapOption(SettingsSection.Appearance.options.indexOf(SettingsOption.Background), actions.settings, wide) // under Colors
            model.onTapPanelItem(index, actions.settings, wide)
            assertEquals(name, expected, appearance.textColor)
            press(Action.Back)
        }
    }

    @Test
    fun aLightPaletteColourAndAWhiteHexCodeGiveDarkTextADarkHexGivesLight() {
        openPalette()
        upToGrid()
        move(Direction.Up)
        val light = backgroundGrid.indexOfFirst { it.value > 0xE0E0E0 }
        model.onTapPanelItem(light, actions.shown, wide)
        assertEquals(TextColorSetting.Dark, appearance.textColor)
        model.onTapPanelItem(backgroundGrid.size + ColorSlider.entries.size, actions.shown, wide)
        model.onHexEntered("#FFFFFF")
        assertEquals(ColorSetting.Custom(0xFFFFFF), appearance.background)
        assertEquals(TextColorSetting.Dark, appearance.textColor)
        model.onTapPanelItem(backgroundGrid.size + ColorSlider.entries.size, actions.shown, wide)
        model.onHexEntered("#101010")
        assertEquals(TextColorSetting.Light, appearance.textColor)
    }

    @Test
    fun leavingThePaletteWithoutChoosingPutsBackTheBackgroundAndTheTextTogether() {
        // The person's own pale-green text on Plum: it reads there, but not on a light swatch.
        val pale = TextColorSetting.Custom(0xC8E6C9)
        actions.settings = actions.settings.copy(appearance = appearance.copy(background = ColorSetting.Preset("Plum"), textColor = pale))
        openBackgroundPanel() // opens at Plum
        openPlus() // the palette, on a swatch
        toHexRow()
        moveToCell(0) // the palest tile (first row is the lightest of each column) previews: the pale text moves darker so it reads
        assertTrue(appearance.background is ColorSetting.Custom)
        assertNotEquals(pale, appearance.textColor)
        press(Action.Back)
        assertEquals(ColorSetting.Preset("Plum"), appearance.background)
        assertEquals("the person's own text is back, exactly", pale, appearance.textColor)
    }

    @Test
    fun textStaysThroughAccentChangesAndALaterBackgroundThatItStillReadsOn() {
        openBackgroundPanel()
        move(Direction.Down)
        press(Action.Enter) // Paper, Dark text
        focusOption(SettingsOption.TextColor)
        press(Action.Enter)
        assertEquals(PanelPage.TextPresets, model.focus.page)
        assertEquals("so the text stays Dark", TextColorSetting.Dark, appearance.textColor)
        assertEquals("changing text leaves the background", ColorSetting.Preset("Paper"), appearance.background)
        press(Action.Back)
        focusOption(SettingsOption.Accent)
        press(Action.Enter)
        move(Direction.Right)
        assertEquals("changing accent leaves text and background", TextColorSetting.Dark, appearance.textColor)
        assertEquals(ColorSetting.Preset("Paper"), appearance.background)
        press(Action.Back)
        while (model.focusedOption != SettingsOption.Background) move(Direction.Up)
        press(Action.Enter)
        move(Direction.Right)
        assertEquals("the next background change keeps a text that still reads", TextColorSetting.Dark, appearance.textColor)
    }

    @Test
    fun aPresetAccentFollowsTheTextThatTheBackgroundPicked() {
        val accent = ColorSetting.Preset("Red")
        assertEquals(accent, appearance.accent)
        openBackgroundPanel()
        move(Direction.Down)
        assertEquals(accent, appearance.accent)
        assertEquals(AccentPreset.Red.colorFor(TextMode.Dark), AccentPreset.Red.colorFor(TextMode.valueOf(appearance.textColor.stored)))
        assertEquals(TextMode.Light, TextMode.bestOn(BackgroundPreset.Graphite.color))
    }

    /** Left from the leftmost item of any colour page returns to its row in the options column. */
    private fun assertLeftReturnsToTheRow(
        option: SettingsOption,
        what: String,
    ) {
        repeat(10) { if (model.focus.column == SettingsColumn.Panel) move(Direction.Left) }
        assertEquals("$what: Left from the leftmost item leaves the panel", SettingsColumn.Options, model.focus.column)
        assertEquals("$what: on its own row", option, model.focusedOption)
    }

    @Test
    fun leftFromTheLeftmostTileOfTheColorsPanelReturnsToTheColorsRow() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Colors)
        press(Action.Enter)
        assertEquals(PanelPage.ColorSets, model.focus.page)
        assertEquals(SettingsColumn.Panel, model.focus.column)
        assertLeftReturnsToTheRow(SettingsOption.Colors, "Colors")
    }

    @Test
    fun leftFromTheLeftmostItemOfEveryColourPageReturnsToItsRow() {
        for ((option, name) in listOf(SettingsOption.Background to "Background", SettingsOption.TextColor to "Text color", SettingsOption.Accent to "Accent color")) {
            openSection(SettingsSection.Appearance)
            focusOption(option)
            press(Action.Enter)
            assertLeftReturnsToTheRow(option, "$name presets")
            press(Action.Enter)
            openPlus()
            assertLeftReturnsToTheRow(option, "$name custom grid")
            move(Direction.Left) // on to the sections column for the next option
            assertEquals(SettingsColumn.Sections, model.focus.column)
        }
    }
}
