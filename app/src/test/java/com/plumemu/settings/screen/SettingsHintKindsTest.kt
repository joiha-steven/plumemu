package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the hint bar says A does on each kind of row: "Open" on a row
 * that opens a page or the sign-in form, "Change" on a value, a switch, a colour dot and a system's
 * emulator (their panels are choices), "Select" on an action, nothing on read-only rows (Privacy,
 * Version, the box art status) at any width.
 */
internal class SettingsHintKindsTest : SettingsModelHarness() {
    private fun hintOn(
        section: SettingsSection,
        option: SettingsOption,
    ): Int? {
        backToTheFirstSection()
        openSection(section)
        focusOption(option)
        assertEquals(option, model.focusedOption)
        return confirmHint(model)
    }

    /** [openSection] walks down only: start every look from Library in the sections column. */
    private fun backToTheFirstSection() {
        repeat(3) { if (model.focus.column != SettingsColumn.Sections) press(Action.Back) }
        repeat(SettingsSection.entries.size) { move(Direction.Up) }
        assertEquals(SettingsColumn.Sections, model.focus.column)
        assertEquals(SettingsSection.entries.first(), model.focus.section)
    }

    @Test
    fun rowsThatOpenAPageOrTheSignInSayOpen() {
        model.content = model.content.copy(account = AccountStatus.SignedOut)
        for (option in listOf(SettingsOption.RomFolders, SettingsOption.ScraperAccount)) {
            assertEquals(option.name, R.string.settings_hint_open, hintOn(SettingsSection.Library, option))
        }
        for (option in listOf(SettingsOption.Backup, SettingsOption.Restore, SettingsOption.ResetAll, SettingsOption.ResetLibrary)) {
            assertEquals(option.name, R.string.settings_hint_open, hintOn(SettingsSection.Advanced, option))
        }
        assertEquals(R.string.settings_hint_open, hintOn(SettingsSection.About, SettingsOption.Licenses))
    }

    @Test
    fun theSignedInAccountRowSaysOpenToo() {
        model.content = model.content.copy(account = AccountStatus.SignedIn("plumtester"))
        assertEquals(R.string.settings_hint_open, hintOn(SettingsSection.Library, SettingsOption.ScraperAccount))
    }

    @Test
    fun valuesSwitchesAndChoicePanelsSayChange() {
        for (option in listOf(SettingsOption.Colors, SettingsOption.Background, SettingsOption.TextColor, SettingsOption.Accent, SettingsOption.Font)) {
            assertEquals(option.name, R.string.settings_hint_change, hintOn(SettingsSection.Appearance, option))
        }
        assertEquals(R.string.settings_hint_change, hintOn(SettingsSection.Library, SettingsOption.ScanOnStartup))
        assertEquals(R.string.settings_hint_change, hintOn(SettingsSection.Library, SettingsOption.SortGames))
        assertEquals(R.string.settings_hint_change, hintOn(SettingsSection.Advanced, SettingsOption.Animations))
    }

    @Test
    fun aSystemsEmulatorRowOpensAChoiceAndSaysChange() {
        model.content = model.content.copy(systems = listOf(SystemEmulators("snes", "Super Nintendo", null, null, emptyList())))
        openSection(SettingsSection.Emulators)
        assertEquals(SettingsOption.SystemEmulator, model.focusedOption)
        assertEquals(R.string.settings_hint_change, confirmHint(model))
    }

    @Test
    fun actionsSaySelect() {
        assertEquals(R.string.settings_hint_select, hintOn(SettingsSection.Library, SettingsOption.Rescan))
        assertEquals(R.string.settings_hint_select, hintOn(SettingsSection.Emulators, SettingsOption.DetectEmulators))
    }

    @Test
    fun readOnlyRowsHaveNoAHintOnAnyWidth() {
        // Version first: the focus only walks down, and About opens on the row it was left on.
        assertNull(hintOn(SettingsSection.About, SettingsOption.Version))
        assertNull(hintOn(SettingsSection.About, SettingsOption.Privacy))
    }

    /**
     * Every row and what A says on it on a wide screen, written out by hand (not derived from the
     * row's kind), so a row given the wrong kind or left out of [opensPage] shows here.
     */
    @Test
    fun everyRowSaysWhatItsADoes() {
        val open = R.string.settings_hint_open
        val change = R.string.settings_hint_change
        val select = R.string.settings_hint_select
        val none: Int? = null
        val expected: Map<SettingsOption, Int?> =
            mapOf(
                SettingsOption.Colors to change,
                SettingsOption.Background to change,
                SettingsOption.TextColor to change,
                SettingsOption.Accent to change,
                SettingsOption.Font to change,
                SettingsOption.TextSize to change,
                SettingsOption.TextWeight to change,
                SettingsOption.LeftEdge to change,
                SettingsOption.GridSize to change,
                SettingsOption.ConfirmBottom to change,
                SettingsOption.Animations to change,
                SettingsOption.Sounds to change,
                SettingsOption.UsageStatistics to change,
                SettingsOption.Backup to open,
                SettingsOption.Restore to open,
                SettingsOption.UndoRestore to open,
                SettingsOption.ResetAll to open,
                SettingsOption.ResetLibrary to open,
                SettingsOption.ExportLogs to select,
                SettingsOption.Version to none,
                SettingsOption.UpdateAvailable to open,
                SettingsOption.CheckForUpdates to select,
                SettingsOption.CheckUpdatesDaily to change,
                SettingsOption.Licenses to open,
                SettingsOption.Privacy to none,
                SettingsOption.ReportProblem to open,
                SettingsOption.Language to open,
                SettingsOption.Vibration to change,
                SettingsOption.RomFolders to open,
                SettingsOption.Rescan to select,
                SettingsOption.ScanOnStartup to change,
                SettingsOption.SortGames to change,
                SettingsOption.SystemOrder to change,
                SettingsOption.RecentSize to change,
                SettingsOption.ClearRecent to open,
                SettingsOption.Systems to open,
                SettingsOption.UnidentifiedGames to open,
                SettingsOption.HiddenGames to open,
                // A choice of emulators, like Colors' panel: "Change".
                SettingsOption.SystemEmulator to change,
                SettingsOption.UnknownFolder to open,
                SettingsOption.DetectEmulators to select,
                SettingsOption.HoldToScrollFaster to change,
                SettingsOption.PushRightToPlay to change,
                SettingsOption.ShowButtonHints to change,
                SettingsOption.Keyboard to change,
                SettingsOption.SwitchSystemWith to change,
                SettingsOption.UseAsHome to select,
                SettingsOption.ShowApps to change,
                SettingsOption.ShowClock to change,
                SettingsOption.ShowBattery to change,
                SettingsOption.SystemInfo to change,
                SettingsOption.SecondScreen to change,
                SettingsOption.SecondScreenInGame to change,
                SettingsOption.ScraperAccount to open,
                SettingsOption.ScraperStatus to none,
                SettingsOption.BoxArtRegion to change,
                SettingsOption.Statistic to none,
            )
        assertEquals("every row has its expected hint", SettingsOption.entries.toSet(), expected.keys)
        for ((option, hint) in expected) assertEquals(option.name, hint, rowConfirmHint(option))
    }
}
