package com.plumemu.settings.screen

import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.ColorShades
import com.plumemu.coreui.theme.ShadeRole
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

/** The colour pages of Settings: presets, Color, Shade and the Color code row (their walk, preview, put-back and choice), the hex code field. */
internal class SettingsColorPagesTest : SettingsModelHarness() {
    init {
        startOnRed() // these tests walk the accent presets from Red
    }

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
        assertOnColorsRow(COLORS_BACKGROUND_ITEM, "A chooses and goes back to its row")
        assertEquals(ColorSetting.Preset("Sand"), appearance.background)
        press(Action.Enter)
        assertEquals("opens again at the current one", 6, model.focus.panelIndex)
    }

    @Test
    fun anOwnColourOpensOnItsNearestShadeWithoutChangingTheColour() {
        actions.settings = actions.settings.copy(appearance = appearance.copy(background = ColorSetting.Custom(0x0F2B7A), textColor = TextColorSetting.Light))
        openBackgroundPanel()
        val (stop, shade) = ColorShades.nearestStopAndShade(ShadeRole.Background, Rgb(0x0F2B7A), Rgb(0))
        assertEquals(PanelPage.BackgroundPresets, model.focus.page)
        assertEquals(ColorPart.Shade, model.colourPart)
        assertEquals("the strip is at the nearest stop", stop, model.colorStop)
        assertEquals("the cursor is on the nearest shade", model.shadeIndex(PanelPage.BackgroundPresets, shade), model.focus.panelIndex)
        assertEquals("opening keeps the colour exactly", ColorSetting.Custom(0x0F2B7A), appearance.background)
        press(Action.Enter)
        assertOnColorsRow(COLORS_BACKGROUND_ITEM, "A before any move keeps the colour and goes back")
        assertEquals(ColorSetting.Custom(0x0F2B7A), appearance.background)
    }

    @Test
    fun downFromThePresetsWalksColorShadeAndTheColorCodeRowAndUpWalksBack() {
        openBackgroundPanel()
        val page = colourPage
        val presets = ColorPages.presetCount(page, ColorPages.backgroundOf(actions.shown))
        toColor()
        assertEquals(ColorPart.Color, model.colourPart)
        assertEquals("Color comes right after the presets", presets, model.focus.panelIndex)
        move(Direction.Down)
        assertEquals(ColorPart.Shade, model.colourPart)
        val shades = model.colorShades(page)
        assertTrue("up to five shades", shades.size in 2..ColorShades.SHADE_COUNT)
        move(Direction.Down)
        assertEquals(ColorPart.Hex, model.colourPart)
        assertEquals("the Color code row is after the last shade", presets + 1 + shades.size, model.focus.panelIndex)
        move(Direction.Up)
        assertEquals(ColorPart.Shade, model.colourPart)
        move(Direction.Up)
        assertEquals(ColorPart.Color, model.colourPart)
        move(Direction.Up)
        assertEquals("up from Color reaches the presets again", ColorPart.Preset, model.colourPart)
    }

    @Test
    fun upFromTheTopRowOfThePresetsDoesNothingAndAHeldDownNeverEntersColor() {
        openBackgroundPanel()
        move(Direction.Up)
        assertEquals(0, model.focus.panelIndex)
        assertEquals(PanelPage.BackgroundPresets, model.focus.page)
        repeat(6) { press(Action.Move(Direction.Down, isRepeat = true)) }
        assertEquals("a held Down walks the presets and stops at their last row", ColorPart.Preset, model.colourPart)
    }

    @Test
    fun bPutsBackTheColourInEffectWhenTheCursorCameDownButKeepsABrowsedPreset() {
        openBackgroundPanel()
        move(Direction.Down) // Paper, on the second row of presets
        assertEquals(ColorSetting.Preset("Paper"), appearance.background)
        press(Action.Back)
        assertOnColorsRow(COLORS_BACKGROUND_ITEM, "B on a preset keeps the one reached")
        assertEquals(ColorSetting.Preset("Paper"), appearance.background)

        press(Action.Enter)
        toColor()
        val reached = appearance.background
        move(Direction.Right)
        assertNotEquals("Color previews", reached, appearance.background)
        press(Action.Back)
        assertOnColorsRow(COLORS_BACKGROUND_ITEM)
        assertEquals("B from Color puts back the colour in effect when the cursor came down", reached, appearance.background)

        press(Action.Enter)
        toShade()
        move(Direction.Right)
        press(Action.Back)
        assertEquals("B from Shade does too", reached, appearance.background)

        press(Action.Enter)
        toHexRow()
        press(Action.Back)
        assertEquals("and from the Color code row", reached, appearance.background)
    }

    @Test
    fun aKeepsWhatColorAndShadeShowAndGoesBackToItsRow() {
        openBackgroundPanel()
        toShade()
        move(Direction.Right)
        val shown = appearance.background
        assertTrue(shown is ColorSetting.Custom)
        press(Action.Enter)
        assertOnColorsRow(COLORS_BACKGROUND_ITEM)
        assertEquals(shown, appearance.background)
        press(Action.Enter)
        assertEquals("an own colour opens on a shade", ColorPart.Shade, model.colourPart)
    }

    @Test
    fun leftFromColorAndShadeDoesNothingAndFromTheColorCodeRowLeavesToColorsRow() {
        openBackgroundPanel()
        toColor()
        repeat(3) { move(Direction.Left) }
        assertEquals(PanelPage.BackgroundPresets, model.focus.page)
        move(Direction.Down)
        repeat(6) { move(Direction.Left) }
        assertEquals(PanelPage.BackgroundPresets, model.focus.page)
        assertEquals(ColorPart.Shade, model.colourPart)
        toHexRow()
        move(Direction.Left)
        assertOnColorsRow(COLORS_BACKGROUND_ITEM)
    }

    @Test
    fun aHeldUpFromShadeStopsAtColorAndNeverReachesThePresets() {
        openBackgroundPanel()
        toShade()
        repeat(5) { press(Action.Move(Direction.Up, isRepeat = true)) }
        assertEquals(ColorPart.Color, model.colourPart)
        assertEquals(PanelPage.BackgroundPresets, model.focus.page)
        move(Direction.Up)
        assertEquals("a fresh Up goes on to the presets", ColorPart.Preset, model.colourPart)
    }

    @Test
    fun tappingAShadeWhileTypingLeavesTheField() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Accent)
        press(Action.Enter)
        toHexRow()
        press(Action.Enter)
        assertTrue(model.isEditingHex)
        val first = model.shadeIndex(colourPage, 0)
        model.onTapPanelItem(first, actions.shown, wide)
        assertFalse(model.isEditingHex)
        assertEquals(first, model.focus.panelIndex)
        val shade = model.colorShades(colourPage)[0]
        assertEquals("a first tap on a shade previews it", ColorSetting.Custom(shade.value), appearance.accent)
        model.onTapPanelItem(first, actions.shown, wide)
        assertOnColorsRow(COLORS_ACCENT_ITEM, "a second tap keeps it")
        assertEquals(ColorSetting.Custom(shade.value), appearance.accent)
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
        val beforeTyping = appearance.accent // the shade the walk down rests on previewed it: the Color code row keeps it
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
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Accent)
        press(Action.Enter)
        assertEquals(PanelPage.AccentPresets, model.focus.page)
        assertEquals(0, model.focus.panelIndex)
        toHexRow()
        val hexRow = model.focus.panelIndex
        assertEquals(model.hexIndex(colourPage), hexRow)
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
        assertEquals("moves are ignored while typing", hexRow, model.focus.panelIndex)
        press(Action.Back)
        assertFalse("Back leaves the field first", model.isEditingHex)
        assertEquals(PanelPage.AccentPresets, model.focus.page)
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
        tapIntoColorPage(SettingsOption.Background)
        model.onTapPanelItem(5, actions.settings, wide)
        assertEquals(PanelPage.BackgroundPresets, model.focus.page)
        assertEquals(ColorSetting.Preset("Paper"), appearance.background)
        model.onTapPanelItem(5, actions.settings, wide)
        assertEquals("the page is done: back on its row in Colors' panel", PanelPage.ColorSets to COLORS_BACKGROUND_ITEM, model.focus.page to model.focus.panelIndex)
        assertEquals(ColorSetting.Preset("Paper"), appearance.background)
    }

    @Test
    fun tappingTheColorCodeRowKeepsTheColourShownLikeTheDPad() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background) // under Colors
        press(Action.Enter)
        toShade()
        move(Direction.Right)
        val shown = appearance.background
        model.onTapPanelItem(model.hexIndex(colourPage), actions.shown, wide)
        assertTrue(model.isEditingHex)
        assertEquals("the colour shown stays", shown, appearance.background)
    }

    @Test
    fun leavingTheCompositionBelowThePresetsPutsBackTheColourInEffectWhenTheCursorCameDown() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background) // under Colors
        press(Action.Enter)
        toColor()
        val reached = appearance.background
        move(Direction.Right)
        assertNotEquals(reached, appearance.background)
        model.onDispose()
        assertEquals("no preview stays stored", reached, appearance.background)
        model.onDispose()
        assertEquals("a second dispose writes nothing more", reached, appearance.background)
    }

    /** A grey accent: it reads on Graphite and on the dark backgrounds, but not on the light ones. */
    private val grey = Rgb(0x909090)

    private fun openBackgroundPanel() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background) // under Colors
        press(Action.Enter)
    }

    /** Background's page with the grey accent, the cursor on the lightest shade of the strip's stop (a light background the grey does not read on). */
    private fun onLightestShadeWithGreyAccent() {
        actions.settings = actions.settings.copy(appearance = appearance.copy(accent = ColorSetting.Custom(grey.value)))
        openBackgroundPanel()
        toShade()
        repeat(ColorShades.SHADE_COUNT) { move(Direction.Left) }
    }

    @Test
    fun browsingToAShadeThatMovesTheAccentAndCancellingPutsTheOpeningAccentBack() {
        actions.settings = actions.settings.copy(appearance = appearance.copy(accent = ColorSetting.Custom(grey.value)))
        openBackgroundPanel()
        toColor()
        val cameDown = appearance
        move(Direction.Down)
        repeat(ColorShades.SHADE_COUNT) { move(Direction.Left) }
        assertNotEquals(ColorSetting.Custom(grey.value), appearance.accent)
        assertTrue("and the person is told", model.colorWrite?.accentAdjusted == true)
        press(Action.Back)
        assertEquals(cameDown.background, appearance.background)
        assertEquals("the accent in effect when the cursor came down is back", cameDown.accent, appearance.accent)
    }

    @Test
    fun browsingOnToAShadeWhereTheAccentReadsGivesTheOpeningAccentAgain() {
        onLightestShadeWithGreyAccent()
        assertNotEquals(ColorSetting.Custom(grey.value), appearance.accent)
        repeat(ColorShades.SHADE_COUNT) { move(Direction.Right) } // the darkest shade
        assertEquals(ColorSetting.Custom(grey.value), appearance.accent)
        assertNull("nothing to tell once it reads again", model.colorWrite?.takeIf { it.adjusted })
    }

    @Test
    fun theAccentShadesOfEveryStopReadSoNothingIsRefused() {
        openAccentHexRow() // Paper, Dark text
        move(Direction.Up) // Shade
        move(Direction.Up) // Color
        val stops = ColorShades.stopCount(ShadeRole.Accent)
        repeat(stops) { move(Direction.Left) } // the first stop
        repeat(stops) {
            val shades = model.colorShades(colourPage)
            assertTrue("every stop has shades on Paper", shades.isNotEmpty())
            for (shade in shades) assertTrue("${shade.toHex()} reads on Paper", Readability.passes(shade, BackgroundPreset.Paper.color, ColorRole.Accent))
            assertNull("no refusal on stop ${model.colorStop}", model.colorWrite?.refusal)
            move(Direction.Right)
        }
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
        assertOnColorsRow(COLORS_BACKGROUND_ITEM)
        assertEquals(TextColorSetting.Dark, appearance.textColor)
    }

    @Test
    fun everyPresetGetsTheTextThatContrastsMore() {
        for ((name, expected) in listOf("Sand" to TextColorSetting.Dark, "Mist" to TextColorSetting.Dark, "Plum" to TextColorSetting.Light)) {
            val index = ColorPages.backgroundPresets.indexOfFirst { it.name == name }
            model.onTapSection(SettingsSection.Appearance)
            model.onTapSection(SettingsSection.Appearance)
            tapIntoColorPage(SettingsOption.Background)
            model.onTapPanelItem(index, actions.settings, wide)
            assertEquals(name, expected, appearance.textColor)
            press(Action.Back)
        }
    }

    @Test
    fun aLightShadeAndAWhiteHexCodeGiveDarkTextADarkHexGivesLight() {
        openBackgroundPanel()
        toShade()
        repeat(ColorShades.SHADE_COUNT) { move(Direction.Left) } // the lightest shade
        assertTrue(appearance.background is ColorSetting.Custom)
        assertEquals(TextColorSetting.Dark, appearance.textColor)
        val hexRow = model.hexIndex(colourPage)
        move(Direction.Down)
        model.onTapPanelItem(hexRow, actions.shown, wide)
        model.onHexEntered("#FFFFFF")
        assertEquals(ColorSetting.Custom(0xFFFFFF), appearance.background)
        assertEquals(TextColorSetting.Dark, appearance.textColor)
        model.onTapPanelItem(model.hexIndex(colourPage), actions.shown, wide)
        model.onHexEntered("#101010")
        assertEquals(TextColorSetting.Light, appearance.textColor)
    }

    @Test
    fun leavingBelowThePresetsWithoutChoosingPutsBackTheBackgroundAndTheTextTogether() {
        // The person's own pale-green text on their own plum: it reads there, but not on a light shade.
        val pale = TextColorSetting.Custom(0xC8E6C9)
        val plum = ColorSetting.Custom(0x4A1D4F)
        actions.settings = actions.settings.copy(appearance = appearance.copy(background = plum, textColor = pale))
        openBackgroundPanel() // opens on the plum's nearest shade
        repeat(ColorShades.SHADE_COUNT) { move(Direction.Left) } // the lightest: the pale text moves darker so it reads
        assertNotEquals(plum, appearance.background)
        assertNotEquals(pale, appearance.textColor)
        press(Action.Back)
        assertEquals(plum, appearance.background)
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
        move(Direction.Up)
        focusOption(SettingsOption.Background)
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
        openSection(SettingsSection.Appearance)
        for ((option, item) in listOf(SettingsOption.Accent to COLORS_ACCENT_ITEM, SettingsOption.Background to COLORS_BACKGROUND_ITEM, SettingsOption.TextColor to COLORS_TEXT_ITEM)) {
            focusOption(option)
            press(Action.Enter)
            assertLeftReturnsToItsRowInColors(option, item, "presets")
            press(Action.Enter)
            toHexRow()
            assertLeftReturnsToItsRowInColors(option, item, "Color code row")
            move(Direction.Left) // out of Colors' panel, to the Colors row
            assertEquals(SettingsOption.Colors, model.focusedOption)
            assertEquals(SettingsColumn.Options, model.focus.column)
        }
    }

    private fun assertLeftReturnsToItsRowInColors(
        option: SettingsOption,
        item: Int,
        what: String,
    ) {
        repeat(geometryColumnsReach) { if (model.focus.page != PanelPage.ColorSets) move(Direction.Left) }
        assertEquals("$option $what: Left lands in Colors' panel", PanelPage.ColorSets, model.focus.page)
        assertEquals("$option $what: on its row", item, model.focus.panelIndex)
    }

    /** Enough Lefts to cross any colour page's widest row. */
    private val geometryColumnsReach = 11
}
