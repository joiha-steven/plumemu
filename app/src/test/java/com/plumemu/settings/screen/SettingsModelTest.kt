package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.GridSizeSetting
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.SwitchSystemWithSetting
import com.plumemu.coredata.settings.SoundLevel
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coredata.settings.TextWeightSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings' focus and writes by gamepad and by touch. */
internal class SettingsModelTest : SettingsModelHarness() {
    @Test
    fun opensOnTheSectionsInSpecOrderWithOnlyWorkingSections() {
        assertEquals(SettingsColumn.Sections, model.focus.column)
        assertEquals(
            listOf("Library", "Appearance", "Emulators", "Controls", "HomeScreen", "Statistics", "Advanced", "About"),
            SettingsSection.entries.map { it.name },
        )
    }

    @Test
    fun backChainGoesOneColumnLeftThenClosesSettings() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background)
        press(Action.Enter) // Background: into the panel
        assertEquals(SettingsColumn.Panel, model.focus.column)
        press(Action.Back)
        assertEquals(SettingsColumn.Options, model.focus.column)
        press(Action.Back)
        assertEquals(SettingsColumn.Sections, model.focus.column)
        assertEquals(0, actions.closed)
        press(Action.Back)
        assertEquals("from the sections, back to the main screen", 1, actions.closed)
    }

    @Test
    fun libraryOpensOnRomFolders() {
        assertEquals(SettingsSection.Library, model.focus.section)
        press(Action.Enter)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(SettingsOption.RomFolders, model.focusedOption)
    }

    @Test
    fun l1AndR1ChangeSectionAndEachRemembersItsOption() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.TextColor) // remembered for Appearance
        press(Action.NextSystem)
        assertEquals(SettingsSection.Emulators, model.focus.section)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals("no system has games: only Detect new emulators", SettingsOption.DetectEmulators, model.focusedOption)
        press(Action.NextSystem)
        assertEquals(SettingsOption.HoldToScrollFaster, model.focusedOption)
        press(Action.NextSystem)
        assertEquals(SettingsOption.UseAsHome, model.focusedOption)
        press(Action.NextSystem)
        assertEquals(SettingsOption.ConfirmBottom, model.focusedOption)
        press(Action.NextSystem)
        assertEquals(SettingsSection.About, model.focus.section)
        press(Action.NextSystem)
        assertEquals("the last section stays", SettingsSection.About, model.focus.section)
        repeat(5) { press(Action.PreviousSystem) }
        assertEquals(SettingsSection.Appearance, model.focus.section)
        assertEquals("each section remembers its option", SettingsOption.TextColor, model.focusedOption)
    }

    @Test
    fun fontAndTextSizeStepWithLeftRightAndWrapWithA() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Font)
        press(Action.Enter)
        assertEquals(FontSetting.Serif, appearance.font)
        press(Action.Enter)
        assertEquals(FontSetting.Mono, appearance.font)
        move(Direction.Left)
        assertEquals(FontSetting.Serif, appearance.font)

        focusOption(SettingsOption.TextSize)
        move(Direction.Left)
        assertEquals(TextSizeSetting.Small, appearance.textSize)
        move(Direction.Left)
        assertEquals("no write at the first value", TextSizeSetting.Small, appearance.textSize)
        press(Action.Enter)
        press(Action.Enter)
        assertEquals(TextSizeSetting.Large, appearance.textSize)
    }

    @Test
    fun textSizeIsUnsetUntilPickedAndSteppingStartsFromTheScreensDefault() {
        val thor = SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Large)
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.TextSize)
        assertNull("nothing is stored until a size is picked", appearance.textSize)

        move(Direction.Right, thor)
        assertEquals("from Large, the Thor's default, one step up", TextSizeSetting.Larger, appearance.textSize)
        repeat(5) { move(Direction.Right, thor) }
        assertEquals("right stops at the last of six", TextSizeSetting.Largest, appearance.textSize)
        move(Direction.Left, thor)
        assertEquals(TextSizeSetting.Huge, appearance.textSize)
    }

    @Test
    fun textWeightFollowsTextSizeAndShowsMediumUntilPickedThenStepsAndWraps() {
        val rows = SettingsSection.Appearance.rowsIn(model.content)
        assertEquals("right after Text size", rows.indexOf(SettingsOption.TextSize) + 1, rows.indexOf(SettingsOption.TextWeight))
        assertEquals(OptionKind.Choice, SettingsOption.TextWeight.kind)
        assertEquals(3, SettingsOption.TextWeight.choiceCount(actions.settings))
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.TextWeight)
        assertNull("nothing is stored until a weight is picked", appearance.textWeight)
        assertEquals(
            "not picked shows Medium",
            R.string.settings_weight_medium,
            choiceName(SettingsOption.TextWeight, SettingsOption.TextWeight.choiceIndex(actions.settings, TextSizeSetting.Medium)),
        )

        move(Direction.Left)
        assertEquals(TextWeightSetting.Thin, appearance.textWeight)
        move(Direction.Left)
        assertEquals("left stops at Thin", TextWeightSetting.Thin, appearance.textWeight)
        move(Direction.Right)
        move(Direction.Right)
        assertEquals(TextWeightSetting.Bold, appearance.textWeight)
        move(Direction.Right)
        assertEquals("right stops at Bold", TextWeightSetting.Bold, appearance.textWeight)
        press(Action.Enter)
        assertEquals("A wraps", TextWeightSetting.Thin, appearance.textWeight)
        assertNull("the text size is not touched", appearance.textSize)
    }

    @Test
    fun aTapOnTheTextWeightValueStepsItAndTheNamesAreThinMediumBold() {
        openSection(SettingsSection.Appearance)
        val index = SettingsSection.Appearance.rowsIn(model.content).indexOf(SettingsOption.TextWeight)
        model.onTapValue(index, actions.settings, wide) // the first tap focuses and steps, as for Text size
        assertEquals(TextWeightSetting.Bold, appearance.textWeight)
        model.onTapValue(index, actions.settings, wide)
        assertEquals("a tap past the end wraps", TextWeightSetting.Thin, appearance.textWeight)
        assertEquals(
            listOf(R.string.settings_weight_thin, R.string.settings_weight_medium, R.string.settings_weight_bold),
            TextWeightSetting.entries.indices.map { choiceName(SettingsOption.TextWeight, it) },
        )
    }

    @Test
    fun leftEdgeFollowsTextWeightShowsGameCountsUntilPickedThenStepsAndWraps() {
        val rows = SettingsSection.Appearance.rowsIn(model.content)
        assertEquals("right after Text weight", rows.indexOf(SettingsOption.TextWeight) + 1, rows.indexOf(SettingsOption.LeftEdge))
        assertEquals(OptionKind.Choice, SettingsOption.LeftEdge.kind)
        assertEquals(3, SettingsOption.LeftEdge.choiceCount(actions.settings))
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.LeftEdge)
        assertNull("nothing is stored until a pick", appearance.leftEdge)
        assertEquals(
            "not picked shows Game counts",
            R.string.settings_left_edge_counts,
            choiceName(SettingsOption.LeftEdge, SettingsOption.LeftEdge.choiceIndex(actions.settings, TextSizeSetting.Medium)),
        )

        move(Direction.Left)
        assertEquals(LeftEdgeSetting.Counts, appearance.leftEdge)
        move(Direction.Right)
        assertEquals(LeftEdgeSetting.Codes, appearance.leftEdge)
        move(Direction.Right)
        assertEquals(LeftEdgeSetting.Letters, appearance.leftEdge)
        move(Direction.Right)
        assertEquals("right stops at Letters", LeftEdgeSetting.Letters, appearance.leftEdge)
        press(Action.Enter)
        assertEquals("A wraps", LeftEdgeSetting.Counts, appearance.leftEdge)
        assertNull("the text weight is not touched", appearance.textWeight)
    }

    @Test
    fun aTapOnTheLeftEdgeValueStepsItAndTheNamesAreGameCountsSystemCodesLetters() {
        openSection(SettingsSection.Appearance)
        val index = SettingsSection.Appearance.rowsIn(model.content).indexOf(SettingsOption.LeftEdge)
        model.onTapValue(index, actions.settings, wide) // not picked shows Game counts; the first tap focuses and steps to System codes
        assertEquals(LeftEdgeSetting.Codes, appearance.leftEdge)
        model.onTapValue(index, actions.settings, wide)
        model.onTapValue(index, actions.settings, wide)
        assertEquals("a tap past the end wraps", LeftEdgeSetting.Counts, appearance.leftEdge)
        assertEquals(
            listOf(R.string.settings_left_edge_counts, R.string.settings_left_edge_codes, R.string.settings_left_edge_letters),
            LeftEdgeSetting.entries.indices.map { choiceName(SettingsOption.LeftEdge, it) },
        )
    }

    @Test
    fun gridSizeIsListedOnlyWithBoxArtAfterTextSize() {
        assertEquals("no account, no grid: no row", false, SettingsOption.GridSize in SettingsSection.Appearance.rowsIn(model.content))
        for (account in listOf(AccountStatus.NotInThisBuild, AccountStatus.SignedOut)) {
            assertFalse(account.toString(), SettingsOption.GridSize in SettingsSection.Appearance.rowsIn(model.content.copy(account = account)))
        }
        val rows = SettingsSection.Appearance.rowsIn(model.content.copy(account = AccountStatus.SignedIn("plumtester")))
        assertEquals("last, after Left edge", listOf(SettingsOption.LeftEdge, SettingsOption.GridSize), rows.takeLast(2))
        assertEquals(OptionKind.Choice, SettingsOption.GridSize.kind)
    }

    @Test
    fun gridSizeShowsMediumUntilPickedAndStepsWithLeftRightAndWrapsWithA() {
        model.content = model.content.copy(account = AccountStatus.SignedIn("plumtester"))
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.GridSize)
        val index = SettingsOption.GridSize.choiceIndex(actions.settings, TextSizeSetting.Medium)
        assertEquals("the row's value: ‹ Medium › (the default is one step up)", R.string.settings_size_medium, choiceName(SettingsOption.GridSize, index))
        assertEquals(3, SettingsOption.GridSize.choiceCount(actions.settings))

        move(Direction.Left)
        assertEquals(GridSizeSetting.Small, appearance.gridSize)
        move(Direction.Left)
        assertEquals("left stops at Small", GridSizeSetting.Small, appearance.gridSize)
        move(Direction.Right)
        assertEquals(GridSizeSetting.Medium, appearance.gridSize)
        assertEquals(R.string.settings_size_medium, choiceName(SettingsOption.GridSize, SettingsOption.GridSize.choiceIndex(actions.settings, TextSizeSetting.Medium)))
        move(Direction.Right)
        assertEquals(GridSizeSetting.Large, appearance.gridSize)
        assertEquals(R.string.settings_size_large, choiceName(SettingsOption.GridSize, SettingsOption.GridSize.choiceIndex(actions.settings, TextSizeSetting.Medium)))
        move(Direction.Right)
        assertEquals("right stops at Large", GridSizeSetting.Large, appearance.gridSize)
        press(Action.Enter)
        assertEquals("A wraps", GridSizeSetting.Small, appearance.gridSize)
        assertNull("the text size is not touched", appearance.textSize)
        // A choice steps in place at both widths: its explanation is the panel beside (SettingsGridSizeRowTest).
        move(Direction.Right, narrow)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(GridSizeSetting.Medium, appearance.gridSize)
    }

    @Test
    fun keyboardIsTheLastControlsOptionPlumemuByDefaultAndStepsToAndroid() {
        assertEquals(SettingsOption.Keyboard, SettingsSection.Controls.options.last())
        assertEquals(OptionKind.Choice, SettingsOption.Keyboard.kind)
        openSection(SettingsSection.Controls)
        focusOption(SettingsOption.Keyboard)
        val shown = { choiceName(SettingsOption.Keyboard, SettingsOption.Keyboard.choiceIndex(actions.settings, TextSizeSetting.Medium)) }
        assertEquals("the row's value: ‹ Plumemu ›", R.string.settings_keyboard_plumemu, shown())
        assertEquals(KeyboardSetting.Plumemu, actions.settings.controls.keyboard)
        assertEquals(2, SettingsOption.Keyboard.choiceCount(actions.settings))
        move(Direction.Right)
        assertEquals(KeyboardSetting.Android, actions.settings.controls.keyboard)
        assertEquals(R.string.settings_keyboard_android, shown())
        move(Direction.Right)
        assertEquals("right stops at Android", KeyboardSetting.Android, actions.settings.controls.keyboard)
        press(Action.Enter)
        assertEquals("A wraps back to Plumemu", KeyboardSetting.Plumemu, actions.settings.controls.keyboard)
        assertEquals("nothing else in Controls moves", true, actions.settings.controls.showButtonHints)
    }

    @Test
    fun switchSystemWithIsAControlsChoiceBeforeKeyboardDefaultsToL2R2AndStepsToL1R1() {
        val controls = SettingsSection.Controls.options
        assertEquals("right before Keyboard", SettingsOption.Keyboard, controls[controls.indexOf(SettingsOption.SwitchSystemWith) + 1])
        assertEquals(OptionKind.Choice, SettingsOption.SwitchSystemWith.kind)
        openSection(SettingsSection.Controls)
        focusOption(SettingsOption.SwitchSystemWith)
        val shown = { choiceName(SettingsOption.SwitchSystemWith, SettingsOption.SwitchSystemWith.choiceIndex(actions.settings, TextSizeSetting.Medium)) }
        assertEquals("the default: L2 / R2", SwitchSystemWithSetting.Triggers, actions.settings.controls.switchSystemWith)
        assertEquals(R.string.settings_switch_system_triggers, shown())
        assertEquals(2, SettingsOption.SwitchSystemWith.choiceCount(actions.settings))
        move(Direction.Right)
        assertEquals(SwitchSystemWithSetting.Bumpers, actions.settings.controls.switchSystemWith)
        assertEquals(R.string.settings_switch_system_bumpers, shown())
        press(Action.Enter)
        assertEquals("A wraps back to L2 / R2", SwitchSystemWithSetting.Triggers, actions.settings.controls.switchSystemWith)
        assertEquals("nothing else in Controls moves", KeyboardSetting.Plumemu, actions.settings.controls.keyboard)
    }

    @Test
    fun leftOnAnOptionThatIsNotAChoiceGoesBackToTheSections() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background)
        move(Direction.Left)
        assertEquals(SettingsColumn.Sections, model.focus.column)
    }

    @Test
    fun quickPressesEachCountBeforeTheStoreAnswers() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.TextSize)
        move(Direction.Left)
        assertEquals(TextSizeSetting.Small, appearance.textSize)
        move(Direction.Right)
        move(Direction.Right) // the screen still shows Small
        assertEquals(TextSizeSetting.Large, appearance.textSize)

        repeat(4) { move(Direction.Up) } // Background
        press(Action.Enter)
        move(Direction.Right)
        move(Direction.Right)
        move(Direction.Left)
        assertEquals(1, model.focus.panelIndex)
        assertEquals("the colour under the cursor", ColorSetting.Preset("Black"), appearance.background)
    }

    @Test
    fun advancedSwitchesFlipTheirKeys() {
        openSection(SettingsSection.Advanced)
        press(Action.Enter)
        assertEquals(ConfirmButtonSetting.Bottom, actions.settings.controls.confirmButton)
        press(Action.Enter)
        assertEquals(ConfirmButtonSetting.Right, actions.settings.controls.confirmButton)
        move(Direction.Down)
        press(Action.Enter)
        assertFalse(actions.settings.advanced.animations)
        move(Direction.Down)
        press(Action.Enter)
        assertEquals("Loud, the default, steps on to Off", SoundLevel.Off, actions.settings.advanced.sounds)
        move(Direction.Down)
        press(Action.Enter)
        assertFalse(actions.settings.advanced.vibration)
        move(Direction.Down) // Language: A opens its panel, nothing is written
        move(Direction.Down)
        press(Action.Enter)
        assertTrue("off by default, so Enter switches it on", actions.settings.advanced.usageStatistics)
        assertEquals("one write per change", 6, actions.writes)
    }

    @Test
    fun resetAllNeedsAConfirmAndKeepsSetupFlags() {
        actions.settings = actions.settings.copy(appearance = AppearanceSettings(font = FontSetting.Mono, textWeight = TextWeightSetting.Bold, leftEdge = LeftEdgeSetting.Letters))
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.ResetAll)
        press(Action.Enter)
        assertEquals(PanelPage.Confirm, model.focus.page)
        assertEquals("Cancel is focused first", SettingsModel.CONFIRM_CANCEL, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(0, actions.resetsAll)
        press(Action.Enter)
        move(Direction.Right)
        press(Action.Back)
        assertEquals("Back cancels too", 0, actions.resetsAll)

        press(Action.Enter)
        move(Direction.Right)
        press(Action.Enter)
        assertEquals(1, actions.resetsAll)
        assertEquals(SettingsOption.ResetAll, model.doneOption)
        assertEquals(FontSetting.Sans, appearance.font)
        assertNull("Reset all puts the text size back to not picked, which is Large", appearance.textSize)
        assertNull("Reset all puts the text weight back to not picked, which is Medium", appearance.textWeight)
        assertNull("Reset all puts the left edge back to not picked, which is Game counts", appearance.leftEdge)
        assertEquals(
            R.string.settings_weight_medium,
            choiceName(SettingsOption.TextWeight, SettingsOption.TextWeight.choiceIndex(actions.settings, TextSizeSetting.Medium)),
        )
        assertTrue(actions.settings.flags.setupCompleted)
        move(Direction.Up)
        assertNull("the done line goes with the focus", model.doneOption)
    }

    @Test
    fun resetLibraryNeedsAConfirm() {
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.ResetLibrary)
        press(Action.Enter)
        press(Action.Enter) // Cancel
        assertEquals(0, actions.resetsLibrary)
        press(Action.Enter)
        move(Direction.Right)
        press(Action.Enter)
        assertEquals(1, actions.resetsLibrary)
        assertEquals(SettingsOption.ResetLibrary, model.doneOption)
    }

    @Test
    fun narrowScreensOpenExplanationsAsAFurtherStopWideOnesShowThemBeside() {
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.UsageStatistics)
        move(Direction.Right, wide)
        assertEquals("wide: the panel is beside already", SettingsColumn.Options, model.focus.column)
        move(Direction.Right, narrow)
        assertEquals(SettingsColumn.Panel, model.focus.column)
        assertEquals(PanelPage.ReadOnly, model.focus.page)
        press(Action.Enter, narrow)
        assertEquals("A or B leaves the read-only stop", SettingsColumn.Options, model.focus.column)
        assertFalse("the switch was not flipped", actions.settings.advanced.usageStatistics)

        press(Action.NextSystem)
        focusOption(SettingsOption.Privacy)
        press(Action.Enter, wide)
        assertEquals(SettingsColumn.Options, model.focus.column)
        // A does nothing on a read-only row, narrow too: Right ("› More") is the one way in.
        press(Action.Enter, narrow)
        assertEquals(SettingsColumn.Options, model.focus.column)
        move(Direction.Right, narrow)
        assertEquals(PanelPage.ReadOnly, model.focus.page)
    }

    @Test
    fun licencesOpenAListThenAText() {
        openSection(SettingsSection.About)
        focusOption(SettingsOption.Licenses)
        press(Action.Enter)
        assertEquals(PanelPage.Licenses, model.focus.page)
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(PanelPage.LicenseText, model.focus.page)
        assertEquals(1, model.focus.licenseIndex)
        move(Direction.Down)
        move(Direction.Down)
        move(Direction.Up)
        assertEquals(1, model.licenseScroll)
        press(Action.Back)
        assertEquals(PanelPage.Licenses, model.focus.page)
        assertEquals(1, model.focus.panelIndex)
        press(Action.Back)
        assertEquals(SettingsColumn.Options, model.focus.column)
    }

    @Test
    fun touchFocusesFirstThenActsAndSwitchesFlipOnTheFirstTap() {
        model.onTapSection(SettingsSection.Advanced)
        assertEquals(SettingsColumn.Sections, model.focus.column)
        model.onTapSection(SettingsSection.Advanced)
        assertEquals("a second tap opens it", SettingsColumn.Options, model.focus.column)

        model.onTapOption(1, actions.settings, wide) // Animations: a switch flips on the first tap
        assertFalse(actions.settings.advanced.animations)
        val resetAll = SettingsSection.Advanced.options.indexOf(SettingsOption.ResetAll)
        model.onTapOption(resetAll, actions.settings, wide) // Reset all: first tap focuses
        assertEquals(SettingsOption.ResetAll, model.focusedOption)
        assertEquals(SettingsColumn.Options, model.focus.column)
        model.onTapOption(resetAll, actions.settings, wide)
        assertEquals("second tap opens the confirm step", PanelPage.Confirm, model.focus.page)
        model.onTapPanelItem(SettingsModel.CONFIRM_RESET, actions.settings, wide)
        assertEquals("a tap on Reset focuses it", 0, actions.resetsAll)
        model.onTapPanelItem(SettingsModel.CONFIRM_RESET, actions.settings, wide)
        assertEquals("a second tap resets", 1, actions.resetsAll)
    }

    @Test
    fun aTapOnTheValueActsAtOnce() {
        model.onTapSection(SettingsSection.Appearance)
        model.onTapSection(SettingsSection.Appearance)
        val rows = SettingsSection.Appearance.options
        model.onTapValue(rows.indexOf(SettingsOption.TextColor), actions.settings, wide)
        assertEquals("the Text color value opens its panel like Background's", PanelPage.TextPresets, model.focus.page)
        press(Action.Back)
        model.onTapValue(rows.indexOf(SettingsOption.Background), actions.settings, wide)
        assertEquals("the swatch value opens the colours", PanelPage.BackgroundPresets, model.focus.page)
    }
}
