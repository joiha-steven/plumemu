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
import com.plumemu.coreui.theme.CustomPalette
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
 * The Text color panel and the readable-only palettes of Background, Text color and Accent color:
 * what the palettes offer, refused hex codes with the nearest
 * colour, backgrounds nothing reads on, and the notice when a background moved the text or accent.
 */
internal class SettingsTextColorPanelTest : SettingsModelHarness() {
    private val banana = ColorSetting.Custom(ColorSet.Banana.backgroundColor.value)

    private fun use(
        background: ColorSetting,
        text: TextColorSetting,
        accent: ColorSetting = ColorSetting.Preset(AppearanceSettings.DEFAULT_ACCENT),
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

    /** Text color's own-colour page, at the hex row (the current text is a preset, so it is not in the grid). */
    private fun openTextPalette(onHexRow: Boolean = true) {
        openTextPanel()
        assertEquals(PanelPage.TextPresets, model.focus.page)
        openPlus()
        assertEquals(PanelPage.TextCustom, model.focus.page)
        if (onHexRow) toHexRow()
    }

    private fun openAccentPalette() {
        enterAppearance()
        focusOptionFromTop(SettingsOption.Accent)
        press(Action.Enter)
        openPlus()
        assertEquals(PanelPage.AccentCustom, model.focus.page)
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
        val row = SettingsSection.Appearance.options.indexOf(SettingsOption.TextColor)
        model.onTapValue(row, actions.settings, wide)
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
        assertEquals(SettingsColumn.Options, model.focus.column)
    }

    @Test
    fun aPresetThatReadsIsUsedWithAAndTheAccentFollowsItsMode() {
        use(ColorSetting.Preset("Black"), TextColorSetting.Light)
        openTextPanel()
        press(Action.Enter) // Light is current: A closes
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(TextColorSetting.Light, appearance.textColor)
    }

    // The palettes show only what passes.

    @Test
    fun theTextPaletteShowsOnlyPassingColoursOnBlackPaperAndBanana() {
        val cases = listOf("Black" to ColorSetting.Preset("Black"), "Paper" to ColorSetting.Preset("Paper"), "Banana" to banana)
        for ((name, background) in cases) {
            val text = if (name == "Black") TextColorSetting.Light else TextColorSetting.Dark
            use(background, text)
            openTextPalette()
            val bg = shownBackground()
            val cells = PaletteCuration.curated(CustomPalette.textSwatchesOn(bg))
            assertTrue("$name offers cells", cells.size >= 8)
            assertEquals("$name: the grid is the passing cells and the hex row", cells.size + ColorSlider.entries.size + 1, PanelGrid.of(PanelPage.TextCustom, wide, com.plumemu.settings.screen.SettingsContent(), model.focus, actions.settings)?.count)
            // Touch: a tap on each cell takes it, and none is ever refused.
            for ((i, cell) in cells.withIndex()) {
                model.onTapPanelItem(i, actions.settings, wide)
                assertEquals("$name cell $i", cell, appearance.textColor.toRgb())
                assertNull("$name cell ${cell.toHex()} is never refused", model.colorWrite?.refusal)
                assertTrue("$name ${cell.toHex()} reads", Readability.passes(cell, bg, ColorRole.Text))
            }
            press(Action.Back)
            press(Action.Back)
            assertEquals("$name: B closes the panel", SettingsColumn.Options, model.focus.column)
        }
    }

    @Test
    fun theDPadWalksTheTextPaletteAndEveryStepIsAPreview() {
        use(ColorSetting.Preset("Black"), TextColorSetting.Light)
        openTextPalette(onHexRow = false)
        val cells = PaletteCuration.curated(CustomPalette.textSwatchesOn(shownBackground()))
        // Light #EDEDED is not a cell: the page opens on the nearest swatch, not on the hex row.
        val start = model.focus.panelIndex
        assertTrue("on a swatch, not the hex row", start < cells.size)
        val sideways = if (start % 8 == 7) -1 else 1
        move(if (sideways > 0) Direction.Right else Direction.Left)
        assertEquals(start + sideways, model.focus.panelIndex)
        assertEquals(cells[start + sideways], appearance.textColor.toRgb())
        val vertical = if (start + sideways >= 8) -8 else 8
        move(if (vertical < 0) Direction.Up else Direction.Down)
        assertEquals(start + sideways + vertical, model.focus.panelIndex)
        assertEquals(cells[start + sideways + vertical], appearance.textColor.toRgb())
        repeat(10) { move(Direction.Down) }
        assertEquals("Down runs through the sliders and stops on the hex row", cells.size + ColorSlider.entries.size, model.focus.panelIndex)
        press(Action.Back)
        assertEquals("B restores the text the page opened with", TextColorSetting.Light, appearance.textColor)
    }

    @Test
    fun theAccentPaletteOffersOnlyThreeToOneColoursOnBlackPaperAndBanana() {
        for (background in listOf(ColorSetting.Preset("Black"), ColorSetting.Preset("Paper"), banana)) {
            val text = if (background == ColorSetting.Preset("Black")) TextColorSetting.Light else TextColorSetting.Dark
            use(background, text)
            openAccentPalette()
            val bg = shownBackground()
            val cells = PaletteCuration.curated(CustomPalette.accentSwatchesOn(bg))
            assertTrue(cells.isNotEmpty())
            assertEquals(cells.size + ColorSlider.entries.size + 1, PanelGrid.of(PanelPage.AccentCustom, wide, SettingsContent(), model.focus, actions.settings)?.count)
            for ((i, cell) in cells.withIndex()) {
                model.onTapPanelItem(i, actions.settings, wide)
                assertEquals(ColorSetting.Custom(cell.value), appearance.accent)
                assertNull(model.colorWrite?.refusal)
            }
            press(Action.Back)
            press(Action.Back)
        }
    }

    @Test
    fun theBackgroundPaletteLeavesOutBackgroundsNoTextReadsOn() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        walkToPlus()
        press(Action.Enter)
        val grid = PanelGrid.of(PanelPage.BackgroundCustom, wide, SettingsContent(), model.focus, actions.settings)
        assertEquals(backgroundGrid.size + ColorSlider.entries.size + 1, grid?.count)
        for (cell in backgroundGrid) assertTrue(cell.toHex(), Readability.backgroundAllowsText(cell))
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
        openPlus()
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
        val onGraphite = PaletteCuration.curated(CustomPalette.accentSwatchesOn(BackgroundPreset.Graphite.color))
        val greyish = onGraphite.first { !Readability.passes(it, BackgroundPreset.Paper.color, ColorRole.Accent) }
        openAccentPalette()
        model.onTapPanelItem(onGraphite.indexOf(greyish), actions.settings, wide)
        assertEquals(ColorSetting.Custom(greyish.value), appearance.accent)
        press(Action.Back)
        press(Action.Back)
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

    /** A tap on "+" of the open presets page: no preview of the presets on the way, unlike walking there. */
    private fun tapPlus() {
        val page = model.focus.page!!
        repeat(2) { if (model.focus.page == page) model.onTapPanelItem(ColorPages.presetCount(page, shownBackground()) - 1, actions.settings, wide) }
    }

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
        tapPlus()
        press(Action.Back)
        backToSections()
        // Restore a backup: the confirm, then Restore (the graph replaces the settings after).
        model.content = SettingsContent(restore = com.plumemu.graph.RestoreState.Confirm(com.plumemu.graph.RestoreSummary(com.plumemu.coredata.backup.BackupKind.Everything, 1L, games = 1, pictures = 0, account = com.plumemu.graph.AccountInFile.SignsIn)))
        openSection(SettingsSection.Advanced)
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
        tapPlus()
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
        tapPlus()
        move(Direction.Up) // a cell of the palette: previewed
        press(Action.Back)
        assertEquals(ColorSetting.Preset("Paper"), appearance.background)
        assertEquals(TextColorSetting.Custom(0xC8E6C9), appearance.textColor)
    }

    @Test
    fun theTextPresetsAreSixToEightColoursThatAllReadOnTheBackground() {
        // Every background a person can pick: the palette, the presets and the colour sets.
        val backgrounds = (backgroundGrid + BackgroundPreset.entries.map { it.color } + ColorSet.entries.map { it.backgroundColor }).distinct()
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
    fun theCursorOnTheTextPresetsNeverStopsOnARefusedColourAndEndsOnPlus() {
        use(ColorSetting.Preset("Black"), TextColorSetting.Light)
        assertEquals(TextColorSetting.Light, ColorPages.textPresetsOn(shownBackground()).first())
        openTextPanel()
        val plus = ColorPages.presetCount(PanelPage.TextPresets, shownBackground()) - 1
        repeat(plus + 4) { move(Direction.Right) }
        assertNull(model.colorWrite?.refusal)
        assertTrue(model.focus.panelIndex in 0..plus)
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
        assertEquals("A does nothing and does not open the palette", PanelPage.TextPresets, model.focus.page)
        assertEquals(TextColorSetting.Light, appearance.textColor)
    }
}
