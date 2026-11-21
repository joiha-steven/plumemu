package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.ColorRole
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.ColorShades
import com.plumemu.coreui.theme.ShadeRole
import com.plumemu.coreui.theme.Readability
import com.plumemu.coreui.theme.Rgb
import com.plumemu.settings.toRgb
import com.plumemu.settings.toTextSetting
import com.plumemu.settings.toThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Text color panel and the readable-only shades of Background, Text color and Accent color:
 * what the shades offer, refused hex codes with the nearest
 * colour, backgrounds nothing reads on, and the notice when a background moved the text or accent.
 */
internal class SettingsTextColorPanelTest : SettingsModelHarness() {
    private val banana = ColorSetting.Custom(ColorSet.Banana.backgroundColor.value)

    private fun use(
        background: ColorSetting,
        text: TextColorSetting,
        accent: ColorSetting = ColorSetting.Preset("Red"),
    ) {
        actions.settings = actions.settings.copy(appearance = appearance.copy(background = background, textColor = text, accent = accent))
        model.forgetColorWrites()
    }

    private fun shownBackground(): Rgb = actions.settings.toThemeSettings().backgroundColor

    /** In the Appearance options, whatever was open before. */
    private fun enterAppearance() {
        if (model.focus.column == SettingsColumn.Sections) openSection(SettingsSection.Appearance)
    }

    private fun focusOptionFromTop(option: SettingsOption) {
        repeat(12) { move(Direction.Up) }
        focusOption(option)
    }

    private fun openTextPanel() {
        enterAppearance()
        focusOptionFromTop(SettingsOption.TextColor)
        press(Action.Enter)
    }

    /** Text color's page, at the Color code row. */
    private fun openTextPalette(onHexRow: Boolean = true) {
        openTextPanel()
        assertEquals(PanelPage.TextPresets, model.focus.page)
        if (onHexRow) toHexRow()
    }

    private fun openAccentPalette() {
        enterAppearance()
        focusOptionFromTop(SettingsOption.Accent)
        press(Action.Enter)
        assertEquals(PanelPage.AccentPresets, model.focus.page)
        toHexRow()
    }

    private fun typeHex(code: String) {
        useAndroidKeyboard()
        press(Action.Enter)
        assertTrue(model.isEditingHex)
        model.onHexTextChange(code)
        model.onHexEntered(model.hexText)
    }

    // The row and the presets page.

    @Test
    fun theTextColorRowIsAColourRowLikeBackgroundNotAStepper() {
        assertEquals(OptionKind.Swatch, SettingsOption.TextColor.kind)
        assertEquals(SettingsOption.Background.kind, SettingsOption.TextColor.kind)
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.TextColor)
        move(Direction.Right)
        assertEquals("right opens the panel, as on Background", PanelPage.TextPresets, model.focus.page)
        assertEquals("on the current preset", 0, model.focus.panelIndex)
    }

    @Test
    fun aTapOnTheRowOpensThePanelAndTheNameIsLightDarkOrCustom() {
        model.onTapSection(SettingsSection.Appearance)
        model.onTapSection(SettingsSection.Appearance)
        tapIntoColorPage(SettingsOption.TextColor)
        assertEquals(PanelPage.TextPresets, model.focus.page)
        assertEquals(R.string.settings_text_light, textColorName(TextColorSetting.Light))
        assertEquals(R.string.settings_text_dark, textColorName(TextColorSetting.Dark))
        assertEquals(R.string.settings_color_custom, textColorName(TextColorSetting.Custom(0xC8E6C9)))
    }

    @Test
    fun aPresetThatDoesNotReadOnTheBackgroundIsNotShownSoTheCursorNeverStopsOnARefusedOne() {
        use(ColorSetting.Preset("Paper"), TextColorSetting.Dark)
        openTextPanel()
        val presets = ColorPages.textPresetsOn(shownBackground())
        assertEquals("Dark first, Light does not read on Paper", TextColorSetting.Dark, presets.first())
        assertFalse(presets.contains(TextColorSetting.Light))
        assertEquals("opens on Dark", 0, model.focus.panelIndex)
        move(Direction.Right)
        assertEquals(1, model.focus.panelIndex)
        assertNull(model.colorWrite?.refusal)
        assertEquals("every step is a colour that reads", presets[1], appearance.textColor)
        press(Action.Back)
        assertOnColorsRow(COLORS_TEXT_ITEM)
    }

    @Test
    fun aPresetThatReadsIsUsedWithAAndTheAccentFollowsItsMode() {
        use(ColorSetting.Preset("Black"), TextColorSetting.Light)
        openTextPanel()
        press(Action.Enter) // Light is current: A closes
        assertOnColorsRow(COLORS_TEXT_ITEM)
        assertEquals(TextColorSetting.Light, appearance.textColor)
    }

    // The shades show only what passes.

    /** Taps the Color row of the open page: the cursor goes under the presets with no preset previewed on the way. */
    private fun tapColorRow() = model.onTapPanelItem(model.colorIndex(colourPage), actions.settings, wide)

    @Test
    fun theTextShadesOfEveryStopPassOnBlackPaperAndBananaAndTheColorRowWritesThemUnrefused() {
        val cases = listOf("Black" to ColorSetting.Preset("Black"), "Paper" to ColorSetting.Preset("Paper"), "Banana" to banana)
        for ((name, background) in cases) {
            val text = if (name == "Black") TextColorSetting.Light else TextColorSetting.Dark
            use(background, text)
            openTextPanel()
            val bg = shownBackground()
            tapColorRow()
            val stops = ColorShades.stopCount(ShadeRole.Text)
            repeat(stops) { move(Direction.Left) }
            var withShades = 0
            repeat(stops) { stop ->
                val shades = model.colorShades(colourPage)
                for (shade in shades) assertTrue("$name stop $stop ${shade.toHex()} reads", Readability.passes(shade, bg, ColorRole.Text))
                if (shades.isNotEmpty()) withShades++
                // The first stop was where the strip already stood: no move wrote it.
                if (stop > 0 && shades.isNotEmpty()) assertEquals("$name stop $stop previews its shade of the same place", shades[model.shadePlaceOf(colourPage)], appearance.textColor.toRgb())
                assertNull("$name stop $stop is never refused", model.colorWrite?.refusal)
                move(Direction.Right)
            }
            assertEquals("$name: every stop offers shades", stops, withShades)
            press(Action.Back)
            assertOnColorsRow(COLORS_TEXT_ITEM, "$name: B closes the page")
            press(Action.Back)
        }
    }

    @Test
    fun theDPadWalksTheTextShadesAndEveryStepIsAPreview() {
        use(ColorSetting.Preset("Black"), TextColorSetting.Light)
        openTextPanel()
        tapColorRow()
        move(Direction.Down)
        val first = model.shadeIndex(colourPage, 0)
        val shades = model.colorShades(colourPage)
        assertEquals("the cursor rests on a shade", ColorPart.Shade, model.colourPart)
        repeat(shades.size) { move(Direction.Left) }
        assertEquals(first, model.focus.panelIndex)
        assertEquals(shades[0], appearance.textColor.toRgb())
        move(Direction.Right)
        assertEquals(shades[1], appearance.textColor.toRgb())
        repeat(10) { move(Direction.Down) }
        assertEquals("Down stops on the Color code row", ColorPart.Hex, model.colourPart)
        press(Action.Back)
        assertEquals("B restores the text the page opened with", TextColorSetting.Light, appearance.textColor)
    }

    @Test
    fun theAccentShadesOfEveryStopPassOnBlackPaperAndBananaAndNoneIsRefused() {
        for (background in listOf(ColorSetting.Preset("Black"), ColorSetting.Preset("Paper"), banana)) {
            val text = if (background == ColorSetting.Preset("Black")) TextColorSetting.Light else TextColorSetting.Dark
            use(background, text)
            enterAppearance()
            focusOptionFromTop(SettingsOption.Accent)
            press(Action.Enter)
            tapColorRow()
            val bg = shownBackground()
            repeat(ColorShades.stopCount(ShadeRole.Accent)) { move(Direction.Left) }
            repeat(ColorShades.stopCount(ShadeRole.Accent)) {
                for (shade in model.colorShades(colourPage)) assertTrue("${shade.toHex()} reads", Readability.passes(shade, bg, ColorRole.Accent))
                assertNull(model.colorWrite?.refusal)
                move(Direction.Right)
            }
            press(Action.Back)
            press(Action.Back)
        }
    }

    @Test
    fun theBackgroundShadesAreAllBackgroundsTextReadsOn() {
        for (stop in 0 until ColorShades.stopCount(ShadeRole.Background)) {
            for (shade in ColorShades.shades(ShadeRole.Background, stop, Rgb(0))) assertTrue("stop $stop ${shade.toHex()}", Readability.backgroundAllowsText(shade))
        }
    }

    // Hex codes that do not read.

    @Test
    fun aTextHexThatDoesNotReadIsRefusedWithTheNearestAndAOrATapUsesIt() {
        use(ColorSetting.Preset("Paper"), TextColorSetting.Dark)
        openTextPalette()
        val beforeTyping = appearance.textColor // the walk down previewed a swatch, which the sliders keep
        typeHex("EEEEEE")
        val refusal = model.hexRefusal
        assertEquals(ColorRole.Text, refusal?.role)
        assertTrue("the nearest reads", Readability.passes(refusal!!.nearest, shownBackground(), ColorRole.Text))
        assertTrue("the field stays", model.isEditingHex)
        assertTrue("the cursor is on the row that uses the nearest", model.hexOnUse)
        assertFalse("and the keyboard is down", model.isTypingHex)
        assertEquals("nothing stored", beforeTyping, appearance.textColor)
        assertEquals(ColorRole.Text, model.colorWrite?.refusal?.role)
        assertEquals("A on it takes the nearest", R.string.settings_hint_choose, confirmHint(model))
        press(Action.Enter)
        assertEquals(refusal.nearest.toTextSetting(), appearance.textColor)
        assertFalse(model.isEditingHex)
        assertNull(model.hexRefusal)
        assertNull(model.colorWrite?.refusal)
    }

    @Test
    fun theUseRowIsTappableToo() {
        use(ColorSetting.Preset("Paper"), TextColorSetting.Dark)
        openTextPalette()
        typeHex("EEEEEE")
        val nearest = model.hexRefusal!!.nearest
        model.onHexEntered(model.hexText) // what a tap on the row does
        assertEquals(nearest.toTextSetting(), appearance.textColor)
    }

    @Test
    fun typingAnotherCodeClearsTheRefusalAndAGoodCodeIsUsedAsTyped() {
        use(ColorSetting.Preset("Paper"), TextColorSetting.Dark)
        openTextPalette()
        typeHex("EEEEEE")
        assertTrue(model.hexRefusal != null)
        model.onHexTextChange("203020")
        assertNull(model.hexRefusal)
        assertNull(model.colorWrite?.refusal)
        model.onHexEntered(model.hexText)
        assertEquals(TextColorSetting.Custom(0x203020), appearance.textColor)
        assertFalse(model.isEditingHex)
    }

    @Test
    fun anAccentHexBelowThreeToOneIsRefusedWithTheNearestAndUseTakesIt() {
        use(ColorSetting.Preset("Paper"), TextColorSetting.Dark)
        openAccentPalette()
        typeHex("F0F0F0")
        val refusal = model.hexRefusal
        assertEquals(ColorRole.Accent, refusal?.role)
        assertTrue(Readability.passes(refusal!!.nearest, shownBackground(), ColorRole.Accent))
        assertNotEquals(ColorSetting.Custom(0xF0F0F0), appearance.accent)
        press(Action.Enter)
        assertEquals(ColorSetting.Custom(refusal.nearest.value), appearance.accent)
    }

    @Test
    fun aMidGreyBackgroundHexIsRefusedWithTheNearestUsableOne() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        toHexRow()
        val opening = appearance.background
        typeHex("808080")
        val refusal = model.hexRefusal
        assertNull("a background has no role", refusal?.role)
        assertTrue("the refusal exists", refusal != null)
        assertTrue(Readability.backgroundAllowsText(refusal!!.nearest))
        assertEquals("nothing stored", opening, appearance.background)
        assertTrue(model.isEditingHex)
        press(Action.Enter)
        assertEquals(ColorSetting.Custom(refusal.nearest.value), appearance.background)
        assertTrue("the text reads on it", Readability.passes(actions.settings.toThemeSettings().textColor, refusal.nearest, ColorRole.Text))
    }

    // The notice.

    @Test
    fun aBackgroundThatMovesTheTextIsSaidAndTheAccentToo() {
        use(ColorSetting.Preset("Graphite"), TextColorSetting.Custom(0xC8E6C9))
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        move(Direction.Down) // Paper: the pale green text cannot be read there
        appearance // the store answers
        val write = model.colorWrite
        assertTrue("text moved", write?.textAdjusted == true)
        assertNotEquals(TextColorSetting.Custom(0xC8E6C9), appearance.textColor)
        move(Direction.Up)
        appearance
        assertFalse("nothing moved back on the dark one", model.colorWrite?.adjusted == true)
        assertEquals("and the person's text is back exactly", TextColorSetting.Custom(0xC8E6C9), appearance.textColor)
    }

    @Test
    fun theNoticeIsClearedWhenAPanelOpens() {
        use(ColorSetting.Preset("Graphite"), TextColorSetting.Light)
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        move(Direction.Down) // Paper: Light becomes Dark
        appearance // the store answers
        assertTrue(model.colorWrite?.textAdjusted == true)
        press(Action.Enter)
        focusOption(SettingsOption.Accent)
        press(Action.Enter)
        assertNull(model.colorWrite)
    }

    // Leftover of the model step: the accent a background preview moved is the base of the next one.

    @Test
    fun reopeningBackgroundAfterOneThatMovedTheAccentStartsFromTheStoredAccentNotTheOneWrittenBefore() {
        use(ColorSetting.Preset("Graphite"), TextColorSetting.Light)
        val greyish = Rgb(0x909090) // reads on Graphite, not on Paper
        assertFalse(Readability.passes(greyish, BackgroundPreset.Paper.color, ColorRole.Accent))
        use(ColorSetting.Preset("Graphite"), TextColorSetting.Light, accent = ColorSetting.Custom(greyish.value))
        enterAppearance()
        focusOptionFromTop(SettingsOption.Background)
        press(Action.Enter)
        move(Direction.Down) // Paper: the accent no longer reads and moves
        press(Action.Enter) // choose Paper
        val moved = appearance.accent
        assertNotEquals(ColorSetting.Custom(greyish.value), moved)
        focusOptionFromTop(SettingsOption.Background)
        press(Action.Enter) // reopen, at Paper
        move(Direction.Up) // back to a dark preset where either accent reads
        assertEquals("the base is the stored (moved) accent", moved, appearance.accent)
    }

    @Test
    fun aSetPickedBetweenPanelsIsNotUndoneByWhatThePanelsWroteBefore() {
        use(ColorSetting.Preset("Graphite"), TextColorSetting.Light)
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        move(Direction.Down) // Paper
        press(Action.Enter)
        while (model.focusedOption != SettingsOption.Colors) move(Direction.Up)
        pickInColorsPanel(ColorSet.Pocket, wide)
        assertEquals(ColorSet.Pocket.backgroundColor, shownBackground())
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        assertEquals("the panel opens on the set's background, not on Paper", ColorPages.openingFor(SettingsOption.Background, actions.settings), model.focus.page!! to model.focus.panelIndex)
    }

    // The review of 2b: restore, stored text, presets that do not read, a grey stored background.

    private fun backToSections() {
        repeat(6) { if (model.focus.column != SettingsColumn.Sections) press(Action.Back) }
        assertEquals(SettingsColumn.Sections, model.focus.column)
    }

    @Test
    fun aRestoreForgetsTheColoursThePanelWroteSoLeavingABackgroundPageDoesNotBringThemBack() {
        use(ColorSetting.Preset("Black"), TextColorSetting.Light)
        enterAppearance()
        focusOptionFromTop(SettingsOption.Background)
        press(Action.Enter)
        tapColorRow()
        press(Action.Back)
        backToSections()
        // Restore a backup: the confirm, then Restore (the graph replaces the settings after).
        model.content = SettingsContent(restore = com.plumemu.graph.RestoreState.Confirm(com.plumemu.graph.RestoreSummary(com.plumemu.coredata.backup.BackupKind.Everything, 1L, games = 1, pictures = 0, account = com.plumemu.graph.AccountInFile.SignsIn)))
        openSection(SettingsSection.About)
        focusOption(SettingsOption.Restore)
        press(Action.Enter)
        move(Direction.Up)
        press(Action.Enter)
        val restored = appearance.copy(background = ColorSetting.Preset("Paper"), textColor = TextColorSetting.Dark)
        actions.settings = actions.settings.copy(appearance = restored)
        backToSections()
        repeat(8) { if (model.focus.section != SettingsSection.Appearance) move(Direction.Up) }
        assertEquals(SettingsSection.Appearance, model.focus.section)
        press(Action.Enter)
        focusOptionFromTop(SettingsOption.Background)
        press(Action.Enter)
        tapColorRow()
        press(Action.Back)
        assertEquals("B on the page writes the restored background, not the one before the restore", ColorSetting.Preset("Paper"), appearance.background)
        assertEquals(TextColorSetting.Dark, appearance.textColor)
    }

    @Test
    fun leavingTheBackgroundPageWithoutAPickKeepsAStoredTextThatIsDrawnAdjusted() {
        // Stored pale green text on Paper: drawn Dark (settled), stored as it is.
        use(ColorSetting.Preset("Paper"), TextColorSetting.Custom(0xC8E6C9))
        enterAppearance()
        focusOptionFromTop(SettingsOption.Background)
        press(Action.Enter)
        tapColorRow()
        move(Direction.Right) // a shade of the next stop: previewed
        press(Action.Back)
        assertEquals(ColorSetting.Preset("Paper"), appearance.background)
        assertEquals(TextColorSetting.Custom(0xC8E6C9), appearance.textColor)
    }

    @Test
    fun theTextPresetsAreSixToEightColoursThatAllReadOnTheBackground() {
        // Every background a person can pick: the palette, the presets and the colour sets.
        val shades = (0 until ColorShades.stopCount(ShadeRole.Background)).flatMap { ColorShades.shades(ShadeRole.Background, it, Rgb(0)) }
        val backgrounds = (shades + BackgroundPreset.entries.map { it.color } + ColorSet.entries.map { it.backgroundColor }).distinct()
        for (bg in backgrounds) {
            val presets = ColorPages.textPresetsOn(bg)
            if (!Readability.backgroundAllowsText(bg)) {
                assertTrue("${bg.toHex()} has no readable text, offers ${presets.size}", presets.isEmpty())
                continue
            }
            assertTrue("${bg.toHex()} offers ${presets.size}", presets.size in 6..8)
            assertEquals("no duplicates", presets.size, presets.map { it.toRgb() }.toSet().size)
            for (preset in presets) assertTrue("${preset.toRgb().toHex()} reads on ${bg.toHex()}", Readability.passes(preset.toRgb(), bg, ColorRole.Text))
            val first = presets.first()
            assertTrue("Light or Dark comes first where it reads", first == TextColorSetting.Light || first == TextColorSetting.Dark)
        }
    }

    @Test
    fun theCursorOnTheTextPresetsNeverStopsOnARefusedColourAndEndsOnTheLastPreset() {
        use(ColorSetting.Preset("Black"), TextColorSetting.Light)
        assertEquals(TextColorSetting.Light, ColorPages.textPresetsOn(shownBackground()).first())
        openTextPanel()
        val last = ColorPages.presetCount(PanelPage.TextPresets, shownBackground()) - 1
        repeat(last + 4) { move(Direction.Right) }
        assertNull(model.colorWrite?.refusal)
        assertTrue(model.focus.panelIndex in 0..last)
        use(ColorSetting.Preset("Paper"), TextColorSetting.Dark)
        assertEquals(TextColorSetting.Dark, ColorPages.textPresetsOn(shownBackground()).first())
    }

    @Test
    fun aStoredMiddleGreyBackgroundShowsOneLineInsteadOfColoursAndNeverOffersAFailingNearest() {
        use(ColorSetting.Custom(0x808080), TextColorSetting.Light)
        assertTrue(ColorPages.textBlockedOn(shownBackground()))
        openTextPanel()
        assertEquals(PanelPage.TextPresets, model.focus.page)
        assertEquals("nothing to pick", 1, ColorPages.presetCount(PanelPage.TextPresets, shownBackground()))
        press(Action.Enter)
        assertEquals("A does nothing", PanelPage.TextPresets, model.focus.page)
        move(Direction.Down)
        assertEquals("there is no Color row to go down to", 0, model.focus.panelIndex)
        assertEquals(TextColorSetting.Light, appearance.textColor)
    }
}
